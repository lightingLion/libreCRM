package com.qiongge.callassistant.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qiongge.callassistant.R
import com.qiongge.callassistant.ui.navigation.MainDestination
import com.qiongge.callassistant.ui.navigation.NavigationGeometry
import com.qiongge.callassistant.ui.navigation.NavigationFollow
import com.qiongge.callassistant.ui.theme.AppMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

private val navigationIcons = listOf(R.drawable.ic_folder_open, R.drawable.ic_image_add, R.drawable.ic_people)
private val iconRowHeight = 38.dp
private val topPadding = 10.dp

@Composable
internal fun MainNavigationBar(selected: MainDestination, onSelect: (MainDestination) -> Unit) {
    val scope = rememberCoroutineScope()
    val latestSelect by rememberUpdatedState(onSelect)
    val latestSelected by rememberUpdatedState(selected)
    val spring = remember { AppMotion.selection<Float>() }
    val tapMotion = remember { tween<Float>(durationMillis = 360, easing = FastOutSlowInEasing) }
    var trackedX by remember { mutableStateOf<Float?>(null) }
    var settlingPosition by remember { mutableStateOf<Float?>(null) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    var barWidth by remember { mutableIntStateOf(0) }
    val scheme = MaterialTheme.colorScheme

    // Absolute pixels are kept independently of both the animated slot widths and release position.
    fun position(width: Int): Float =
        trackedX?.div(width.coerceAtLeast(1))
            ?: settlingPosition
            ?: NavigationGeometry.anchor(selected.ordinal)

    fun settleTo(start: Float, target: MainDestination, dragged: Boolean = false) {
        settleJob?.cancel()
        settlingPosition = start
        settleJob = scope.launch {
            animate(start, NavigationGeometry.anchor(target.ordinal), animationSpec = if (dragged) spring else tapMotion) { value, _ ->
                settlingPosition = value
            }
            settlingPosition = null
        }
        if (target != latestSelected) latestSelect(target)
    }

    Box(
        Modifier.fillMaxWidth()
            .background(scheme.surfaceContainerLow)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Box(
            Modifier.fillMaxWidth()
                .clipToBounds()
                .onSizeChanged { barWidth = it.width }
                .selectableGroup()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(pass = PointerEventPass.Initial)
                        if (size.width <= 0) return@awaitEachGesture
                        val start = settlingPosition ?: NavigationGeometry.anchor(latestSelected.ordinal)
                        settleJob?.cancel()
                        settlingPosition = start
                        var dragging = false
                        var released = false
                        var rawX = down.position.x
                        var lagDp = 0f
                        var lastMoveNanos = 0L
                        var previousTime = down.uptimeMillis
                        var catchUpJob: Job? = null
                        down.consume()
                        try {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                                // Compose reports a system cancellation as a consumed up change.
                                if (pointer.isConsumed) break
                                if (abs(pointer.position.x - down.position.x) >= viewConfiguration.touchSlop) dragging = true
                                if (dragging && pointer.pressed && pointer.position.x != rawX) {
                                    val deltaDp = (pointer.position.x - rawX) / density
                                    val elapsed = (pointer.uptimeMillis - previousTime).toFloat()
                                    lagDp = NavigationFollow.sampleLag(lagDp, deltaDp, elapsed)
                                    lastMoveNanos = System.nanoTime()
                                    trackedX = pointer.position.x - lagDp * density
                                    if (abs(lagDp * density) >= 0.25f && catchUpJob?.isActive != true) {
                                        catchUpJob = scope.launch {
                                            var previousFrame = lastMoveNanos
                                            while (abs(lagDp * density) >= 0.25f) {
                                                withFrameNanos { frame ->
                                                    val decayStart = maxOf(previousFrame, lastMoveNanos + 24_000_000L)
                                                    lagDp = NavigationFollow.catchUp(lagDp, (frame - decayStart) / 1_000_000f)
                                                    previousFrame = frame
                                                    trackedX = rawX - lagDp * density
                                                }
                                            }
                                            lagDp = 0f
                                            trackedX = rawX
                                        }
                                    }
                                }
                                rawX = pointer.position.x
                                previousTime = pointer.uptimeMillis
                                pointer.consume()
                                if (!pointer.pressed) {
                                    if (pointer.position.y in 0f..size.height.toFloat()) {
                                        val releaseX = pointer.position.x
                                        val slots = NavigationGeometry.boundaries(size.width, start)
                                        val targetIndex = if (dragging) {
                                            NavigationGeometry.target(size.width, releaseX)
                                        } else {
                                            (0 until NavigationGeometry.itemCount).firstOrNull { releaseX < slots[it + 1] }
                                                ?: NavigationGeometry.itemCount - 1
                                        }
                                        val visualStart = trackedX?.div(size.width) ?: start
                                        settleTo(visualStart, MainDestination.entries[targetIndex], dragged = dragging)
                                        released = true
                                    }
                                    break
                                }
                            }
                        } finally {
                            catchUpJob?.cancel()
                            trackedX = null
                            if (!released) settlingPosition = null
                        }
                    }
                },
        ) {
            Box(Modifier.matchParentSize().drawWithCache {
                val centerY = (topPadding + iconRowHeight / 2).toPx()
                val width = 62.dp.toPx()
                val height = 36.dp.toPx()
                val top = centerY - height / 2
                val radius = CornerRadius(height / 2)
                val dimensions = Size(width, height)
                val glaze = Brush.verticalGradient(
                    listOf(lerp(scheme.primaryContainer, scheme.surfaceBright, 0.36f), scheme.primaryContainer),
                    startY = top,
                    endY = top + height,
                )
                val border = Stroke(width = 1.dp.toPx())
                onDrawBehind {
                    val x = trackedX ?: size.width * position(size.width.toInt())
                    val origin = Offset(x - width / 2, top)
                    drawRoundRect(glaze, origin, dimensions, radius)
                    drawRoundRect(
                        color = scheme.primary.copy(alpha = 0.24f),
                        topLeft = origin,
                        size = dimensions,
                        cornerRadius = radius,
                        style = border,
                    )
                }
            })
            Layout(
                modifier = Modifier.fillMaxWidth(),
                content = {
                    MainDestination.entries.forEach { destination ->
                        val selectedItem = selected == destination
                        val emphasis = NavigationGeometry.influence(position(barWidth), destination.ordinal)
                        Column(
                            modifier = Modifier.selectable(selectedItem, role = Role.Tab) {
                                settleTo(position(barWidth), destination)
                            }.semantics { contentDescription = destination.title }
                                .padding(top = topPadding, bottom = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Box(Modifier.fillMaxWidth().height(iconRowHeight), contentAlignment = Alignment.Center) {
                                Icon(
                                    painterResource(navigationIcons[destination.ordinal]),
                                    contentDescription = null,
                                    modifier = Modifier.size((19 + 4 * emphasis).dp),
                                    tint = lerp(scheme.onSurfaceVariant, scheme.onPrimaryContainer, emphasis),
                                )
                            }
                            Text(
                                destination.title,
                                Modifier.clearAndSetSemantics {}.padding(horizontal = 4.dp).graphicsLayer {
                                    alpha = emphasis
                                    scaleX = 0.82f + 0.18f * emphasis
                                    scaleY = scaleX
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = lerp(scheme.onSurfaceVariant, scheme.primary, emphasis),
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
            ) { measurables, constraints ->
                val width = constraints.maxWidth
                val slots = NavigationGeometry.boundaries(width, position(width))
                val items = measurables.mapIndexed { index, measurable ->
                    val itemWidth = slots[index + 1] - slots[index]
                    measurable.measure(constraints.copy(minWidth = itemWidth, maxWidth = itemWidth, minHeight = 0))
                }
                layout(width, items.maxOf { it.height }) {
                    items.forEachIndexed { index, item -> item.place(slots[index], 0) }
                }
            }
        }
    }
}
