package com.reelfocus.app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.reelfocus.app.models.FocusCommand
import com.reelfocus.app.models.FocusState
import com.reelfocus.app.models.FocusStateMachine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.focusRuntimeDataStore by preferencesDataStore(name = "focus_runtime_v1")

enum class DesiredFocusState {
    Stopped,
    Running,
    Paused
}

/**
 * Persisted intent and observed lifecycle are deliberately separate.
 * Running is published only after the service acknowledges foreground startup
 * and its first repository heartbeat.
 */
data class FocusRuntimeSnapshot(
    val desired: DesiredFocusState = DesiredFocusState.Stopped,
    val actual: FocusState = FocusState.NotSetUp,
    val changedAtWallTimeMs: Long = 0L,
    val lastHeartbeatWallTimeMs: Long = 0L,
    val lastErrorCode: String? = null
)

enum class FocusRuntimeCommand {
    SetupCompleted,
    StartRequested,
    ServiceStartAcknowledged,
    StartFailed,
    StopRequested,
    ServiceStopAcknowledged,
    PauseRequested,
    ResumeRequested,
    PermissionLost,
    PermissionsRestored,
    Heartbeat
}

/** Pure reducer shared by the repository and deterministic unit tests. */
object FocusRuntimeReducer {
    fun reduce(
        current: FocusRuntimeSnapshot,
        command: FocusRuntimeCommand,
        nowWallTimeMs: Long,
        errorCode: String? = null
    ): FocusRuntimeSnapshot {
        val next = when (command) {
            FocusRuntimeCommand.SetupCompleted -> current.copy(
                actual = FocusStateMachine.transition(current.actual, FocusCommand.SetupCompleted)
            )
            FocusRuntimeCommand.StartRequested -> {
                if (current.desired == DesiredFocusState.Running ||
                    current.actual != FocusState.Ready
                ) return current
                current.copy(
                    desired = DesiredFocusState.Running,
                    actual = FocusStateMachine.transition(current.actual, FocusCommand.StartRequested),
                    lastErrorCode = null
                )
            }
            FocusRuntimeCommand.ServiceStartAcknowledged -> {
                if (current.desired != DesiredFocusState.Running ||
                    current.actual != FocusState.Starting
                ) return current
                current.copy(
                    actual = FocusStateMachine.transition(current.actual, FocusCommand.ServiceStarted),
                    lastHeartbeatWallTimeMs = nowWallTimeMs,
                    lastErrorCode = null
                )
            }
            FocusRuntimeCommand.StartFailed -> {
                if (current.actual != FocusState.Starting) return current
                current.copy(
                    desired = DesiredFocusState.Stopped,
                    actual = FocusStateMachine.transition(current.actual, FocusCommand.StartFailed),
                    lastErrorCode = errorCode ?: "START_FAILED"
                )
            }
            FocusRuntimeCommand.StopRequested -> {
                if (current.actual == FocusState.NotSetUp ||
                    current.actual == FocusState.Ready
                ) current.copy(desired = DesiredFocusState.Stopped)
                else current.copy(
                    desired = DesiredFocusState.Stopped,
                    actual = FocusStateMachine.transition(current.actual, FocusCommand.StopRequested)
                )
            }
            FocusRuntimeCommand.ServiceStopAcknowledged -> {
                if (current.desired != DesiredFocusState.Stopped) return current
                val settled = if (current.actual == FocusState.NotSetUp) FocusState.NotSetUp else FocusState.Ready
                current.copy(actual = settled)
            }
            FocusRuntimeCommand.PauseRequested -> {
                if (current.actual != FocusState.Running) return current
                current.copy(
                    desired = DesiredFocusState.Paused,
                    actual = FocusStateMachine.transition(current.actual, FocusCommand.PauseRequested)
                )
            }
            FocusRuntimeCommand.ResumeRequested -> {
                if (current.desired != DesiredFocusState.Paused ||
                    current.actual != FocusState.Paused
                ) return current
                current.copy(
                    desired = DesiredFocusState.Running,
                    actual = FocusStateMachine.transition(current.actual, FocusCommand.ResumeRequested)
                )
            }
            FocusRuntimeCommand.PermissionLost -> current.copy(
                actual = FocusStateMachine.transition(current.actual, FocusCommand.PermissionLost)
            )
            FocusRuntimeCommand.PermissionsRestored -> {
                if (current.desired != DesiredFocusState.Running ||
                    current.actual != FocusState.Degraded
                ) return current
                current.copy(
                    actual = FocusStateMachine.transition(current.actual, FocusCommand.PermissionsRestored)
                )
            }
            FocusRuntimeCommand.Heartbeat -> {
                if (current.actual != FocusState.Running) return current
                current.copy(lastHeartbeatWallTimeMs = nowWallTimeMs)
            }
        }
        return if (next == current) current else next.copy(changedAtWallTimeMs = nowWallTimeMs)
    }
}

