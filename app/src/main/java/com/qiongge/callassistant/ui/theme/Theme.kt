package com.qiongge.callassistant.ui.theme

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.rememberPlatformOverscrollFactory
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp

private val AssistantColorScheme = lightColorScheme(
    primary = AppColors.pine,
    onPrimary = AppColors.white,
    primaryContainer = AppColors.pale,
    onPrimaryContainer = AppColors.ink,
    secondary = AppColors.mineral,
    onSecondary = AppColors.white,
    secondaryContainer = AppColors.pale,
    onSecondaryContainer = AppColors.ink,
    tertiary = AppColors.oxide,
    onTertiary = AppColors.white,
    tertiaryContainer = AppColors.paper,
    onTertiaryContainer = AppColors.ink,
    error = AppColors.oxide,
    onError = AppColors.white,
    errorContainer = AppColors.pale,
    onErrorContainer = AppColors.ink,
    background = AppColors.paper,
    onBackground = AppColors.ink,
    surface = AppColors.white,
    onSurface = AppColors.ink,
    surfaceVariant = AppColors.pale,
    onSurfaceVariant = AppColors.muted,
    outline = AppColors.sage,
    outlineVariant = AppColors.rule,
    scrim = AppColors.ink,
    inverseSurface = AppColors.ink,
    inverseOnSurface = AppColors.paper,
    inversePrimary = AppColors.pineSoft,
    surfaceTint = AppColors.pine,
)

private val AssistantShapes = Shapes(
    extraSmall = G2CornerShape(7.dp),
    small = G2CornerShape(10.dp),
    medium = G2CornerShape(14.dp),
    large = G2CornerShape(20.dp),
    extraLarge = G2CornerShape(24.dp),
)

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun AssistantTheme(content: @Composable () -> Unit) {
    // The PRD defines a single, fixed light palette; Android dynamic colors are disabled.
    MaterialTheme(
        colorScheme = AssistantColorScheme,
        typography = AssistantTypography,
        shapes = AssistantShapes,
    ) {
        val overscrollFactory = rememberPlatformOverscrollFactory(
            glowColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
        )
        CompositionLocalProvider(
            LocalOverscrollFactory provides overscrollFactory,
            content = content,
        )
    }
}
