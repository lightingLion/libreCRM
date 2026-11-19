package com.qiongge.callassistant.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp

internal fun dialSwitchDirection(currentIndex: Int, targetIndex: Int): Float =
    if (targetIndex < currentIndex) -1f else 1f

internal val dialDetailArrival = tween<Float>(durationMillis = 440, easing = LinearEasing)

internal fun dialDetailVisibility(outgoing: Float, incoming: Float, order: Int): Float {
    val rank = order.coerceIn(0, 3)
    val departure = outgoing / (0.7f - rank * 0.075f)
    val arrival = (incoming - rank * 0.14f) / (1f - rank * 0.14f)
    return (1f - smoothStep(departure)) * smoothStep(arrival)
}

private fun smoothStep(progress: Float): Float {
    val t = progress.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

internal fun Modifier.dialDetailMotion(visibility: () -> Float): Modifier =
    graphicsLayer {
        val fraction = visibility()
        alpha = fraction
        translationY = -(1f - fraction) * 10.dp.toPx()
    }.drawWithContent {
        val fraction = visibility()
        if (fraction >= 1f) drawContent()
        else if (fraction > 0f) {
            clipRect(bottom = size.height * (0.3f + 0.7f * fraction)) {
                this@drawWithContent.drawContent()
            }
        }
    }
