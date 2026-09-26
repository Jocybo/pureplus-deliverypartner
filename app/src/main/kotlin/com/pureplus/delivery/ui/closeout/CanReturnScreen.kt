package com.pureplus.delivery.ui.closeout

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pureplus.delivery.ui.components.BackHeader
import com.pureplus.delivery.ui.components.CountStepper
import com.pureplus.delivery.ui.components.LabeledRow
import com.pureplus.delivery.ui.components.LoadingState
import com.pureplus.delivery.ui.components.NotePanel
import com.pureplus.delivery.ui.components.PpCard
import com.pureplus.delivery.ui.components.PrimaryButton
import com.pureplus.delivery.ui.components.SectionLabel
import com.pureplus.delivery.ui.components.Tone
import com.pureplus.delivery.ui.theme.PpColor
import com.pureplus.delivery.ui.theme.PpType

/** DP-15 · Empty can return, counted against the load-out baseline. */
@Composable
fun CanReturnScreen(
    onNext: (Long) -> Unit,
    viewModel: CanReturnViewModel = viewModel(factory = CanReturnViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done && state.sessionId > 0) onNext(state.sessionId) }

    Column(Modifier.fillMaxSize().background(PpColor.Canvas)) {
        Column(Modifier.fillMaxWidth().background(PpColor.White).padding(16.dp, 13.dp)) {
            CloseOutStepper(current = 1)
            Spacer(Modifier.height(11.dp))
            Text("Can return", style = PpType.screenTitle)
            Text("Counted against this morning's load-out baseline.", style = PpType.bodySm.copy(color = PpColor.Ink500), modifier = Modifier.padding(top = 4.dp))
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading -> LoadingState()
                state.error != null && state.rows.isEmpty() -> NotePanel(state.error!!, tone = Tone.Danger, modifier = Modifier.padding(16.dp))
                else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.rows.forEach { row ->
                        PpCard {
                            SectionLabel("Product ${row.productId}")
                            Spacer(Modifier.height(8.dp))
                            LabeledRow("Loaded out", row.loadedOut.toString())
                            Spacer(Modifier.height(4.dp))
                            LabeledRow("Delivered", row.delivered.toString())
                            Spacer(Modifier.height(10.dp))
                            SectionLabel("Empties collected")
                            Spacer(Modifier.height(6.dp))
                            CountStepper(row.emptiesCollected, { viewModel.setEmpties(row.productId, it) })
                            Spacer(Modifier.height(10.dp))
                            SectionLabel("Full cans returning")
                            Spacer(Modifier.height(6.dp))
                            CountStepper(row.fullsReturned, { viewModel.setFullsReturned(row.productId, it) })
                            if (row.variance != 0) {
                                Spacer(Modifier.height(10.dp))
                                SectionLabel("Breakage / loss", color = PpColor.Danger)
                                Spacer(Modifier.height(6.dp))
                                CountStepper(row.breakageLoss, { viewModel.setBreakage(row.productId, it) }, valueColor = PpColor.Danger)
                            }
                        }
                    }
                    if (state.unaccounted > 0) {
                        NotePanel("${state.unaccounted} can(s) unaccounted for. Record a reason (breakage/loss). Unexplained shrinkage blocks shift close and goes to store admin.", tone = Tone.Danger)
                    }
                    if (state.error != null) NotePanel(state.error!!, tone = Tone.Danger)
                }
            }
        }

        Column(Modifier.fillMaxWidth().background(PpColor.Canvas).padding(16.dp)) {
            PrimaryButton("Confirm returned quantities", onClick = viewModel::submit, loading = state.submitting)
        }
    }
}
