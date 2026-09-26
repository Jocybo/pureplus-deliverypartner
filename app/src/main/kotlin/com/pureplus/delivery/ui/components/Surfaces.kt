package com.pureplus.delivery.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pureplus.delivery.ui.theme.PpColor

/** White card with the standard 14dp radius + hairline border. The workhorse surface. */
@Composable
fun PpCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(16.dp),
    background: Color = PpColor.White,
    border: Color = PpColor.Line,
    borderWidth: Int = 1,
    radius: Int = 14,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius.dp))
            .background(background)
            .border(BorderStroke(borderWidth.dp, border), RoundedCornerShape(radius.dp))
            .padding(padding),
        content = content,
    )
}

/** A tinted "note" panel (info / success / pending / danger / postpaid) — the consequence text banners. */
@Composable
fun NotePanel(
    text: String,
    tone: Tone,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(tone.bg)
            .border(BorderStroke(1.dp, tone.line), RoundedCornerShape(10.dp))
            .padding(horizontal = 13.dp, vertical = 11.dp),
    ) {
        androidx.compose.material3.Text(
            text,
            style = com.pureplus.delivery.ui.theme.PpType.bodyStrong.copy(color = tone.fg),
        )
    }
}

enum class Tone(val fg: Color, val bg: Color, val line: Color) {
    Info(PpColor.BrandInk, PpColor.Brand50, PpColor.Brand50),
    Success(PpColor.Success, PpColor.SuccessBg, PpColor.SuccessLine),
    Pending(PpColor.Pending, PpColor.PendingBg, PpColor.PendingLine),
    Danger(PpColor.Danger, PpColor.DangerBg, PpColor.DangerLine),
    Postpaid(PpColor.Postpaid, PpColor.PostpaidBg, PpColor.PostpaidLine),
    Neutral(PpColor.Ink500, PpColor.CanvasChip, PpColor.CanvasChip),
}
