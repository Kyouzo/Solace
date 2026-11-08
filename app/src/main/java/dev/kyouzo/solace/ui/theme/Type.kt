package dev.kyouzo.solace.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Slightly tighter, heavier headings than the Material defaults; body text stays at default sizes for readability. */
val Typography = Typography(
    headlineMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.2).sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.1).sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.3.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.8.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp)
)
