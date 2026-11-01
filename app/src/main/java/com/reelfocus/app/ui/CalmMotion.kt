package com.reelfocus.app.ui

import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * One short entrance transition per screen. No repeating animator or frame loop,
 * keeping the calm motion lightweight and battery friendly.
 */
object CalmMotion {
    fun breatheOnce(view: View) {
        view.animate().scaleX(1.025f).scaleY(1.025f)
            .setDuration(950L)
            .withEndAction {
                view.animate().scaleX(1f).scaleY(1f).setDuration(1100L).start()
            }
            .start()
    }

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
