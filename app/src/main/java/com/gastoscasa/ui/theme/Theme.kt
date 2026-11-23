package com.gastoscasa.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object AppColors {
    val Bg = Color(0xFFEEEEF4)
    val BgTop = Color(0xFFF6F2EE)
    val BgBottom = Color(0xFFE6E7F2)
    val Card = Color(0xFFFFFFFF)
    val Ink = Color(0xFF111111)
    val Muted = Color(0xFF8A8A99)
    val Line = Color(0xFFE7E7EE)
    val Soft = Color(0xFFF3F3F7)
    val Yellow = Color(0xFFFFD95A)
    val Teal = Color(0xFF4FD1B5)
    val Red = Color(0xFFFF6B6B)
    val Orange = Color(0xFFFF9F43)

    val background = Brush.verticalGradient(listOf(BgTop, Bg, BgBottom))

    val palette = listOf(
        "#FFD95A", "#4FD1B5", "#FF8A65", "#9B8CFF", "#64B5F6", "#F48FB1", "#A5D6A7", "#B0BEC5",
    )

    fun parse(hex: String): Color = runCatching {
        Color(android.graphics.Color.parseColor(hex))
    }.getOrDefault(Yellow)
}

private val Sans = FontFamily.SansSerif

val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 40.sp, letterSpacing = (-1).sp),
    headlineMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 24.sp),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 17.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontSize = 11.sp),
)

@Composable
fun GastosTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = AppColors.Ink,
            onPrimary = Color.White,
            secondary = AppColors.Teal,
            tertiary = AppColors.Yellow,
            background = AppColors.Bg,
            surface = AppColors.Card,
            onSurface = AppColors.Ink,
            onBackground = AppColors.Ink,
            surfaceVariant = AppColors.Soft,
            onSurfaceVariant = AppColors.Muted,
            outline = AppColors.Line,
            error = AppColors.Red,
        ),
        typography = AppTypography,
        content = content,
    )
}
