package com.pureplus.delivery.ui.self

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pureplus.delivery.data.remote.DriverDelivery
import com.pureplus.delivery.ui.components.BackHeader
import com.pureplus.delivery.ui.components.EmptyState
import com.pureplus.delivery.ui.components.LoadingState
import com.pureplus.delivery.ui.components.NotePanel
import com.pureplus.delivery.ui.components.PpCard
import com.pureplus.delivery.ui.components.StatusChip
import com.pureplus.delivery.ui.components.Tone
import com.pureplus.delivery.ui.theme.PpColor
import com.pureplus.delivery.ui.theme.PpType
import com.pureplus.delivery.util.money
import com.pureplus.delivery.util.shortDate
import com.pureplus.delivery.util.timeOfDay

/** DP-20 · Delivery history — the driver's completed/failed deliveries (last 30 days). */
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(PpColor.Canvas)) {
        BackHeader("Delivery history", onBack, backLabel = "Me")
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading -> LoadingState()
                state.error != null -> NotePanel(state.error!!, tone = Tone.Danger, modifier = Modifier.padding(16.dp))
                state.deliveries.isEmpty() -> EmptyState("Nothing yet", "Your completed and failed deliveries appear here.")
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Text("Last 30 days", style = PpType.label.copy(color = PpColor.Ink500)) }
                    items(state.deliveries) { d -> HistoryRow(d) }
                }
            }
        }
    }
}

private fun DriverDelivery.isFailed() = status.equals("FAILED", true)
private fun DriverDelivery.isPending() = status.equals("DELIVERED_PAYMENT_PENDING", true)

@Composable
private fun HistoryRow(delivery: DriverDelivery) {
    PpCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("Order #${delivery.orderId}", style = PpType.cardTitle)
                delivery.address?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = PpType.bodySm.copy(color = PpColor.Ink500), maxLines = 1)
                }
                delivery.deliveredAt?.let {
                    Text(
                        "${shortDate(it)} · ${timeOfDay(it)}",
                        style = PpType.bodySm.copy(color = PpColor.Ink500),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(money(delivery.amount ?: 0.0), style = PpType.mono.copy(color = PpColor.Ink900))
                val (label, tone) = when {
                    delivery.isFailed() -> "Failed" to Tone.Danger
                    delivery.isPending() -> "Payment pending" to Tone.Postpaid
                    else -> "Delivered" to Tone.Success
                }
                StatusChip(label, tone, Modifier.padding(top = 4.dp))
            }
        }
    }
}
