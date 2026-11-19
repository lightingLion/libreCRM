package com.qiongge.callassistant.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Brand palette from 开发文档.md and its dark surface counterparts.
 */
object AppColors {
    val paper = Color(0xFFF3EEE4)
    val ink = Color(0xFF252A27)
    val pine = Color(0xFF3E6855)
    val oxide = Color(0xFFC35D47)
    val mineral = Color(0xFF4D8790)
    val ochre = Color(0xFFD1A64D)
    val sage = Color(0xFF8F927B)
    val earth = Color(0xFF9A8064)
    val pineSoft = Color(0xFF66827C)
    val muted = Color(0xFF72766F)
    val rule = Color(0xFFD7D0C3)
    val pale = Color(0xFFE6DFD1)
    val white = Color(0xFFFBF8F1)

    // Darken existing PRD tokens for readable small text on paper surfaces.
    val mutedText = lerp(muted, ink, 0.30f)
    val oxideText = lerp(oxide, ink, 0.30f)
    val mineralText = lerp(mineral, ink, 0.36f)

    val deepPine = Color(0xFF17382B)
    val lightPine = Color(0xFFABD2BB)
    val darkSurface = Color(0xFF1A1F1B)
    val lightInk = Color(0xFFEAE7DE)
    val lightMuted = Color(0xFFC4C9BD)
    val darkRule = Color(0xFF434941)
    val darkOutline = Color(0xFF90998D)
    val lightMineral = Color(0xFFA5CED3)
    val lightOxide = Color(0xFFFFB69F)
    val errorLight = Color(0xFFFFDAD4)
    val errorDark = Color(0xFF5D2117)
    val lightSurfaceHigh = lerp(white, pale, 0.65f)
    val lightSurfaceHighest = lerp(pale, rule, 0.35f)
}
