package com.pureplus.delivery.ui.stops

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import com.pureplus.delivery.R
import com.pureplus.delivery.data.remote.Stop
import com.pureplus.delivery.ui.components.FloorLiftTag
import com.pureplus.delivery.ui.components.StatusChip
import com.pureplus.delivery.ui.components.Tone
import com.pureplus.delivery.ui.components.clickableNoRipple
import com.pureplus.delivery.ui.theme.PpColor
import com.pureplus.delivery.ui.theme.PpType
import com.pureplus.delivery.util.money

private fun Stop.railColor(): Color = when {
    isDelivered -> PpColor.Success
    isFailed -> PpColor.Danger
    isPostpaid -> PpColor.Postpaid
    else -> PpColor.Brand700
}

/** DP-05 stop-list row. Left rail carries status colour; amount + payment term on the right. */
@Composable
fun StopCard(
    stop: Stop,
    index: Int,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    val rail = stop.railColor()
    Row(
        modifier = modifier
            .fillMaxWidth()
//            .height(androidx.compose.foundation.layout.IntrinsicSize.Min)
            .clip(RoundedCornerShape(12.dp))
            .background(PpColor.White)
            .border(
                width = if (highlighted) 2.dp else 1.dp,
                color = if (highlighted) PpColor.Brand700 else PpColor.Line,
                shape = RoundedCornerShape(12.dp),
            )
            .clickableNoRipple(onOpen),
    ) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(rail))
        Column(Modifier.padding(13.dp).fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(PpColor.Ink900),
                        contentAlignment = Alignment.Center,
                    ) { Text("%02d".format(index), style = PpType.monoSm.copy(color = PpColor.White)) }
                    Column(Modifier.padding(start = 10.dp)) {
                        Text(stop.receiverName ?: "Stop #${stop.orderId}", style = PpType.cardTitle)
                        stop.receiverNumber?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = PpType.bodySm.copy(color = PpColor.Ink700), maxLines = 1)
                        }
                        Text(stop.addressText(), style = PpType.bodySm.copy(color = PpColor.Ink500), maxLines = 1)
                    }
                }
//                Column(horizontalAlignment = Alignment.End, modifier = Modifier.background(color = Color.Red)) {
//                    Text(money(stop.totalAmount), style = PpType.amountMd.copy(color = if (stop.isPostpaid) PpColor.Postpaid else PpColor.Ink900))
//                    StatusChip(
//                        text = when {
//                            stop.isDelivered -> "Delivered"
//                            stop.isFailed -> "Failed"
//                            stop.isPostpaid -> "Postpaid"
//                            else -> "Collect"
//                        },
//                        tone = when {
//                            stop.isDelivered -> Tone.Success
//                            stop.isFailed -> Tone.Danger
//                            stop.isPostpaid -> Tone.Postpaid
//                            else -> Tone.Success
//                        },
//                        modifier = Modifier.padding(top = 4.dp),
//                    )
//                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 11.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (stop.cans > 0) com.pureplus.delivery.ui.components.Tag("${stop.cans} cans")
                FloorLiftTag(stop.floorNo, stop.lift)
            }
        }
    }
}
