package com.pureplus.delivery.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Doorstep Design System palette (see the design-system handoff).
 * Light-first: drivers work outdoors at midday, so near-black ink on light canvas.
 * Every colour here is a named token — screens never hardcode hex.
 */
object PpColor {
    // Ink (text / dark surfaces)
    val Ink900 = Color(0xFF0B1A28)   // headers, total bars, primary dark surfaces
    val Ink700 = Color(0xFF2C4356)
    val Ink500 = Color(0xFF5B7186)   // secondary text / labels

    // Structure
    val Line   = Color(0xFFDDE5EB)   // borders / dividers
    val LineSoft = Color(0xFFEDF2F6) // inner row dividers
    val Canvas = Color(0xFFF1F5F8)   // screen background
    val CanvasChip = Color(0xFFEEF2F5)
    val White  = Color(0xFFFFFFFF)

    // Brand
    val Brand700 = Color(0xFF0077B6) // primary actions
    val Brand500 = Color(0xFF00B4D8) // accent
    val Brand50  = Color(0xFFE4F6FB) // brand tint background
    val BrandInk = Color(0xFF005B8C) // text on brand tint
    val BrandOnDark = Color(0xFF7FD8EE)

    // Success · Cash / Delivered (green)
    val Success   = Color(0xFF0E7A54)
    val SuccessBg = Color(0xFFE1F4EC)
    val SuccessLine = Color(0xFFB7E0CE)

    // Pending · Queued offline (amber)
    val Pending   = Color(0xFF8A5000)
    val PendingBg = Color(0xFFFDF0DC)
    val PendingLine = Color(0xFFF0D3A0)

    // Failed · Variance · Dispute (red)
    val Danger    = Color(0xFFA81E16)
    val DangerBg  = Color(0xFFFBE7E6)
    val DangerLine = Color(0xFFF0C2BF)
    val DangerBorder = Color(0xFFE3B4B0)

    // Postpaid · Ledgered · B2B (violet)
    val Postpaid   = Color(0xFF4A3BB0)
    val PostpaidBg = Color(0xFFECEAFB)
    val PostpaidLine = Color(0xFFCDC7F2)

    // Neutral chip text
    val NeutralChip = Color(0xFF42586B)

    // Disabled action
    val DisabledBg = Color(0xFFE7EDF2)
    val DisabledFg = Color(0xFF93A5B3)

    // Stepper inactive border
    val StepperBorder = Color(0xFFC6D3DC)
}
