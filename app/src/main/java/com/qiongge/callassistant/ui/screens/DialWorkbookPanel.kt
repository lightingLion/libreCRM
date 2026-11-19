package com.qiongge.callassistant.ui.screens

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import com.qiongge.callassistant.data.local.model.TaskCustomerRow
import com.qiongge.callassistant.ui.components.elasticWorkbookHighlight
import com.qiongge.callassistant.ui.components.WorkbookHighlightDrag
import com.qiongge.callassistant.ui.components.WorkbookDragMotion
import com.qiongge.callassistant.ui.components.centerIn
import com.qiongge.callassistant.ui.components.projectedDragPosition
import com.qiongge.callassistant.ui.theme.AppSpacing
import com.qiongge.callassistant.ui.theme.AppMotion
import com.qiongge.callassistant.ui.theme.NumberTypography
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class DialWorkbookPushState(private val scope: CoroutineScope) {
    var open by mutableStateOf(false)
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    var content by mutableStateOf<(@Composable (Modifier) -> Unit)?>(null)
        private set
    private var settling: Job? = null
    private var dragOrigin = 0f

    fun show(interactive: Boolean, panel: @Composable (Modifier) -> Unit) {
        if (content == null) content = panel
        if (interactive) beginDrag() else settle(true)
    }

    fun beginDrag() {
        settling?.cancel()
        dragOrigin = progress
    }

    fun dragBy(distance: Float, width: Float) {
        if (width > 0f) progress = (dragOrigin + distance / width).coerceIn(0f, 1f)
    }

    fun finishDrag(velocity: Float, width: Float, released: Boolean) {
        val speed = if (width > 0f && released) velocity / width else 0f
        val projected = projectedDragPosition(progress, speed, 0.28f)
        val threshold = if (open) 0.56f else 0.34f
        settle(if (released) projected >= threshold else open, speed)
    }

    fun close() = settle(false)

    private fun settle(show: Boolean, velocity: Float = 0f) {
        settling?.cancel()
        open = show
        settling = scope.launch {
            animate(
                progress, if (show) 1f else 0f,
                initialVelocity = velocity.coerceIn(-4f, 4f),
                animationSpec = AppMotion.selection(),
            ) { value, _ -> progress = value.coerceIn(0f, 1f) }
            if (!show) content = null
        }
    }
}

