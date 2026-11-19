package com.qiongge.callassistant.ui.components

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign

internal object WorkbookDragMotion {
    const val scrollThreshold = 48f
    const val maximumScrollSpeed = 780f
    const val maximumAcceleration = 30_000f
    const val maximumScale = 0.045f
    private const val stiffness = 320f
    private const val damping = 36f
    private const val catchUpDistance = 24f
    private const val scrollAcceleration = 3_200f
    private const val responseRadius = 72f

    fun acceleration(error: Float, velocity: Float): Float {
        val excess = (abs(error) - catchUpDistance).coerceAtLeast(0f)
        val force = stiffness * (error + excess * error.sign) - damping * velocity
        return force.coerceIn(-maximumAcceleration, maximumAcceleration)
    }

    fun scrollTarget(displacement: Float): Float {
        val range = ((abs(displacement) - scrollThreshold) / 160f).coerceIn(0f, 1f)
        return displacement.sign * maximumScrollSpeed * range.pow(1.6f)
    }

    fun scrollSpeed(current: Float, target: Float, seconds: Float): Float {
        val limit = scrollAcceleration * seconds
        return current + (target - current).coerceIn(-limit, limit)
    }

    fun influence(distance: Float): Float {
        val t = (abs(distance) / responseRadius).coerceIn(0f, 1f)
        return 1f - t * t * t * (t * (6f * t - 15f) + 10f)
    }
}
