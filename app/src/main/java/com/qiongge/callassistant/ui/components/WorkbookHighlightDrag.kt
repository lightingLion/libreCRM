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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import com.qiongge.callassistant.ui.theme.AppMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

internal class WorkbookHighlightDrag {
    var center by mutableFloatStateOf(Float.NaN)
    var height by mutableFloatStateOf(0f)
    var stretch by mutableFloatStateOf(0f)
    var emphasis by mutableFloatStateOf(0f)
    var recenter: Job? = null
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
): Modifier {
    val scope = rememberCoroutineScope()
    val latestTop by rememberUpdatedState(passiveTop)
    val latestPick by rememberUpdatedState(onPick)
    val settleSpec = remember { AppMotion.selection<Float>() }
    return Modifier.pointerInput(scroll, selectedKey, enabled, horizontalPadding) {
        var settling: Job? = null
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            drag.recenter?.cancel()
            if (!enabled || down.isConsumed) return@awaitEachGesture
            settling?.cancel()
            drag.center = Float.NaN
            val layout = scroll.layoutInfo
            val item = layout.visibleItemsInfo.firstOrNull { it.key == selectedKey } ?: return@awaitEachGesture
            val height = item.size.toFloat()
            if (height <= 0f) return@awaitEachGesture
            val inset = horizontalPadding.toPx()
            val itemCenter = item.centerIn(layout)
            val visualCenter = latestTop().takeIf(Float::isFinite)
                ?.plus(height / 2)?.coerceIn(itemCenter - 12 * density, itemCenter + 12 * density) ?: itemCenter
            if (down.position.x !in inset..(size.width - inset) ||
                abs(down.position.y - visualCenter) > height / 2
            ) return@awaitEachGesture

            val grabOffset = visualCenter - down.position.y
            var displacement = 0f
            var acquired = false
            var released = false
            var tracking: Job? = null
            try {
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                    if (change.isConsumed) break
                    if (!change.pressed) {
                        if (acquired) {
                            change.consume()
                            released = true
                        }
                        break
                    }
                    displacement = change.position.y - down.position.y
                    if (!acquired) {
                        val horizontal = abs(change.position.x - down.position.x)
                        if (horizontal > viewConfiguration.touchSlop && horizontal > abs(displacement)) break
                        if (abs(displacement) < viewConfiguration.touchSlop) continue
                        acquired = true
                        drag.height = height
                        drag.center = visualCenter
                        drag.stretch = 0f
                        drag.emphasis = 1f
                        val viewportHeight = size.height.toFloat()
                        val pxPerDp = density
                        val padding = minOf(height / 2, viewportHeight / 2)
                        tracking = scope.launch {
                            scroll.scroll(MutatePriority.UserInput) {
                                var velocity = 0f
                                var autoSpeed = 0f
                                var lastFrame = withFrameNanos { it }
                                while (true) {
                                    val frame = withFrameNanos { it }
                                    val dt = ((frame - lastFrame) / 1_000_000_000f).coerceIn(0f, 0.032f)
                                    lastFrame = frame
                                    val target = (down.position.y + displacement + grabOffset)
                                        .coerceIn(padding, viewportHeight - padding)
                                    if (coroutineContext[MotionDurationScale]?.scaleFactor == 0f) {
                                        drag.center = target
                                        drag.stretch = 0f
                                    } else {
                                        val step = dt / 4
                                        var center = drag.center
                                        repeat(4) {
                                            velocity += WorkbookDragMotion.acceleration((target - center) / pxPerDp, velocity) * step
                                            val advanced = center + velocity * pxPerDp * step
                                            center = advanced.coerceIn(padding, viewportHeight - padding)
                                            if (advanced != center) velocity = 0f
                                        }
                                        drag.center = center
                                        drag.stretch = (velocity / 1_100f).coerceIn(-1f, 1f) * 0.08f
                                    }
                                    val requested = WorkbookDragMotion.scrollTarget(displacement / pxPerDp)
                                    val canMove = if (requested < 0) scroll.canScrollBackward else scroll.canScrollForward
                                    autoSpeed = WorkbookDragMotion.scrollSpeed(autoSpeed, if (canMove) requested else 0f, dt)
                                    if ((autoSpeed > 0f && scroll.canScrollForward) || (autoSpeed < 0f && scroll.canScrollBackward)) {
                                        scrollBy(autoSpeed * pxPerDp * dt)
                                    } else autoSpeed = 0f
                                }
                            }
                        }
                    }
                    change.consume()
                }
            } finally {
                tracking?.cancel()
                if (acquired) {
                    val live = scroll.layoutInfo
                    val target = if (released) {
                        live.visibleItemsInfo.minByOrNull { abs(drag.center - it.centerIn(live)) }
                    } else live.visibleItemsInfo.firstOrNull { it.key == selectedKey }
                    if (target == null) {
                        drag.center = Float.NaN
                    } else {
                        val key = target.key as Long
                        val targetCenter = target.centerIn(live)
                        settling = scope.launch {
                            val initialCenter = drag.center
                            val initialHeight = drag.height
                            val initialStretch = drag.stretch
                            try {
                                animate(0f, 1f, animationSpec = settleSpec) { fraction, _ ->
                                    drag.center = initialCenter + (targetCenter - initialCenter) * fraction
                                    drag.height = initialHeight + (target.size - initialHeight) * fraction
                                    drag.stretch = initialStretch * (1f - fraction)
                                    drag.emphasis = 1f - fraction
                                }
                                if (released && key != selectedKey) latestPick(key)
                            } finally {
                                drag.center = Float.NaN
                            }
                        }
                    }
                }
            }
        }
    }
}
