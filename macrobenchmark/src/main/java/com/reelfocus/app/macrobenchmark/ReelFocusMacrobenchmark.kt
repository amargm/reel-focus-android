package com.reelfocus.app.macrobenchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReelFocusMacrobenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStart() = benchmarkRule.measureRepeated(
        packageName = "com.reelfocus.app",
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
        setupBlock = { pressHome() },
        measureBlock = { startActivityAndWait() }
    )

    @Test
    fun homeScrollAndChallengeSheet() = benchmarkRule.measureRepeated(
        packageName = "com.reelfocus.app",
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
            startActivityAndWait()
        },
        measureBlock = {
            val centerX = device.displayWidth / 2
            device.swipe(centerX, device.displayHeight * 3 / 4, centerX, device.displayHeight / 3, 12)
            device.findObject(By.text("A five-hour phone day")).click()
            device.pressBack()
            device.findObject(By.text("Profile")).click()
        }
    )
}