package com.reelfocus.app.ui

import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator

/**
 * Short, finite transitions only. No repeating animator, timer or frame loop.
 * Motion is deliberately one-shot to keep idle battery use unchanged.
 */
object CalmMotion {
    private val easing = DecelerateInterpolator()

    fun reveal(view: View) {
        view.alpha = 0f
        view.translationY = 14f * view.resources.displayMetrics.density
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(420L)
            .setInterpolator(easing)
            .start()
    }

    /** Reveals the screen's visible sections with a short, restrained stagger. */
    fun revealScreen(content: View) {
        reveal(content)
        val first = content as? ViewGroup ?: return
        val container = if (first.childCount == 1 && first.getChildAt(0) is ViewGroup) {
            first.getChildAt(0) as ViewGroup
        } else first
        revealChildren(container)
    }

    fun revealChildren(container: ViewGroup) {
        var delay = 45L
        for (index in 0 until container.childCount) {
            val child = container.getChildAt(index)
            if (child.visibility != View.VISIBLE || child.width == 0 && child.height == 0) continue
            child.alpha = 0f
            child.translationY = 10f * child.resources.displayMetrics.density
            child.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(delay)
                .setDuration(340L)
                .setInterpolator(easing)
                .start()
            delay += 42L
        }
    }

    /** A single inhale/exhale-like scale, then it rests completely. */
    fun breatheOnce(view: View) {
        view.animate()
            .scaleX(1.035f)
            .scaleY(1.035f)
            .setDuration(700L)
            .setInterpolator(easing)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(850L)
                    .setInterpolator(easing)
                    .start()
            }
            .start()
    }

    fun floatOnce(view: View) {
        val distance = 5f * view.resources.displayMetrics.density
        view.animate()
            .translationY(-distance)
            .setDuration(850L)
            .setInterpolator(easing)
            .withEndAction {
                view.animate().translationY(0f).setDuration(950L).setInterpolator(easing).start()
            }
            .start()
    }

    fun emphasize(view: View) {
        view.animate().cancel()
        view.scaleX = 1f
        view.scaleY = 1f
        view.animate()
            .scaleX(1.07f)
            .scaleY(1.07f)
            .setDuration(110L)
            .setInterpolator(easing)
            .withEndAction {
                view.animate().scaleX(1f).scaleY(1f).setDuration(180L).setInterpolator(easing).start()
            }
            .start()
    }
}