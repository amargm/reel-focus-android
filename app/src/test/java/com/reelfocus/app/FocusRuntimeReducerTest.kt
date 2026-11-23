package com.reelfocus.app

import com.reelfocus.app.models.FocusState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusRuntimeReducerTest {
    @Test fun runningIsNotPublishedUntilServiceAcknowledgesStartup() {
        val ready = FocusRuntimeReducer.reduce(
            FocusRuntimeSnapshot(actual = FocusState.Ready),
            FocusRuntimeCommand.StartRequested,
            100L
        )
        assertEquals(DesiredFocusState.Running, ready.desired)
        assertEquals(FocusState.Starting, ready.actual)

        val running = FocusRuntimeReducer.reduce(
            ready,
            FocusRuntimeCommand.ServiceStartAcknowledged,
            250L
        )
        assertEquals(FocusState.Running, running.actual)
        assertEquals(250L, running.lastHeartbeatWallTimeMs)
    }

    @Test fun duplicateStartDoesNotCreateAnotherTransition() {
        val starting = FocusRuntimeSnapshot(
            desired = DesiredFocusState.Running,
            actual = FocusState.Starting,
            changedAtWallTimeMs = 100L
        )
        val duplicate = FocusRuntimeReducer.reduce(
            starting,
            FocusRuntimeCommand.StartRequested,
            200L
        )
        assertEquals(starting, duplicate)
    }

    @Test fun acknowledgementAfterStopCannotResurrectMonitoring() {
        val starting = FocusRuntimeSnapshot(
            desired = DesiredFocusState.Running,
            actual = FocusState.Starting
        )
        val stopped = FocusRuntimeReducer.reduce(
            starting,
            FocusRuntimeCommand.StopRequested,
            200L
        )
        assertEquals(FocusState.Stopping, stopped.actual)
        assertEquals(DesiredFocusState.Stopped, stopped.desired)

        val lateAck = FocusRuntimeReducer.reduce(
            stopped,
            FocusRuntimeCommand.ServiceStartAcknowledged,
            300L
        )
        assertEquals(stopped, lateAck)
    }

    @Test fun failedStartReturnsToReadyAndClearsDesiredRunning() {
        val starting = FocusRuntimeSnapshot(
            desired = DesiredFocusState.Running,
            actual = FocusState.Starting
        )
        val failed = FocusRuntimeReducer.reduce(
            starting,
            FocusRuntimeCommand.StartFailed,
            500L,
            "FOREGROUND_START_BLOCKED"
        )
        assertEquals(DesiredFocusState.Stopped, failed.desired)
        assertEquals(FocusState.Ready, failed.actual)
        assertEquals("FOREGROUND_START_BLOCKED", failed.lastErrorCode)
    }

    @Test fun stopIsIdempotentAndServiceMustAcknowledgeIt() {
        val running = FocusRuntimeSnapshot(
            desired = DesiredFocusState.Running,
            actual = FocusState.Running
        )
        val stopping = FocusRuntimeReducer.reduce(running, FocusRuntimeCommand.StopRequested, 10L)
        assertEquals(FocusState.Stopping, stopping.actual)
        assertEquals(stopping, FocusRuntimeReducer.reduce(stopping, FocusRuntimeCommand.StopRequested, 20L))

        val ready = FocusRuntimeReducer.reduce(stopping, FocusRuntimeCommand.ServiceStopAcknowledged, 30L)
        assertEquals(FocusState.Ready, ready.actual)
        assertEquals(DesiredFocusState.Stopped, ready.desired)
    }

    @Test fun heartbeatCannotMarkStartingAsRunning() {
        val starting = FocusRuntimeSnapshot(
            desired = DesiredFocusState.Running,
            actual = FocusState.Starting
        )
        val result = FocusRuntimeReducer.reduce(starting, FocusRuntimeCommand.Heartbeat, 123L)
        assertFalse(result.actual == FocusState.Running)
        assertEquals(starting, result)
    }

    @Test fun heartbeatUpdatesOnlyRunningState() {
        val running = FocusRuntimeSnapshot(
            desired = DesiredFocusState.Running,
            actual = FocusState.Running
        )
        val updated = FocusRuntimeReducer.reduce(running, FocusRuntimeCommand.Heartbeat, 123L)
        assertTrue(updated.lastHeartbeatWallTimeMs == 123L)
        assertEquals(FocusState.Running, updated.actual)
    }
    @Test fun pauseAndResumeKeepDesiredAndActualStateInSync() {
        val running = FocusRuntimeSnapshot(
            desired = DesiredFocusState.Running,
            actual = FocusState.Running
        )
        val paused = FocusRuntimeReducer.reduce(
            running,
            FocusRuntimeCommand.PauseRequested,
            200L
        )
        assertEquals(DesiredFocusState.Paused, paused.desired)
        assertEquals(FocusState.Paused, paused.actual)

        val resumed = FocusRuntimeReducer.reduce(
            paused,
            FocusRuntimeCommand.ResumeRequested,
            300L
        )
        assertEquals(DesiredFocusState.Running, resumed.desired)
        assertEquals(FocusState.Running, resumed.actual)
    }

    @Test fun pauseCannotBeRequestedBeforeStartupAcknowledgement() {
        val starting = FocusRuntimeSnapshot(
            desired = DesiredFocusState.Running,
            actual = FocusState.Starting
        )
        val result = FocusRuntimeReducer.reduce(
            starting,
            FocusRuntimeCommand.PauseRequested,
            200L
        )
        assertEquals(starting, result)
    }

    @Test fun resumeCannotOverrideAnExplicitStop() {
        val stopped = FocusRuntimeSnapshot(
            desired = DesiredFocusState.Stopped,
            actual = FocusState.Ready
        )
        val result = FocusRuntimeReducer.reduce(
            stopped,
            FocusRuntimeCommand.ResumeRequested,
            200L
        )
        assertEquals(stopped, result)
    }

}
