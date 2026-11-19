package com.qiongge.callassistant.ui.theme

import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.rememberPlatformOverscrollFactory
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val AssistantColorScheme = lightColorScheme(
    primary = AppColors.pine,
    onPrimary = AppColors.white,
    primaryContainer = AppColors.pale,
    onPrimaryContainer = AppColors.ink,
    secondary = AppColors.mineralText,
    onSecondary = AppColors.white,
    secondaryContainer = AppColors.pale,
    onSecondaryContainer = AppColors.ink,
    tertiary = AppColors.oxideText,
    onTertiary = AppColors.white,
    tertiaryContainer = AppColors.paper,
    onTertiaryContainer = AppColors.ink,
    error = AppColors.oxideText,
    onError = AppColors.white,
    errorContainer = AppColors.errorLight,
    onErrorContainer = AppColors.errorDark,
    background = AppColors.paper,
    onBackground = AppColors.ink,
    surface = AppColors.white,
    onSurface = AppColors.ink,
    surfaceVariant = AppColors.pale,
    onSurfaceVariant = AppColors.mutedText,
    surfaceDim = AppColors.pale,
    surfaceBright = AppColors.white,
    surfaceContainerLowest = AppColors.white,
    surfaceContainerLow = AppColors.paper,
    surfaceContainer = lerp(AppColors.paper, AppColors.pale, 0.5f),
    surfaceContainerHigh = AppColors.lightSurfaceHigh,
    surfaceContainerHighest = AppColors.lightSurfaceHighest,
    outline = AppColors.mutedText,
    outlineVariant = AppColors.rule,
    scrim = AppColors.ink,
    inverseSurface = AppColors.ink,
    inverseOnSurface = AppColors.paper,
    inversePrimary = AppColors.pineSoft,
    surfaceTint = AppColors.pine,
)

private val AssistantDarkColorScheme = darkColorScheme(
    primary = AppColors.lightPine,
    onPrimary = AppColors.deepPine,
    primaryContainer = AppColors.deepPine,
    onPrimaryContainer = AppColors.lightPine,
    secondary = AppColors.lightMineral,
    onSecondary = AppColors.darkSurface,
    secondaryContainer = AppColors.darkRule,
    onSecondaryContainer = AppColors.lightMineral,
    tertiary = AppColors.lightOxide,
    onTertiary = AppColors.errorDark,
    tertiaryContainer = AppColors.darkRule,
    onTertiaryContainer = AppColors.lightOxide,
    error = AppColors.lightOxide,
    onError = AppColors.errorDark,
    errorContainer = AppColors.errorDark,
    onErrorContainer = AppColors.errorLight,
    background = AppColors.darkSurface,
    onBackground = AppColors.lightInk,
    surface = AppColors.darkSurface,
    onSurface = AppColors.lightInk,
    surfaceVariant = AppColors.darkRule,
    onSurfaceVariant = AppColors.lightMuted,
    surfaceDim = AppColors.darkSurface,
    surfaceBright = AppColors.darkRule,
    surfaceContainerLowest = lerp(AppColors.darkSurface, AppColors.deepPine, 0.15f),
    surfaceContainerLow = lerp(AppColors.darkSurface, AppColors.darkRule, 0.18f),
    surfaceContainer = lerp(AppColors.darkSurface, AppColors.darkRule, 0.36f),
    surfaceContainerHigh = lerp(AppColors.darkSurface, AppColors.darkRule, 0.54f),
    surfaceContainerHighest = lerp(AppColors.darkSurface, AppColors.darkRule, 0.78f),
    outline = AppColors.darkOutline,
    outlineVariant = AppColors.darkRule,
    inverseSurface = AppColors.lightInk,
    inverseOnSurface = AppColors.darkSurface,
    inversePrimary = AppColors.pine,
    surfaceTint = AppColors.lightPine,
)

private val AssistantShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

internal val ColorScheme.cardSurface
    get() = if (background.luminance() < 0.5f) surfaceContainerHigh else surfaceContainerLowest

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun AssistantTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val dynamic = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            dynamic.copy(background = if (darkTheme) dynamic.surface else dynamic.surfaceContainer)
        }
        darkTheme -> AssistantDarkColorScheme
        else -> AssistantColorScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
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
