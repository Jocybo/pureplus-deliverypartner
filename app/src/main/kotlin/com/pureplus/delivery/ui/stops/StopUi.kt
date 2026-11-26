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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pureplus.delivery.data.remote.Stop
import com.pureplus.delivery.ui.components.FloorLiftTag
import com.pureplus.delivery.ui.components.clickableNoRipple
import com.pureplus.delivery.ui.theme.PpColor
import com.pureplus.delivery.ui.theme.PpType
import com.pureplus.delivery.util.money
import com.pureplus.delivery.util.shortDate
import com.pureplus.delivery.util.timeOfDay

private fun Stop.railColor(): Color = when {
    isDelivered -> PpColor.Success
    isFailed -> PpColor.Danger
    isPostpaid -> PpColor.Postpaid
    else -> PpColor.Brand700
}

/** Small tinted label chip used in the order-info row. */
@Composable
private fun InfoBadge(label: String, textColor: Color, bgColor: Color) {
    Text(
        text = label,
        style = PpType.monoSm.copy(color = textColor),
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** DP-05 stop-list row. Left rail carries status colour; order placed time + urgency below address. */
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

            // ── Top row: index badge + receiver info ──────────────────────────
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
            }

            // ── Order info row: placed time + urgency + delivery type ─────────
            val showOrderInfo = stop.orderDate != null || stop.isCarriedOver || stop.isOverdue
            if (showOrderInfo) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .height(1.dp)
                        .background(PpColor.LineSoft),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Clock or calendar icon
                    val isScheduled = stop.orderType.equals("SCHEDULED", ignoreCase = true)
                    Icon(
                        imageVector = if (isScheduled) Icons.Outlined.Event else Icons.Outlined.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = when {
                            stop.isOverdue -> PpColor.Danger
                            stop.isCarriedOver -> PpColor.Pending
                            else -> PpColor.Ink500
                        },
                    )

                    // Placed time text
                    val placedText = when {
                        stop.isCarriedOver && stop.orderDate != null ->
                            "Placed ${shortDate(stop.orderDate)}"
                        stop.orderDate != null ->
                            "Placed ${timeOfDay(stop.orderDate)}"
                        else -> ""
                    }
                    if (placedText.isNotBlank()) {
                        Text(
                            text = placedText,
                            style = PpType.bodySm.copy(
                                color = when {
                                    stop.isOverdue -> PpColor.Danger
                                    stop.isCarriedOver -> PpColor.Pending
                                    else -> PpColor.Ink500
                                },
                            ),
                        )
                    }

                    // Urgency badge
                    when {
                        stop.isCarriedOver ->
                            InfoBadge("Carried over", PpColor.Pending, PpColor.PendingBg)
                        stop.isOverdue ->
                            InfoBadge("Overdue", PpColor.Danger, PpColor.DangerBg)
                    }

                    // Delivery type badge (scheduled only — instant is the default, no badge needed)
                    if (isScheduled) {
                        val slotText = stop.slotLabel?.let { "Slot $it" } ?: "Scheduled"
                        InfoBadge(slotText, PpColor.BrandInk, PpColor.Brand50)
                    }
                }
            }

            // ── Tags row: cans · floor/lift · ETA · road distance ────────────
            Row(Modifier.fillMaxWidth().padding(top = 11.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (stop.cans > 0) com.pureplus.delivery.ui.components.Tag("${stop.cans} cans")
                FloorLiftTag(stop.floorNo, stop.lift)
                stop.eta?.takeIf { it.isNotBlank() }?.let { com.pureplus.delivery.ui.components.Tag("~$it") }
                stop.roadKm?.let { com.pureplus.delivery.ui.components.Tag("${"%.1f".format(it)} km") }
            }
        }
    }
}
