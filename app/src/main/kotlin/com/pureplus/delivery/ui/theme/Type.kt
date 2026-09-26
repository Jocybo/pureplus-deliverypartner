package com.pureplus.delivery.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Two families, per the design system:
 *  - [Ui]  → UI, headings, labels (Archivo in the mocks).
 *  - [Mono] → every amount, count, order id, timestamp (IBM Plex Mono in the mocks; tabular).
 *
 * We map to the platform sans/monospace families so the app is 100% reliable offline with no
 * runtime font download. The sans/mono split — the meaningful part of the system — is preserved.
 * (Swapping in the branded Archivo / IBM Plex Mono via bundled or downloadable fonts is a later polish;
 *  point both vals at the branded FontFamily and nothing else changes.)
 */
object PpFont {
    val Ui: FontFamily = FontFamily.SansSerif
    val Mono: FontFamily = FontFamily.Monospace
}

/** Semantic text styles used across screens (named, so screens don't re-declare sizes). */
object PpType {
    val amountXl = TextStyle(fontFamily = PpFont.Mono, fontWeight = FontWeight.ExtraBold, fontSize = 46.sp, lineHeight = 48.sp)
    val amount   = TextStyle(fontFamily = PpFont.Mono, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 32.sp)
    val amountMd = TextStyle(fontFamily = PpFont.Mono, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 20.sp)
    val mono     = TextStyle(fontFamily = PpFont.Mono, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp)
    val monoSm   = TextStyle(fontFamily = PpFont.Mono, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp)

    val screenTitle = TextStyle(fontFamily = PpFont.Ui, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 29.sp, letterSpacing = (-0.5).sp)
    val cardTitle   = TextStyle(fontFamily = PpFont.Ui, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 22.sp)
    val body        = TextStyle(fontFamily = PpFont.Ui, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp)
    val bodyStrong  = TextStyle(fontFamily = PpFont.Ui, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp)
    val bodySm      = TextStyle(fontFamily = PpFont.Ui, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp)
    val label       = TextStyle(fontFamily = PpFont.Ui, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 1.2.sp)
    val button      = TextStyle(fontFamily = PpFont.Ui, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 18.sp)
}

val PpTypography = Typography(
    titleLarge = PpType.screenTitle,
    titleMedium = PpType.cardTitle,
    bodyLarge = PpType.body,
    bodyMedium = PpType.bodySm,
    labelLarge = PpType.button,
    labelSmall = PpType.label,
)
