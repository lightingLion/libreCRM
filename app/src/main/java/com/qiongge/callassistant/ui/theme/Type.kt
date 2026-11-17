package com.qiongge.callassistant.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.qiongge.callassistant.R

val LXGWFamily = FontFamily(
    Font(R.font.lxgw_wenkai_regular, FontWeight.Normal),
    Font(R.font.lxgw_wenkai_medium, FontWeight.Medium),
)

private val BaseTypography = Typography()

val AssistantTypography = BaseTypography.copy(
    displayLarge = BaseTypography.displayLarge.wenkai(FontWeight.Medium),
    displayMedium = BaseTypography.displayMedium.wenkai(FontWeight.Medium),
    displaySmall = BaseTypography.displaySmall.wenkai(FontWeight.Medium),
    headlineLarge = BaseTypography.headlineLarge.wenkai(FontWeight.Medium),
    headlineMedium = BaseTypography.headlineMedium.wenkai(FontWeight.Medium),
    headlineSmall = BaseTypography.headlineSmall.wenkai(FontWeight.Medium),
    titleLarge = BaseTypography.titleLarge.wenkai(FontWeight.Medium),
    titleMedium = BaseTypography.titleMedium.wenkai(FontWeight.Medium),
    titleSmall = BaseTypography.titleSmall.wenkai(FontWeight.Medium),
    bodyLarge = BaseTypography.bodyLarge.wenkai(FontWeight.Normal),
    bodyMedium = BaseTypography.bodyMedium.wenkai(FontWeight.Normal),
    bodySmall = BaseTypography.bodySmall.wenkai(FontWeight.Normal).copy(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = BaseTypography.labelLarge.wenkai(FontWeight.Medium),
    labelMedium = BaseTypography.labelMedium.wenkai(FontWeight.Medium).copy(fontSize = 13.sp, lineHeight = 18.sp),
    labelSmall = BaseTypography.labelSmall.wenkai(FontWeight.Medium).copy(fontSize = 13.sp, lineHeight = 18.sp),
)

private fun TextStyle.wenkai(weight: FontWeight): TextStyle =
    copy(fontFamily = LXGWFamily, fontWeight = weight)

val NumberTypography = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Medium,
    fontSize = 16.sp,
)
