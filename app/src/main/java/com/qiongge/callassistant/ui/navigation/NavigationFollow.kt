package com.qiongge.callassistant.ui.navigation

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sign

internal object NavigationFollow {
    private const val speedThreshold = 1_150f
    private const val fullLagSpeed = 2_800f
    const val maxLagDp = 16f

    fun sampleLag(previousLag: Float, deltaDp: Float, elapsedMillis: Float): Float {
        val duration = elapsedMillis.coerceAtLeast(1f)
        val speed = abs(deltaDp) * 1_000f / duration
        val t = ((speed - speedThreshold) / (fullLagSpeed - speedThreshold)).coerceIn(0f, 1f)
        val desiredLag = deltaDp.sign * maxLagDp * t * t * (3f - 2f * t)
        return desiredLag + (previousLag - desiredLag) * exp(-duration / 20f)
    }

    fun catchUp(lagDp: Float, elapsedMillis: Float): Float =
        lagDp * exp(-elapsedMillis.coerceAtLeast(0f) / 23f)
}
