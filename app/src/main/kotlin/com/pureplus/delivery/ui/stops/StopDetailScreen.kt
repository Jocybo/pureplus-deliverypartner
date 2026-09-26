package com.pureplus.delivery.ui.stops

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pureplus.delivery.data.remote.Stop
import com.pureplus.delivery.ui.components.BackHeader
import com.pureplus.delivery.ui.components.FloorLiftTag
import com.pureplus.delivery.ui.components.LabeledRow
import com.pureplus.delivery.ui.components.LoadingState
import com.pureplus.delivery.ui.components.NotePanel
import com.pureplus.delivery.ui.components.OutlineButton
import com.pureplus.delivery.ui.components.PpCard
import com.pureplus.delivery.ui.components.PrimaryButton
import com.pureplus.delivery.ui.components.SectionLabel
import com.pureplus.delivery.ui.components.Tag
import com.pureplus.delivery.ui.components.Tone
import com.pureplus.delivery.ui.components.clickableNoRipple
import com.pureplus.delivery.ui.theme.PpColor
import com.pureplus.delivery.ui.theme.PpType
import com.pureplus.delivery.util.dialPhone
import com.pureplus.delivery.util.money
import com.pureplus.delivery.util.openMaps

/** DP-06 · Stop / order detail. DP-07 navigation is the "Navigate" hand-off to the device map app. */
@Composable
fun StopDetailScreen(
    orderId: Long,
    onDeliver: () -> Unit,
    onUnable: () -> Unit,
    onBack: () -> Unit,
    viewModel: StopDetailViewModel = viewModel(key = "stop_$orderId", factory = StopDetailViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(orderId) { viewModel.load(orderId) }

    Column(Modifier.fillMaxSize().background(PpColor.Canvas)) {
        BackHeader(title = state.stop?.receiverName ?: "Stop #$orderId", onBack = onBack, backLabel = "Stops")

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading -> LoadingState()
                state.error != null -> NotePanel(state.error!!, tone = Tone.Danger, modifier = Modifier.padding(16.dp))
                state.stop != null -> StopDetailBody(state.stop!!, context)
            }
        }

        Column(Modifier.fillMaxWidth().background(PpColor.Canvas).padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlineButton("Navigate", onClick = {
                    state.stop?.let { openMaps(context, it.latitude, it.longitude, it.addressText()) }
                }, modifier = Modifier.weight(1f))
                OutlineButton("Can't deliver", onClick = onUnable, borderColor = PpColor.DangerBorder, contentColor = PpColor.Danger, modifier = Modifier.weight(1f))
            }
            val postpaid = state.stop?.isPostpaid == true
            PrimaryButton(if (postpaid) "Start delivery · no cash" else "Start delivery", onClick = onDeliver, height = 60)
        }
    }
}

@Composable
private fun StopDetailBody(stop: Stop, context: android.content.Context) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stop.receiverNumber?.let { maskPhone(it) } ?: "No number", style = PpType.mono.copy(color = PpColor.Ink500))
            Text(
                "Call",
                style = PpType.bodyStrong.copy(color = PpColor.Brand700),
                modifier = Modifier
                    .clickableNoRipple { dialPhone(context, stop.receiverNumber) }
                    .padding(4.dp),
            )
        }

        PpCard {
            SectionLabel("Address")
            Spacer(Modifier.height(9.dp))
            Text(stop.addressText(), style = PpType.bodyStrong)
            Row(Modifier.fillMaxWidth().padding(top = 11.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                FloorLiftTag(stop.floorNo, stop.lift)
                stop.landmark?.let { Tag(it) }
            }
        }

        // Customer location on an OpenStreetMap map (shown only when the stop has coordinates).
        if (stop.latitude != null && stop.longitude != null) {
            StopsMap(
                stops = listOf(stop),
                modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)),
            )
        }

        if (!stop.items.isNullOrEmpty()) {
            PpCard {
                SectionLabel("Items")
                Spacer(Modifier.height(10.dp))
                stop.items.forEach { item ->
                    LabeledRow(item.productName ?: "Product ${item.productId}", "× ${item.quantity}")
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        PpCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    SectionLabel("Amount payable")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (stop.isPostpaid) "Postpaid · collect nothing" else "Collect at door · cash or UPI",
                        style = PpType.bodyStrong.copy(color = if (stop.isPostpaid) PpColor.Postpaid else PpColor.Success),
                    )
                }
                Text(money(stop.totalAmount), style = PpType.amount)
            }
        }
    }
}

private fun maskPhone(number: String): String =
    if (number.length >= 4) "+91 ${"•".repeat((number.length - 4).coerceAtLeast(0))}${number.takeLast(4)}" else number
