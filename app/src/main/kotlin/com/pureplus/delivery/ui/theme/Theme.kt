package com.pureplus.delivery.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Light-first scheme. The old app shipped a dark scheme; the design system deliberately flips this
 * to light for outdoor legibility (dark is a night-shift variant, not the default).
 */
private val LightScheme = lightColorScheme(
    primary = PpColor.Brand700,
    onPrimary = PpColor.White,
    primaryContainer = PpColor.Brand50,
    onPrimaryContainer = PpColor.BrandInk,
    secondary = PpColor.Brand500,
    onSecondary = PpColor.Ink900,
    background = PpColor.Canvas,
    onBackground = PpColor.Ink900,
    surface = PpColor.White,
    onSurface = PpColor.Ink900,
    surfaceVariant = PpColor.Canvas,
    onSurfaceVariant = PpColor.Ink500,
    outline = PpColor.Line,
    error = PpColor.Danger,
    onError = PpColor.White,
    errorContainer = PpColor.DangerBg,
    onErrorContainer = PpColor.Danger,
    scrim = Color(0x66000000),
)

@Composable
fun PurePlusDeliveryTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightScheme,
        typography = PpTypography,
        content = content,
    )
}
