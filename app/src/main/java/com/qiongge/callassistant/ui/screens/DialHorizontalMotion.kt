package com.qiongge.callassistant.ui.screens

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp

internal fun Modifier.dialHorizontalDrag(
    key: Any?,
    avoidSystemEdge: Boolean = false,
    onStart: (width: Float, distance: Float) -> Boolean,
    onMove: (distance: Float) -> Unit,
    onFinish: (velocity: Float, released: Boolean) -> Unit,
): Modifier = pointerInput(key) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val width = size.width.toFloat()
        val edge = if (avoidSystemEdge) 24.dp.toPx() else 0f
        if (width <= 0f || down.position.x !in edge..(width - edge)) return@awaitEachGesture
        var distance = 0f
        val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
            distance = over
            change.consume()
        } ?: return@awaitEachGesture
        if (!onStart(width, distance)) return@awaitEachGesture
        val velocity = VelocityTracker()
        velocity.addPosition(down.uptimeMillis, Offset.Zero)
        velocity.addPosition(start.uptimeMillis, Offset(distance, 0f))
        var lastMove = start.uptimeMillis
        var released = false
        try {
            released = horizontalDrag(start.id) { change ->
                distance += change.position.x - change.previousPosition.x
                velocity.addPosition(change.uptimeMillis, Offset(distance, 0f))
                lastMove = change.uptimeMillis
                onMove(distance)
                change.consume()
            }
        } finally {
            val upTime = currentEvent.changes.firstOrNull { it.id == start.id }?.uptimeMillis
            val speed = if (released && upTime != null && upTime - lastMove <= 80L) {
                velocity.calculateVelocity().x
            } else 0f
            onFinish(speed, released)
        }
    }
}
