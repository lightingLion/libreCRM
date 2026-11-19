package com.qiongge.callassistant.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import com.qiongge.callassistant.ui.theme.cardSurface
import com.qiongge.callassistant.ui.theme.AppMotion
import com.qiongge.callassistant.ui.theme.AppSpacing

@Composable
internal fun WorkbookCustomerRow(
    ordinal: Int,
    name: String,
    phone: String,
    result: String,
    propertyLabel: String?,
    isCurrent: Boolean,
    shape: Shape,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val container by animateColorAsState(
        targetValue = if (isCurrent) scheme.secondaryContainer else scheme.cardSurface,
        animationSpec = AppMotion.selection(),
        label = "workbook row container",
    )
    ListItem(
        modifier = modifier
            .clip(shape)
            .clickable(role = Role.Button, onClickLabel = "打开客户详情", onClick = onOpen)
            .semantics { if (isCurrent) stateDescription = "上次打开" },
        colors = ListItemDefaults.colors(
            containerColor = container,
            headlineColor = if (isCurrent) scheme.onSecondaryContainer else scheme.onSurface,
            supportingColor = if (isCurrent) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
        ),
        leadingContent = {
            Text(
                text = ordinal.toString(),
                modifier = Modifier.widthIn(min = AppSpacing.xl),
                style = type.labelLarge.copy(fontFeatureSettings = "tnum"),
                textAlign = TextAlign.Center,
                color = scheme.onSurfaceVariant,
            )
        },
        headlineContent = {
            Text(name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = type.titleMedium)
        },
        supportingContent = {
            Column(
                modifier = Modifier.padding(top = AppSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
            ) {
                Text(phone, style = type.bodyMedium.copy(fontFeatureSettings = "tnum"))
                val status = if (isCurrent) "上次打开 · $result" else result
                Text(
                    text = if (propertyLabel == null) status else "$status · $propertyLabel",
                    style = type.labelMedium,
                    color = if (isCurrent) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
                )
            }
        },
        trailingContent = {
            TextButton(onClick = onRemove, modifier = Modifier.heightIn(min = AppSpacing.touchTarget)) {
                Text("移出")
            }
        },
    )
}
