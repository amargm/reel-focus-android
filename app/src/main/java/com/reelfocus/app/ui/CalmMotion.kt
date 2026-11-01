package com.reelfocus.app.ui

import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * One short entrance transition per screen. No repeating animator or frame loop,
 * keeping the calm motion lightweight and battery friendly.
 */
object CalmMotion {
    fun reveal(view: View) {
        view.alpha = 0f
        view.translationY = 14f * view.resources.displayMetrics.density
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(420L)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }
}
