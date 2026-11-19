package com.qiongge.callassistant.ui.components

import androidx.compose.animation.core.FloatSpringSpec
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

@Composable
internal fun elasticWorkbookHighlight(
    scroll: LazyListState,
    selectedKey: Long?,
    color: Color,
    horizontalPadding: Dp,
    drag: WorkbookHighlightDrag,
    enabled: Boolean,
    onPick: (Long) -> Unit,
): Modifier {
    if (selectedKey == null) return Modifier

    val maxLag = with(LocalDensity.current) { 12.dp.toPx() }
    val spring = remember { FloatSpringSpec(dampingRatio = 0.84f, stiffness = Spring.StiffnessMediumLow) }
    var paintedTop by remember(selectedKey) { mutableFloatStateOf(Float.NaN) }

    LaunchedEffect(scroll, selectedKey, maxLag) {
        coroutineScope {
            var target: Float? = null
            var follow: Job? = null
            snapshotFlow {
                val layout = scroll.layoutInfo
                layout.visibleItemsInfo.firstOrNull { it.key == selectedKey }
                    ?.let { (it.offset - layout.viewportStartOffset).toFloat() }
            }.collect { next ->
                target = next
                when {
                    next == null -> {
                        follow?.cancel()
                        paintedTop = Float.NaN
                    }
                    paintedTop.isNaN() || coroutineContext[MotionDurationScale]?.scaleFactor == 0f -> {
                        follow?.cancel()
                        paintedTop = next
                    }
                    follow?.isActive != true -> follow = launch {
                        var lastFrame = withFrameNanos { it }
                        var velocity = 0f
                        while (true) {
                            val frame = withFrameNanos { it }
                            val goal = target ?: break
                            val scale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
                            if (scale == 0f) {
                                paintedTop = goal
                                break
                            }
                            val dt = ((frame - lastFrame) / scale).toLong()
                            val start = paintedTop.coerceIn(goal - maxLag, goal + maxLag)
                            val top = spring.getValueFromNanos(dt, start, goal, velocity)
                            velocity = spring.getVelocityFromNanos(dt, start, goal, velocity)
                            paintedTop = top.coerceIn(goal - maxLag, goal + maxLag)
                            lastFrame = frame
                            if (abs(paintedTop - goal) < 0.25f && abs(velocity) < 4f) {
                                paintedTop = goal
                                break
                            }
                        }
                    }
                }
            }
        }
    }

    return workbookDragInput(scroll, drag, selectedKey, enabled, horizontalPadding, { paintedTop }, onPick)
        .drawWithContent {
        val layout = scroll.layoutInfo
        val selected = layout.visibleItemsInfo.firstOrNull { it.key == selectedKey }
        val dragging = drag.center.isFinite()
        if (dragging || (selected != null && selected.size > 0)) {
            val height: Float
            val top: Float
            if (dragging) {
                val extension = abs(drag.stretch) * drag.height
                height = drag.height + extension
                top = drag.center - drag.height / 2 - if (drag.stretch > 0f) extension else 0f
            } else {
                checkNotNull(selected)
                val actualTop = (selected.offset - layout.viewportStartOffset).toFloat()
                val offset = if (paintedTop.isNaN()) 0f else (paintedTop - actualTop).coerceIn(-maxLag, maxLag)
                val stretch = selected.size * 0.09f * (abs(offset) / maxLag)
                top = actualTop + offset - if (offset < 0f) stretch else 0f
                height = selected.size + stretch
            }
            val inset = horizontalPadding.toPx()
            clipRect {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(inset, top),
                    size = Size((size.width - 2 * inset).coerceAtLeast(0f), height),
                    cornerRadius = CornerRadius(20.dp.toPx()),
                )
            }
        }
        drawContent()
    }
}
