package com.reelfocus.app.macrobenchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReelFocusBaselineProfile {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun startupAndCoreNavigation() = baselineProfileRule.collect(
        packageName = "com.reelfocus.app"
    ) {
        pressHome()
        startActivityAndWait()
        device.findObject(By.text("A five-hour phone day")).click()
        device.pressBack()
        device.findObject(By.text("Profile")).click()
    }
}