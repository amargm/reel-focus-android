package com.reelfocus.app.models

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BreakEnforcementPolicyTest {
    @Test
    fun monitoredAppIsInterruptedOnlyWhileBreakIsActive() {
        assertTrue(BreakEnforcementPolicy.shouldInterruptMonitoredApp(true, 1L))
        assertFalse(BreakEnforcementPolicy.shouldInterruptMonitoredApp(true, 0L))
        assertFalse(BreakEnforcementPolicy.shouldInterruptMonitoredApp(false, 60_000L))
    }
}
