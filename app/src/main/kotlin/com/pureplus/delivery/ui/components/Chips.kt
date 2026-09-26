package com.pureplus.delivery.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pureplus.delivery.ui.theme.PpColor
import com.pureplus.delivery.ui.theme.PpFont

/** Rounded status chip (999 radius), uppercase — Delivered / Postpaid / Failed / Pending etc. */
@Composable
fun StatusChip(text: String, tone: Tone, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        color = tone.fg,
        fontFamily = PpFont.Ui,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 0.5.sp,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(tone.bg)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** Small rectangular tag for item / floor-lift markers (8dp radius). */
@Composable
fun Tag(
    text: String,
    modifier: Modifier = Modifier,
    fg: Color = PpColor.Ink700,
    bg: Color = PpColor.Canvas,
    border: Color = PpColor.Line,
) {
    Text(
        text,
        color = fg,
        fontFamily = PpFont.Ui,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** Pill filter (All / Pending / Postpaid …). Selected = ink fill, unselected = outline. */
@Composable
fun FilterChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text,
        color = if (selected) PpColor.White else PpColor.NeutralChip,
        fontFamily = PpFont.Ui,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) PpColor.Ink900 else PpColor.Canvas)
            .border(1.dp, if (selected) PpColor.Ink900 else PpColor.Line, RoundedCornerShape(999.dp))
            .clickableNoRipple(onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/**
 * Floor + lift marker. Ground = neutral, lift = neutral, no-lift = amber (extra effort + charge).
 */
@Composable
fun FloorLiftTag(floor: Int?, hasLift: Boolean?, modifier: Modifier = Modifier) {
    val (label, amber) = when {
        floor == null -> "Floor —" to false
        floor <= 0 -> "Ground" to false
        hasLift == true -> "${floor}F · lift" to false
        else -> "${floor}F · no lift" to true
    }
    if (amber) {
        Tag(label, modifier, fg = PpColor.Pending, bg = PpColor.PendingBg, border = PpColor.PendingLine)
    } else {
        Tag(label, modifier)
    }
}
