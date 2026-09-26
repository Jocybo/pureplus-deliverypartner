package com.pureplus.delivery.ui.closeout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.pureplus.delivery.ui.theme.PpColor
import com.pureplus.delivery.ui.theme.PpType

/** The "1 · Cans → 2 · Cash → 3 · Check out" pill strip. [current] is 1-based; earlier steps show done. */
@Composable
fun CloseOutStepper(current: Int, modifier: Modifier = Modifier) {
    val steps = listOf("Cans", "Cash", "Check out")
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        steps.forEachIndexed { i, label ->
            val n = i + 1
            val done = n < current
            val active = n == current
            val bg = when { active -> PpColor.Brand700; done -> PpColor.SuccessBg; else -> PpColor.CanvasChip }
            val fg = when { active -> PpColor.White; done -> PpColor.Success; else -> PpColor.DisabledFg }
            Text(
                "$n · $label${if (done) " ✓" else ""}",
                style = PpType.label.copy(color = fg),
                modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(bg).padding(horizontal = 9.dp, vertical = 5.dp),
            )
            if (i < steps.lastIndex) {
                Box(Modifier.width(14.dp).height(2.dp).padding(horizontal = 2.dp).background(PpColor.Line))
            }
        }
    }
}
