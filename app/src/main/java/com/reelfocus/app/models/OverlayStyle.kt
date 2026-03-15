package com.reelfocus.app.models

enum class OverlayStyle {
    /** Classic rounded-rect with session label + countdown text. */
    TEXT,

    /** Circular progress ring (donut) that drains clockwise as time passes. */
    DONUT,

    /** Filled circle whose radius shrinks to zero as time runs out. */
    SHRINKING_CIRCLE
}
