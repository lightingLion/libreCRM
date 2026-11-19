package com.qiongge.callassistant.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.Dp
import com.qiongge.callassistant.ui.theme.AppMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

internal class WorkbookHighlightDrag {
    var center by mutableFloatStateOf(Float.NaN)
    var height by mutableFloatStateOf(0f)
    var emphasis by mutableFloatStateOf(0f)
    var recenter by mutableStateOf<Job?>(null)
    var headerHeight = 0
    private var settling: Job? = null

    fun catchHighlight() {
        settling?.cancel()
        settling = null
    }

    fun glideTo(scroll: LazyListState, scope: CoroutineScope, key: Long, fromKey: Long? = null) {
        catchHighlight()
        val layout = scroll.layoutInfo
        val from = layout.visibleItemsInfo.firstOrNull { it.key == fromKey }
        val initialCenter = center.takeIf(Float::isFinite) ?: from?.centerIn(layout) ?: return
        val initialHeight = if (center.isFinite()) height else from?.size?.toFloat() ?: return
        center = initialCenter
        height = initialHeight
        settling = scope.launch {
            animate(0f, 1f, animationSpec = AppMotion.workbookCentering()) { value, _ ->
                val fraction = value.coerceIn(0f, 1f)
                val live = scroll.layoutInfo
                val item = live.visibleItemsInfo.firstOrNull { it.key == key }
                if (item != null) {
                    center = initialCenter + (item.centerIn(live) - initialCenter) * fraction
                    height = initialHeight + (item.size - initialHeight) * fraction
                }
                emphasis = 1f - fraction
            }
            center = Float.NaN
            emphasis = 0f
        }
    }
}

internal fun LazyListItemInfo.centerIn(layout: LazyListLayoutInfo): Float =
    offset - layout.viewportStartOffset + size / 2f

@Composable
internal fun workbookDragInput(
    scroll: LazyListState,
    drag: WorkbookHighlightDrag,
    selectedKey: Long,
    enabled: Boolean,
    horizontalPadding: Dp,
    passiveTop: () -> Float,
    onPick: (Long) -> Unit,
    onDragStart: () -> Unit,
): Modifier {
    val scope = rememberCoroutineScope()
    val latestTop by rememberUpdatedState(passiveTop)
    val latestPick by rememberUpdatedState(onPick)
    val latestKey by rememberUpdatedState(selectedKey)
    val collapse by rememberUpdatedState(onDragStart)
    return Modifier.pointerInput(scroll, enabled, horizontalPadding) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            drag.recenter?.cancel()
            if (!enabled || down.isConsumed) return@awaitEachGesture
            val key = latestKey
            val layout = scroll.layoutInfo
            val item = layout.visibleItemsInfo.firstOrNull { it.key == key } ?: return@awaitEachGesture
            val height = if (drag.center.isFinite()) drag.height else item.size.toFloat()
            if (height <= 0f) return@awaitEachGesture
            val inset = horizontalPadding.toPx()
            val itemCenter = item.centerIn(layout)
            val visualCenter = drag.center.takeIf(Float::isFinite) ?: latestTop().takeIf(Float::isFinite)
                ?.plus(height / 2)?.coerceIn(itemCenter - 12 * density, itemCenter + 12 * density) ?: itemCenter
            if (down.position.x !in inset..(size.width - inset) ||
                abs(down.position.y - visualCenter) > height / 2
            ) return@awaitEachGesture

            val grabOffset = visualCenter - down.position.y
            val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
            var lastMove = down.uptimeMillis
            var speed = 0f
            var candidate = key
            var displacement = 0f
            var acquired = false
            var released = false
            var tracking: Job? = null
            var shrinking: Job? = null
            val viewportHeight = size.height.toFloat()
            val pxPerDp = density
            fun choose(position: Float): Long {
                val live = scroll.layoutInfo
                val closest = live.visibleItemsInfo.minByOrNull { abs(position - it.centerIn(live)) } ?: return candidate
                val previous = live.visibleItemsInfo.firstOrNull { it.key == candidate }
                if (previous != null && previous.key != closest.key) {
                    val advantage = abs(position - previous.centerIn(live)) - abs(position - closest.centerIn(live))
                    if (advantage < 12f * pxPerDp) return candidate
                }
                return closest.key as Long
            }
            try {
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                    if (change.isConsumed) break
                    if (!change.pressed) {
                        if (acquired) {
                            change.consume()
                            released = true
                            if (change.uptimeMillis - lastMove <= 80L) speed = tracker.calculateVelocity().y
                        }
                        break
                    }
                    displacement = change.position.y - down.position.y
                    tracker.addPosition(change.uptimeMillis, change.position)
                    lastMove = change.uptimeMillis
                    if (!acquired) {
                        val horizontal = abs(change.position.x - down.position.x)
                        if (horizontal > viewConfiguration.touchSlop && horizontal > abs(displacement)) break
                        if (abs(displacement) < viewConfiguration.touchSlop) continue
                        acquired = true
                        drag.catchHighlight()
                        drag.height = height
                        drag.emphasis = 1f
                        collapse()
                        val headerHeight = drag.headerHeight.toFloat()
                        if (headerHeight > 0f && height > headerHeight) {
                            shrinking = scope.launch {
                                animate(height, headerHeight, animationSpec = AppMotion.selection()) { value, _ ->
                                    drag.height = value
                                }
                            }
                        }
                        tracking = scope.launch {
                            scroll.scroll(MutatePriority.UserInput) {
                                var autoSpeed = 0f
                                var lastFrame = withFrameNanos { it }
                                while (true) {
                                    val frame = withFrameNanos { it }
                                    val dt = ((frame - lastFrame) / 1_000_000_000f).coerceIn(0f, 0.032f)
                                    lastFrame = frame
                                    val requested = WorkbookDragMotion.scrollTarget(displacement / pxPerDp)
                                    val canMove = if (requested < 0) scroll.canScrollBackward else scroll.canScrollForward
                                    autoSpeed = WorkbookDragMotion.scrollSpeed(autoSpeed, if (canMove) requested else 0f, dt)
                                    if ((autoSpeed > 0f && scroll.canScrollForward) || (autoSpeed < 0f && scroll.canScrollBackward)) {
                                        scrollBy(autoSpeed * pxPerDp * dt)
                                        candidate = choose(drag.center)
                                    } else autoSpeed = 0f
                                }
                            }
                        }
                    }
                    drag.center = (change.position.y + grabOffset).coerceIn(0f, viewportHeight)
                    candidate = choose(drag.center)
                    change.consume()
                }
            } finally {
                tracking?.cancel()
                shrinking?.cancel()
                if (acquired) {
                    val targetKey = if (released) {
                        choose(projectedDragPosition(drag.center, speed, 28f * pxPerDp))
                    } else key
                    if (scroll.layoutInfo.visibleItemsInfo.none { it.key == targetKey }) {
                        drag.center = Float.NaN
                    } else {
                        drag.glideTo(scroll, scope, targetKey)
                        if (released) latestPick(targetKey)
                    }
                }
            }
        }
    }
}
