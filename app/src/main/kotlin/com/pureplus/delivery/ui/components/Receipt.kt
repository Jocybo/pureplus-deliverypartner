package com.pureplus.delivery.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pureplus.delivery.ui.theme.PpColor
import com.pureplus.delivery.ui.theme.PpType
import com.pureplus.delivery.util.money

/**
 * The itemised money list ("money is a receipt"). Additions are amber, credits green, base rows plain,
 * and the total bar is always ink — it is the number said out loud.
 */
@Composable
fun Receipt(
    rows: List<ReceiptLine>,
    totalLabel: String,
    total: Double,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .border(1.dp, PpColor.Line, RoundedCornerShape(13.dp)),
    ) {
        rows.forEachIndexed { i, row -> ReceiptRow(row, showTopDivider = i > 0) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PpColor.Ink900)
                .padding(horizontal = 14.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(totalLabel.uppercase(), style = PpType.label.copy(color = PpColor.White))
            Text(money(total), style = PpType.amountMd.copy(color = PpColor.White))
        }
    }
}

@Composable
private fun ReceiptRow(row: ReceiptLine, showTopDivider: Boolean) {
    val bg = when (row.kind) {
        LineKind.Addition -> PpColor.PendingBg
        LineKind.Credit -> PpColor.SuccessBg
        LineKind.Base -> PpColor.White
    }
    val fg = when (row.kind) {
        LineKind.Addition -> PpColor.Pending
        LineKind.Credit -> PpColor.Success
        LineKind.Base -> PpColor.Ink900
    }
    val topLine: Color = when (row.kind) {
        LineKind.Addition -> PpColor.PendingLine
        LineKind.Credit -> PpColor.SuccessLine
        LineKind.Base -> PpColor.LineSoft
    }
    Column(modifier = Modifier.fillMaxWidth().background(bg)) {
        if (showTopDivider) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(topLine))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(row.label, style = PpType.body.copy(color = fg), modifier = Modifier.padding(end = 12.dp))
            Text(
                row.amountText ?: money(row.amount, signed = row.kind != LineKind.Base),
                style = PpType.mono.copy(color = fg),
            )
        }
    }
}

data class ReceiptLine(
    val label: String,
    val amount: Double,
    val kind: LineKind = LineKind.Base,
    /** override the formatted amount (e.g. to force a leading + / −). */
    val amountText: String? = null,
)

enum class LineKind { Base, Addition, Credit }
