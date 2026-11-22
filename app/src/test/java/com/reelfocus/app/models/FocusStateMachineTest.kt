package com.reelfocus.app.models

import org.junit.Assert.assertEquals
import org.junit.Test

class FocusStateMachineTest {
    private data class Transition(val from: FocusState, val command: FocusCommand, val to: FocusState)

    private val allowed = listOf(
        Transition(FocusState.NotSetUp, FocusCommand.SetupCompleted, FocusState.Ready),
        Transition(FocusState.Ready, FocusCommand.StartRequested, FocusState.Starting),
        Transition(FocusState.Ready, FocusCommand.RequirementsMissing, FocusState.NotSetUp),
        Transition(FocusState.Ready, FocusCommand.PermissionLost, FocusState.Degraded),
        Transition(FocusState.Starting, FocusCommand.ServiceStarted, FocusState.Running),
        Transition(FocusState.Starting, FocusCommand.StartFailed, FocusState.Ready),
        Transition(FocusState.Starting, FocusCommand.PermissionLost, FocusState.Degraded),
        Transition(FocusState.Starting, FocusCommand.StopRequested, FocusState.Stopping),
        Transition(FocusState.Running, FocusCommand.PauseRequested, FocusState.Paused),
        Transition(FocusState.Running, FocusCommand.BreakStarted, FocusState.Break),
        Transition(FocusState.Running, FocusCommand.SessionLimitReached, FocusState.SessionLimitReached),
        Transition(FocusState.Running, FocusCommand.DailyLimitReached, FocusState.DailyLimitReached),
        Transition(FocusState.Running, FocusCommand.PermissionLost, FocusState.Degraded),
        Transition(FocusState.Running, FocusCommand.StopRequested, FocusState.Stopping),
        Transition(FocusState.Paused, FocusCommand.ResumeRequested, FocusState.Running),
        Transition(FocusState.Paused, FocusCommand.PermissionLost, FocusState.Degraded),
        Transition(FocusState.Paused, FocusCommand.StopRequested, FocusState.Stopping),
        Transition(FocusState.Break, FocusCommand.BreakEnded, FocusState.Running),
        Transition(FocusState.Break, FocusCommand.SessionLimitReached, FocusState.SessionLimitReached),
        Transition(FocusState.Break, FocusCommand.DailyLimitReached, FocusState.DailyLimitReached),
        Transition(FocusState.Break, FocusCommand.PermissionLost, FocusState.Degraded),
        Transition(FocusState.Break, FocusCommand.StopRequested, FocusState.Stopping),
        Transition(FocusState.SessionLimitReached, FocusCommand.BreakStarted, FocusState.Break),
        Transition(FocusState.SessionLimitReached, FocusCommand.ExtensionGranted, FocusState.Running),
        Transition(FocusState.SessionLimitReached, FocusCommand.DailyLimitReached, FocusState.DailyLimitReached),
        Transition(FocusState.SessionLimitReached, FocusCommand.PermissionLost, FocusState.Degraded),
        Transition(FocusState.SessionLimitReached, FocusCommand.StopRequested, FocusState.Stopping),
        Transition(FocusState.DailyLimitReached, FocusCommand.NewDay, FocusState.Ready),
        Transition(FocusState.DailyLimitReached, FocusCommand.PermissionLost, FocusState.Degraded),
        Transition(FocusState.DailyLimitReached, FocusCommand.StopRequested, FocusState.Stopping),
        Transition(FocusState.Degraded, FocusCommand.PermissionsRestored, FocusState.Ready),
        Transition(FocusState.Degraded, FocusCommand.StopRequested, FocusState.Stopping),
        Transition(FocusState.Stopping, FocusCommand.ServiceStopped, FocusState.Ready),
        Transition(FocusState.Stopping, FocusCommand.PermissionLost, FocusState.Degraded)
    )

    @Test fun everyAllowedTransitionMatchesTheContract() {
        allowed.forEach { transition ->
            assertEquals(
                "${transition.from} + ${transition.command}",
                transition.to,
                FocusStateMachine.transition(transition.from, transition.command)
            )
        }
    }

    @Test fun everyUnlistedTransitionIsRejected() {
        val allowedPairs = allowed.map { it.from to it.command }.toSet()
        FocusState.values().forEach { state ->
            FocusCommand.values().forEach { command ->
                if ((state to command) !in allowedPairs) {
                    assertEquals(
                        "Illegal transition ${state} + ${command} must be a no-op",
                        state,
                        FocusStateMachine.transition(state, command)
                    )
                }
            }
        }
    }

    @Test fun repeatedCommandsAreIdempotent() {
        assertEquals(FocusState.Starting, FocusStateMachine.transition(FocusState.Starting, FocusCommand.StartRequested))
        assertEquals(FocusState.Running, FocusStateMachine.transition(FocusState.Running, FocusCommand.StartRequested))
        assertEquals(FocusState.Stopping, FocusStateMachine.transition(FocusState.Stopping, FocusCommand.StopRequested))
        assertEquals(FocusState.Ready, FocusStateMachine.transition(FocusState.Ready, FocusCommand.StopRequested))
    }
}
