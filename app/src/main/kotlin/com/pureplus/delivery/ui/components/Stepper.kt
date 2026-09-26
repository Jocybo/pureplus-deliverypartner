package com.pureplus.delivery.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pureplus.delivery.ui.theme.PpColor
import com.pureplus.delivery.ui.theme.PpType

/**
 * Big touch-target −/＋ stepper. "No keyboard at the door." Consequence text lives outside this row.
 * [min] clamps so counts never go negative.
 */
@Composable
fun CountStepper(
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    min: Int = 0,
    max: Int = 999,
    valueColor: androidx.compose.ui.graphics.Color = PpColor.Ink900,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StepBox("−", enabled = value > min, brand = false) { if (value > min) onChange(value - 1) }
        Text(
            value.toString(),
            style = PpType.amount,
            color = valueColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        StepBox("+", enabled = value < max, brand = true) { if (value < max) onChange(value + 1) }
    }
}

@Composable
private fun StepBox(symbol: String, enabled: Boolean, brand: Boolean, onClick: () -> Unit) {
    val border = if (brand) PpColor.Brand700 else PpColor.StepperBorder
    val bg = if (brand) PpColor.Brand50 else PpColor.White
    val fg = if (brand) PpColor.Brand700 else PpColor.Ink700
    Box(
        modifier = Modifier
            .size(50.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (enabled) bg else PpColor.DisabledBg)
            .border(2.dp, if (enabled) border else PpColor.Line, RoundedCornerShape(11.dp))
            .then(if (enabled) Modifier.clickableNoRipple(onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            symbol,
            fontFamily = com.pureplus.delivery.ui.theme.PpFont.Ui,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            fontSize = 23.sp,
            color = if (enabled) fg else PpColor.DisabledFg,
        )
    }
}
