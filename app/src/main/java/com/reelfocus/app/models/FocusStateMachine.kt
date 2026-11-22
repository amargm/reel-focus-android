package com.reelfocus.app.models

/**
 * Authoritative monitoring lifecycle vocabulary.
 *
 * UI, service and notification code should send [FocusCommand]s and render the
 * resulting [FocusState]; they must not infer lifecycle from service-local flags.
 */
enum class FocusState {
    NotSetUp,
    Ready,
    Starting,
    Running,
    Paused,
    Break,
    SessionLimitReached,
    DailyLimitReached,
    Degraded,
    Stopping
}

enum class FocusCommand {
    SetupCompleted,
    RequirementsMissing,
    StartRequested,
    ServiceStarted,
    StartFailed,
    PauseRequested,
    ResumeRequested,
    BreakStarted,
    BreakEnded,
    SessionLimitReached,
    DailyLimitReached,
    ExtensionGranted,
    PermissionLost,
    PermissionsRestored,
    StopRequested,
    ServiceStopped,
    NewDay
}

/**
 * Pure, deterministic transition policy. Illegal commands are rejected by
 * leaving the state unchanged. Repeated commands are therefore harmless.
 *
 * Allowed transitions:
 * NotSetUp -> Ready
 * Ready -> Starting | NotSetUp | Degraded
 * Starting -> Running | Ready | Degraded | Stopping
 * Running -> Paused | Break | SessionLimitReached | DailyLimitReached | Degraded | Stopping
 * Paused -> Running | Degraded | Stopping
 * Break -> Running | SessionLimitReached | DailyLimitReached | Degraded | Stopping
 * SessionLimitReached -> Break | Running | DailyLimitReached | Degraded | Stopping
 * DailyLimitReached -> Ready | Degraded | Stopping
 * Degraded -> Ready | Stopping
 * Stopping -> Ready | Degraded
 */
object FocusStateMachine {
    fun transition(current: FocusState, command: FocusCommand): FocusState = when (current) {
        FocusState.NotSetUp -> when (command) {
            FocusCommand.SetupCompleted -> FocusState.Ready
            else -> current
        }
        FocusState.Ready -> when (command) {
            FocusCommand.StartRequested -> FocusState.Starting
            FocusCommand.RequirementsMissing -> FocusState.NotSetUp
            FocusCommand.PermissionLost -> FocusState.Degraded
            else -> current
        }
        FocusState.Starting -> when (command) {
            FocusCommand.ServiceStarted -> FocusState.Running
            FocusCommand.StartFailed -> FocusState.Ready
            FocusCommand.PermissionLost -> FocusState.Degraded
            FocusCommand.StopRequested -> FocusState.Stopping
            else -> current
        }
        FocusState.Running -> when (command) {
            FocusCommand.PauseRequested -> FocusState.Paused
            FocusCommand.BreakStarted -> FocusState.Break
            FocusCommand.SessionLimitReached -> FocusState.SessionLimitReached
            FocusCommand.DailyLimitReached -> FocusState.DailyLimitReached
            FocusCommand.PermissionLost -> FocusState.Degraded
            FocusCommand.StopRequested -> FocusState.Stopping
            else -> current
        }
        FocusState.Paused -> when (command) {
            FocusCommand.ResumeRequested -> FocusState.Running
            FocusCommand.PermissionLost -> FocusState.Degraded
            FocusCommand.StopRequested -> FocusState.Stopping
            else -> current
        }
        FocusState.Break -> when (command) {
            FocusCommand.BreakEnded -> FocusState.Running
            FocusCommand.SessionLimitReached -> FocusState.SessionLimitReached
            FocusCommand.DailyLimitReached -> FocusState.DailyLimitReached
            FocusCommand.PermissionLost -> FocusState.Degraded
            FocusCommand.StopRequested -> FocusState.Stopping
            else -> current
        }
        FocusState.SessionLimitReached -> when (command) {
            FocusCommand.BreakStarted -> FocusState.Break
            FocusCommand.ExtensionGranted -> FocusState.Running
            FocusCommand.DailyLimitReached -> FocusState.DailyLimitReached
            FocusCommand.PermissionLost -> FocusState.Degraded
            FocusCommand.StopRequested -> FocusState.Stopping
            else -> current
        }
        FocusState.DailyLimitReached -> when (command) {
            FocusCommand.NewDay -> FocusState.Ready
            FocusCommand.PermissionLost -> FocusState.Degraded
            FocusCommand.StopRequested -> FocusState.Stopping
            else -> current
        }
        FocusState.Degraded -> when (command) {
            FocusCommand.PermissionsRestored -> FocusState.Ready
            FocusCommand.StopRequested -> FocusState.Stopping
            else -> current
        }
        FocusState.Stopping -> when (command) {
            FocusCommand.ServiceStopped -> FocusState.Ready
            FocusCommand.PermissionLost -> FocusState.Degraded
            else -> current
        }
    }
}
