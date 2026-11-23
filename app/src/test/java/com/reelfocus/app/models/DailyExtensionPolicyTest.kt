package com.reelfocus.app.models

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyExtensionPolicyTest {
    @Test
    fun allowsAtMostTwoExtensionsPerLocalDay() {
        assertTrue(DailyExtensionPolicy.canConsume(0))
        assertTrue(DailyExtensionPolicy.canConsume(1))
        assertFalse(DailyExtensionPolicy.canConsume(2))
        assertFalse(DailyExtensionPolicy.canConsume(3))
    }
}
