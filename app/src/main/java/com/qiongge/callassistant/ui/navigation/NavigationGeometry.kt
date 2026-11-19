package com.qiongge.callassistant.ui.navigation

import kotlin.math.abs
import kotlin.math.roundToInt

internal object NavigationGeometry {
    const val itemCount = 3
    private const val baseShare = 0.30f
    private const val emphasisShare = 0.10f
    private const val responseDistance = 0.30f

    // Each reference anchor is the center of its slot when it alone takes 40% of the bar.
    fun anchor(index: Int): Float = index * baseShare + (baseShare + emphasisShare) / 2f

    fun influence(position: Float, index: Int): Float {
        val t = (abs(position - anchor(index)) / responseDistance).coerceIn(0f, 1f)
        val smootherstep = t * t * t * (t * (t * 6f - 15f) + 10f)
        return (1f - smootherstep).coerceIn(0f, 1f)
    }

    // Resolve widths from fixed anchors once. The resulting centers are used for hit-testing only.
    fun boundaries(width: Int, position: Float): IntArray {
        val weights = FloatArray(itemCount) { baseShare + emphasisShare * influence(position, it) }
        val total = weights.sum()
        var previous = 0f
        return IntArray(itemCount + 1) { index ->
            when (index) {
                0 -> 0
                itemCount -> width
                else -> {
                    previous += weights[index - 1]
                    (width * (previous / total)).roundToInt()
                }
            }
        }
    }

    fun target(width: Int, touchX: Float): Int {
        val slots = boundaries(width, touchX / width.coerceAtLeast(1))
        return (0 until itemCount).minBy { index -> abs(touchX - (slots[index] + slots[index + 1]) / 2f) }
    }
}