/**
 * Single serialized writer for monitoring lifecycle state.
 * Callers observe [state] and submit commands; they do not maintain their own
 * authoritative monitoring boolean.
 */
class FocusStateRepository(context: Context) {
    private val dataStore: DataStore<Preferences> = context.applicationContext.focusRuntimeDataStore
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableState = MutableStateFlow(FocusRuntimeSnapshot())
    val state: StateFlow<FocusRuntimeSnapshot> = mutableState.asStateFlow()
    private var loaded = false

    private val desiredKey = stringPreferencesKey("desired_state")
    private val actualKey = stringPreferencesKey("actual_state")
    private val changedAtKey = longPreferencesKey("changed_at_wall_ms")
    private val heartbeatKey = longPreferencesKey("last_heartbeat_wall_ms")
    private val errorKey = stringPreferencesKey("last_error_code")

    init {
        scope.launch {
            dataStore.data.collect { preferences ->
                mutableState.value = preferences.toSnapshot()
                loaded = true
            }
        }
    }

    suspend fun markSetupComplete() = dispatch(FocusRuntimeCommand.SetupCompleted)
    suspend fun requestStart(): Boolean = dispatch(FocusRuntimeCommand.StartRequested)
    suspend fun acknowledgeServiceStarted(): Boolean =
        dispatch(FocusRuntimeCommand.ServiceStartAcknowledged)
    suspend fun failStart(errorCode: String): Boolean =
        dispatch(FocusRuntimeCommand.StartFailed, errorCode)
    suspend fun requestStop() = dispatch(FocusRuntimeCommand.StopRequested)
    suspend fun acknowledgeServiceStopped() =
        dispatch(FocusRuntimeCommand.ServiceStopAcknowledged)
    suspend fun requestPause() = dispatch(FocusRuntimeCommand.PauseRequested)
    suspend fun requestResume() = dispatch(FocusRuntimeCommand.ResumeRequested)
    suspend fun markPermissionLost() = dispatch(FocusRuntimeCommand.PermissionLost)
    suspend fun markPermissionsRestored() = dispatch(FocusRuntimeCommand.PermissionsRestored)
    suspend fun recordHeartbeat() = dispatch(FocusRuntimeCommand.Heartbeat)

    private suspend fun dispatch(
        command: FocusRuntimeCommand,
        errorCode: String? = null
    ): Boolean = mutex.withLock {
        ensureLoaded()
        val before = mutableState.value
        val after = FocusRuntimeReducer.reduce(
            current = before,
            command = command,
            nowWallTimeMs = System.currentTimeMillis(),
            errorCode = errorCode
        )
        if (after == before) return@withLock false
        dataStore.edit { preferences -> preferences.putSnapshot(after) }
        mutableState.value = after
        true
    }

    private suspend fun ensureLoaded() {
        if (loaded) return
        mutableState.value = dataStore.data.first().toSnapshot()
        loaded = true
    }

    private fun Preferences.toSnapshot(): FocusRuntimeSnapshot {
        val desired = runCatching {
            DesiredFocusState.valueOf(this[desiredKey] ?: DesiredFocusState.Stopped.name)
        }.getOrDefault(DesiredFocusState.Stopped)
        val actual = runCatching {
            FocusState.valueOf(this[actualKey] ?: FocusState.NotSetUp.name)
        }.getOrDefault(FocusState.NotSetUp)
        return FocusRuntimeSnapshot(
            desired = desired,
            actual = actual,
            changedAtWallTimeMs = this[changedAtKey] ?: 0L,
            lastHeartbeatWallTimeMs = this[heartbeatKey] ?: 0L,
            lastErrorCode = this[errorKey]
        )
    }

    private fun MutablePreferences.putSnapshot(snapshot: FocusRuntimeSnapshot) {
        this[desiredKey] = snapshot.desired.name
        this[actualKey] = snapshot.actual.name
        this[changedAtKey] = snapshot.changedAtWallTimeMs
        this[heartbeatKey] = snapshot.lastHeartbeatWallTimeMs
        if (snapshot.lastErrorCode == null) remove(errorKey) else this[errorKey] = snapshot.lastErrorCode
    }
}

/** Process-wide repository instance so Activity and Service share one StateFlow and Mutex. */
object FocusStateRepositoryProvider {
    @Volatile private var instance: FocusStateRepository? = null
    fun get(context: Context): FocusStateRepository =
        instance ?: synchronized(this) {
            instance ?: FocusStateRepository(context.applicationContext).also { instance = it }
        }
}