@Composable
internal fun DialWorkbookPanel(
    members: List<TaskCustomerRow>,
    currentIndex: Int,
    saving: Boolean,
    onDismiss: () -> Unit,
    onOpen: (TaskCustomerRow) -> Unit,
    onSaveAndOpen: (TaskCustomerRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expandedId by remember { mutableStateOf<Long?>(null) }
    val selectedKey = members.getOrNull(currentIndex)?.membershipId
    val density = LocalDensity.current
    val scroll = rememberLazyListState(currentIndex.coerceAtLeast(0))
    val drag = remember { WorkbookHighlightDrag() }
    val scope = rememberCoroutineScope()
    fun centerOn(key: Long) {
        drag.recenter?.cancel()
        drag.recenter = scope.launch {
            try {
                val index = members.indexOfFirst { it.membershipId == key }
                if (index < 0) return@launch
                if (scroll.layoutInfo.visibleItemsInfo.none { it.key == key }) {
                    scroll.animateScrollToItem(index)
                }
                // The previously expanded row can still be shrinking during the first scroll.
                repeat(2) {
                    val layout = scroll.layoutInfo
                    val item = layout.visibleItemsInfo.firstOrNull { it.key == key } ?: return@launch
                    val distance = item.centerIn(layout) - (layout.viewportEndOffset - layout.viewportStartOffset) / 2f
                    if (abs(distance) <= 1f) return@launch
                    if (distance < 0f && !scroll.canScrollBackward || distance > 0f && !scroll.canScrollForward) return@launch
                    scroll.animateScrollBy(distance, animationSpec = AppMotion.workbookCentering())
                }
            } finally {
                if (drag.recenter === coroutineContext[Job]) drag.recenter = null
            }
        }
    }
    val recenter by rememberUpdatedState<() -> Boolean> {
        if (selectedKey == null || saving || drag.center.isFinite()) {
            false
        } else {
            centerOn(selectedKey)
            true
        }
    }
    val blurSteps = remember(density) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            List(13) { step ->
                if (step == 0) null else {
                    val radius = with(density) { step * 0.45f * this.density }
                    BlurEffect(radius, radius, TileMode.Decal)
                }
            }
        } else emptyList()
    }
    LaunchedEffect(scroll) {
        if (selectedKey == null) return@LaunchedEffect
        val distanceToCenter = snapshotFlow {
            val layout = scroll.layoutInfo
            layout.visibleItemsInfo.firstOrNull { it.key == selectedKey }?.let { item ->
                item.offset + item.size / 2f - (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
            }
        }.filterNotNull().first()
        // LazyList clamps the scroll at the real start and end of the workbook.
        scroll.scrollBy(distanceToCenter)
    }
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.safeDrawingPadding().padding(vertical = AppSpacing.sm)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = AppSpacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "客户名单",
                    Modifier.weight(1f)
                        .pointerInput(Unit) { detectTapGestures(onDoubleTap = { recenter() }) }
                        .semantics {
                            customActions = listOf(CustomAccessibilityAction("定位当前客户") { recenter() })
                        },
                    style = MaterialTheme.typography.titleLarge,
                )
                TextButton(onClick = onDismiss, enabled = !saving) { Text("关闭") }
            }
            LazyColumn(
                state = scroll,
                modifier = Modifier.weight(1f).fillMaxWidth().then(
                    elasticWorkbookHighlight(
                        scroll, selectedKey, MaterialTheme.colorScheme.secondaryContainer, AppSpacing.md, drag,
                        enabled = !saving,
                        onPick = { key ->
                            if (key != selectedKey) members.firstOrNull { it.membershipId == key }?.let(onOpen)
                            centerOn(key)
                        },
                        onDragStart = { expandedId = null },
                    ),
                ),
                contentPadding = PaddingValues(horizontal = AppSpacing.md, vertical = AppSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.joined),
            ) {
                itemsIndexed(members, key = { _, row -> row.membershipId }, contentType = { _, _ -> "customer" }) { index, row ->
                    val expanded = expandedId == row.membershipId
                    val isCurrent = row.membershipId == selectedKey
                    Surface(
                        modifier = Modifier.fillMaxWidth().graphicsLayer {
                            val layout = scroll.layoutInfo
                            val halfHeight = (layout.viewportEndOffset - layout.viewportStartOffset) / 2f
                            val viewportCenter = layout.viewportStartOffset + halfHeight
                            val current = layout.visibleItemsInfo.firstOrNull { it.key == selectedKey }
                            val center = if (current != null && (!scroll.canScrollBackward || !scroll.canScrollForward)) {
                                current.offset + current.size / 2f
                            } else viewportCenter
                            val item = layout.visibleItemsInfo.firstOrNull { it.key == row.membershipId }
                            val focused = if (item == null || !drag.center.isFinite()) 0f else
                                WorkbookDragMotion.influence((drag.center - item.centerIn(layout)) / density.density) * drag.emphasis
                            scaleX = 1f + WorkbookDragMotion.maximumScale * focused
                            scaleY = scaleX
                            val normalized = if (item == null || halfHeight <= 0f) 0f
                                else abs(item.offset + item.size / 2f - center) / halfHeight
                            val edge = ((normalized - 0.55f) / 0.45f).coerceIn(0f, 1f)
                            val fade = if (isCurrent) 0f else edge * edge * (3f - 2f * edge) * (1f - focused)
                            alpha = 1f - 0.66f * fade
                            renderEffect = blurSteps.getOrNull((fade * blurSteps.lastIndex).roundToInt())
                        },
                        shape = MaterialTheme.shapes.large,
                        color = Color.Transparent,
                        contentColor = if (isCurrent) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                    ) {
                        Column {
                            Row(
                                Modifier.fillMaxWidth()
                                    .then(if (isCurrent) Modifier.onSizeChanged { drag.headerHeight = it.height } else Modifier)
                                    .semantics { stateDescription = if (expanded) "已展开" else "已收起" }
                                    .clickable(
                                        enabled = !saving,
                                        onClickLabel = if (expanded) "收起资料" else "展开资料",
                                    ) {
                                        expandedId = if (expanded) null else row.membershipId
                                        if (!expanded && !isCurrent) {
                                            drag.glideTo(scroll, scope, row.membershipId, selectedKey)
                                            onOpen(row)
                                        }
                                    }
                                    .heightIn(min = AppSpacing.touchTarget)
                                    .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm),
                                horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("${index + 1}", style = MaterialTheme.typography.labelLarge)
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        row.customer.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val suffix = row.customer.phone.filter(Char::isDigit).takeLast(4)
                                    if (suffix.isNotEmpty()) {
                                        Text(
                                            "...$suffix",
                                            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                if (isCurrent) Text("当前", style = MaterialTheme.typography.labelMedium)
                                Text(if (expanded) "−" else "+", style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.clearAndSetSemantics {})
                            }
                            AnimatedVisibility(
                                visible = expanded,
                                enter = expandVertically(animationSpec = AppMotion.selection()),
                                exit = shrinkVertically(animationSpec = AppMotion.selection()),
                            ) {
                                Column(
                                    Modifier.fillMaxWidth().padding(horizontal = AppSpacing.lg),
                                    verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                                ) {
                                    Text(row.customer.phone, style = NumberTypography)
                                    Text(
                                        row.customer.customResult ?: row.customer.status.label,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    val property = listOfNotNull(row.building, row.room).joinToString(" · ")
                                    if (property.isNotBlank()) Text(property, style = MaterialTheme.typography.bodySmall)
                                    if (isCurrent) {
                                        TextButton(onClick = onDismiss, enabled = !saving) { Text("返回拨号页") }
                                    } else {
                                        TextButton(onClick = { onSaveAndOpen(row) }, enabled = !saving) { Text("保存当前并切换") }
                                        TextButton(onClick = { onOpen(row) }, enabled = !saving) { Text("不保存，直接切换") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
