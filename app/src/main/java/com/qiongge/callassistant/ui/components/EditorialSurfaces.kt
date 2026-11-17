package com.qiongge.callassistant.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.Shape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qiongge.callassistant.ui.theme.G2CornerShape
import kotlinx.coroutines.delay

data class CardRouteMotion(
    val routeKey: String? = null,
    val enabled: Boolean = false,
    val entering: Boolean = false,
)

val LocalCardRouteMotion = compositionLocalOf { CardRouteMotion() }

enum class CardMotionProfile(
    val travel: Dp,
    val stiffness: Float,
    val startDelayMs: Long,
) {
    Large(travel = 16.dp, stiffness = 460f, startDelayMs = 0L),
    Medium(travel = 10.dp, stiffness = 800f, startDelayMs = 18L),
    Compact(travel = 6.dp, stiffness = 1_300f, startDelayMs = 34L),
}

@Composable
fun RaisedCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    motionProfile: CardMotionProfile = CardMotionProfile.Medium,
    content: @Composable BoxScope.() -> Unit,
) {
    val routeMotion = LocalCardRouteMotion.current
    val entryMotionActive = routeMotion.enabled && routeMotion.routeKey != null
    val motionProgress = remember(routeMotion.routeKey) {
        Animatable(if (entryMotionActive && routeMotion.entering) 0f else 1f)
    }
    LaunchedEffect(routeMotion.routeKey, routeMotion.enabled, routeMotion.entering, motionProfile) {
        if (!entryMotionActive) {
            motionProgress.snapTo(1f)
        } else {
            if (routeMotion.entering && motionProfile.startDelayMs > 0L) {
                delay(motionProfile.startDelayMs)
            }
            motionProgress.animateTo(
                targetValue = if (routeMotion.entering) 1f else 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = motionProfile.stiffness,
                ),
            )
        }
    }

    val shape = G2CornerShape(20.dp)
    Surface(
        modifier = modifier
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = motionProfile.stiffness,
                ),
            )
            .graphicsLayer {
                val progress = motionProgress.value
                translationY = (1f - progress) * motionProfile.travel.toPx()
                alpha = 0.92f + 0.08f * progress
            }
            .shadow(
                elevation = 1.5.dp,
                shape = shape,
                ambientColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                spotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
            ),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.92f)),
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier.padding(contentPadding),
            content = content,
        )
    }
}

@Composable
fun InsetSurface(
    modifier: Modifier = Modifier,
    shape: Shape = G2CornerShape(18.dp),
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceVariant,
    insetEdge: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.34f),
    highlight: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.28f),
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        modifier = modifier.insetHighlight(shape, insetEdge, highlight),
        shape = shape,
        color = color,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.76f)),
        tonalElevation = 0.dp,
    ) {
        Box(content = content)
    }
}

@Composable
fun EditorialButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val shape = G2CornerShape(16.dp)
    val surfaceColor by animateColorAsState(when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant
        pressed -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.primary
    }, animationSpec = tween(120), label = "button surface")
    val textColor by animateColorAsState(when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        pressed -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onPrimary
    }, animationSpec = tween(120), label = "button text")
    val insetEdge = MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)
    val highlight = MaterialTheme.colorScheme.surface.copy(alpha = 0.28f)

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .heightIn(min = 52.dp)
            .then(
                if (pressed) {
                    Modifier.insetHighlight(shape, insetEdge, highlight)
                } else {
                    Modifier.shadow(
                        elevation = 2.dp,
                        shape = shape,
                        ambientColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                        spotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    )
                },
            )
            .border(
                width = 1.5.dp,
                color = if (pressed) {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.8f)
                } else {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.46f)
                },
                shape = shape,
            ),
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = surfaceColor,
            contentColor = textColor,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = if (pressed) 0.dp else 1.dp,
            pressedElevation = 0.dp,
        ),
        interactionSource = interactionSource,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
    }
}

fun Modifier.insetHighlight(
    shape: Shape,
    insetEdge: Color,
    highlight: Color,
    strokeWidth: Dp = 1.dp,
): Modifier = drawWithContent {
    drawContent()
    val outline = shape.createOutline(size, layoutDirection, this)
    val brush = Brush.linearGradient(colors = listOf(insetEdge, highlight))
    val stroke = Stroke(width = strokeWidth.toPx())
    when (outline) {
        is Outline.Generic -> drawPath(outline.path, brush, style = stroke)
        is Outline.Rounded -> drawPath(Path().apply { addRoundRect(outline.roundRect) }, brush, style = stroke)
        is Outline.Rectangle -> drawRect(brush, style = stroke)
    }
}

@Composable
fun InsetPill(
    text: String,
    modifier: Modifier = Modifier,
) {
    val insetEdge = MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)
    val highlight = MaterialTheme.colorScheme.surface.copy(alpha = 0.28f)
    InsetSurface(
        modifier = modifier,
        shape = G2CornerShape(50.dp),
        insetEdge = insetEdge,
        highlight = highlight,
    ) {
        Text(
            text = text,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
