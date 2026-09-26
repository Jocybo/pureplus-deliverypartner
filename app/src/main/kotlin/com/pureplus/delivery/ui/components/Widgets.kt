package com.pureplus.delivery.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pureplus.delivery.ui.theme.PpColor
import com.pureplus.delivery.ui.theme.PpType

/** Rounded progress track. [fraction] is clamped to 0..1. */
@Composable
fun LinearProgress(
    fraction: Float,
    modifier: Modifier = Modifier,
    track: Color = PpColor.DisabledBg,
    bar: Color = PpColor.Brand700,
    height: Int = 14,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(track),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(bar),
        )
    }
}

/** A big number + caption tile (stops done, cash, goal…). Use inside a Row with weight(1f). */
@Composable
fun StatTile(
    value: String,
    caption: String,
    modifier: Modifier = Modifier,
    valueColor: Color = PpColor.Ink900,
    background: Color = PpColor.White,
    captionColor: Color = PpColor.Ink500,
    border: Color = PpColor.Line,
) {
    PpCard(modifier = modifier, background = background, border = border) {
        Text(value, style = PpType.amount.copy(color = valueColor))
        Text(caption, style = PpType.bodySm.copy(color = captionColor), modifier = Modifier.padding(top = 6.dp))
    }
}
