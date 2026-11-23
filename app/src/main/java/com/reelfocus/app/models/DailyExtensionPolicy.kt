package com.reelfocus.app.models

/** Daily allowance for deliberate five-minute session extensions. */
object DailyExtensionPolicy {
    const val MAX_PER_LOCAL_DAY = 2

    fun canConsume(currentCount: Int): Boolean = currentCount < MAX_PER_LOCAL_DAY
}
