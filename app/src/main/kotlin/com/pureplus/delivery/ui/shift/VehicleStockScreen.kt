package com.pureplus.delivery.ui.shift

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pureplus.delivery.data.remote.VehicleReturn
import com.pureplus.delivery.data.remote.VehicleStock
import com.pureplus.delivery.data.remote.VehicleStockLine
import com.pureplus.delivery.data.remote.VehicleTrip
import com.pureplus.delivery.ui.components.BackHeader
import com.pureplus.delivery.ui.components.CountStepper
import com.pureplus.delivery.ui.components.LabeledRow
import com.pureplus.delivery.ui.components.LoadingState
import com.pureplus.delivery.ui.components.NotePanel
import com.pureplus.delivery.ui.components.PpCard
import com.pureplus.delivery.ui.components.PrimaryButton
import com.pureplus.delivery.ui.components.SectionLabel
import com.pureplus.delivery.ui.components.StatTile
import com.pureplus.delivery.ui.components.Tone
import com.pureplus.delivery.ui.theme.PpColor
import com.pureplus.delivery.ui.theme.PpType

/** DP-03 / DP-15 · Vehicle stock — van inventory, trip & return history, and record-a-return. */
@Composable
fun VehicleStockScreen(
    onBack: () -> Unit,
    viewModel: VehicleStockViewModel = viewModel(factory = VehicleStockViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(PpColor.Canvas)) {
        BackHeader("Vehicle stock", onBack, backLabel = "Me")

        when {
            state.loading -> Box(Modifier.weight(1f).fillMaxWidth()) { LoadingState() }
            state.stock == null -> Box(Modifier.weight(1f).fillMaxWidth()) {
                NotePanel(state.error ?: "Couldn't load vehicle stock.", tone = Tone.Danger, modifier = Modifier.padding(16.dp))
            }
            else -> Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SnapshotCard(state.stock!!)
                OnBoardCard(state.stock!!.lines)
                ReturnCard(state, viewModel)
                TripsCard(state.trips)
                ReturnsCard(state.returns)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SnapshotCard(stock: VehicleStock) {
    PpCard {
        SectionLabel("This shift")
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(stock.trips.toString(), "Load-out trips", Modifier.weight(1f))
            StatTile(stock.capacity?.toString() ?: "—", "Van capacity", Modifier.weight(1f))
            StatTile(stock.stockMode ?: "—", "Mode", Modifier.weight(1f))
        }
        stock.shiftDate?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(10.dp))
            LabeledRow("Shift date", it)
        }
        stock.sessionStatus?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(6.dp)); LabeledRow("Session", it)
        }
    }
}

@Composable
private fun OnBoardCard(lines: List<VehicleStockLine>) {
    PpCard {
        SectionLabel("On the vehicle")
        Spacer(Modifier.height(10.dp))
        if (lines.isEmpty()) {
            Text("Nothing loaded yet.", style = PpType.bodySm.copy(color = PpColor.Ink500))
            return@PpCard
        }
        lines.forEachIndexed { i, l ->
            if (i > 0) Spacer(Modifier.height(12.dp))
            Text(l.productName ?: "Product ${l.productId}", style = PpType.cardTitle)
            Spacer(Modifier.height(6.dp))
            LabeledRow("On board", l.onBoard.toString())
            Spacer(Modifier.height(4.dp))
            LabeledRow("Reserved (out for delivery)", l.reserved.toString())
            Spacer(Modifier.height(4.dp))
            LabeledRow("Free", l.free.toString())
            Spacer(Modifier.height(4.dp))
            LabeledRow("Empties on board", l.emptiesOnBoard.toString())
        }
    }
}

@Composable
private fun ReturnCard(state: VehicleStockState, viewModel: VehicleStockViewModel) {
    PpCard {
        SectionLabel("Return to store")
        Spacer(Modifier.height(6.dp))
        Text(
            "Hand back undelivered full cans and collected empties. Only FREE fulls can be returned — cans reserved to out-for-delivery orders are locked until those are delivered, failed or cancelled.",
            style = PpType.bodySm.copy(color = PpColor.Ink500),
        )
        if (state.returnRows.isEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("Nothing on the vehicle to return.", style = PpType.bodySm.copy(color = PpColor.Ink500))
            return@PpCard
        }
        state.returnRows.forEach { row ->
            Spacer(Modifier.height(12.dp))
            Text(row.productName, style = PpType.cardTitle)
            Spacer(Modifier.height(6.dp))
            Text("Full cans returning (max ${row.free} free)", style = PpType.label.copy(color = PpColor.Ink500))
            Spacer(Modifier.height(6.dp))
            CountStepper(row.fulls, { viewModel.setFulls(row.productId, it) })
            Spacer(Modifier.height(8.dp))
            Text("Empties returning (max ${row.emptiesOnBoard})", style = PpType.label.copy(color = PpColor.Ink500))
            Spacer(Modifier.height(6.dp))
            CountStepper(row.empties, { viewModel.setEmpties(row.productId, it) })
        }
        state.message?.let { Spacer(Modifier.height(10.dp)); NotePanel(it, tone = Tone.Success) }
        state.error?.let { Spacer(Modifier.height(10.dp)); NotePanel(it, tone = Tone.Danger) }
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Record return", onClick = viewModel::recordReturn, loading = state.submitting)
    }
}

@Composable
private fun TripsCard(trips: List<VehicleTrip>) {
    PpCard {
        SectionLabel("Load-out trips")
        Spacer(Modifier.height(10.dp))
        if (trips.isEmpty()) {
            Text("No load-out trips yet.", style = PpType.bodySm.copy(color = PpColor.Ink500))
            return@PpCard
        }
        trips.forEachIndexed { i, t ->
            if (i > 0) Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Trip ${t.tripNo}", style = PpType.bodyStrong)
                    Text(t.loadedAt, style = PpType.bodySm.copy(color = PpColor.Ink500))
                    val batches = t.batches.filter { it.cans > 0 }
                    if (batches.isNotEmpty()) {
                        Text(
                            batches.joinToString(", ") { "${it.batchNumber}: ${it.cans}" },
                            style = PpType.monoSm.copy(color = PpColor.Ink500),
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                Text("${t.cans} cans", style = PpType.mono.copy(color = PpColor.Ink900))
            }
        }
    }
}

@Composable
private fun ReturnsCard(returns: List<VehicleReturn>) {
    PpCard {
        SectionLabel("Returns to store")
        Spacer(Modifier.height(10.dp))
        if (returns.isEmpty()) {
            Text("No returns recorded yet.", style = PpType.bodySm.copy(color = PpColor.Ink500))
            return@PpCard
        }
        returns.forEachIndexed { i, r ->
            if (i > 0) Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.returnedAt, style = PpType.bodyStrong)
                    Text("Shift ${r.shiftDate}", style = PpType.bodySm.copy(color = PpColor.Ink500))
                }
                Text("${r.fulls} full · ${r.empties} empty", style = PpType.mono.copy(color = PpColor.Ink900))
            }
        }
    }
}
