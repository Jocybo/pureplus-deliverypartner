package com.pureplus.delivery.ui.closeout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.pureplus.delivery.ui.components.LabeledRow
import com.pureplus.delivery.ui.components.LoadingState
import com.pureplus.delivery.ui.components.NotePanel
import com.pureplus.delivery.ui.components.OutlineButton
import com.pureplus.delivery.ui.components.PpCard
import com.pureplus.delivery.ui.components.PrimaryButton
import com.pureplus.delivery.ui.components.SectionLabel
import com.pureplus.delivery.ui.components.Tone
import com.pureplus.delivery.ui.theme.PpColor
import com.pureplus.delivery.ui.theme.PpType
import com.pureplus.delivery.util.money

/**
 * DP-22 · Shift check-out. Reconciled mode (from close-out) gates on cans+cash balancing; direct mode
 * ("checked in by mistake", [sessionId] null) does an attendance-only check-out when nothing was run.
 */
@Composable
fun CheckOutScreen(
    sessionId: Long?,
    onCheckedOut: () -> Unit,
    onGoCloseOut: () -> Unit,
    viewModel: CheckOutViewModel = viewModel(
        key = "checkout_${sessionId ?: "direct"}",
        factory = CheckOutViewModel.factory(sessionId),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onCheckedOut() }

    Column(Modifier.fillMaxSize().background(PpColor.Canvas)) {
        Column(Modifier.fillMaxWidth().background(PpColor.Ink900).padding(18.dp)) {
            if (!state.directMode) {
                CloseOutStepper(current = 3)
                Spacer(Modifier.height(11.dp))
            }
            Text(if (state.directMode) "End shift" else "Shift summary", style = PpType.screenTitle.copy(color = PpColor.White))
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading -> LoadingState()
                state.directMode -> DirectBody(state)
                else -> ReconciledBody(state)
            }
        }

        Column(Modifier.fillMaxWidth().background(PpColor.Canvas).padding(16.dp)) {
            when {
                state.directMode && state.canEndDirect ->
                    PrimaryButton("End shift", onClick = viewModel::checkOut, loading = state.submitting, container = PpColor.Ink900, height = 62)
                state.directMode && state.blockedByActivity ->
                    OutlineButton("Go to close-out", onClick = onGoCloseOut)
                !state.directMode ->
                    PrimaryButton(
                        text = "Check out · end shift",
                        onClick = viewModel::checkOut,
                        loading = state.submitting,
                        enabled = state.session?.readyToClose == true,
                        container = PpColor.Ink900,
                        height = 62,
                    )
            }
        }
    }
}

@Composable
private fun DirectBody(state: CheckOutState) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.blockedByActivity) {
            NotePanel(
                "You've completed ${state.completedToday} deliver${if (state.completedToday == 1) "y" else "ies"} today. " +
                    "Company cash and cans must be reconciled before you can check out — please close out.",
                tone = Tone.Danger,
            )
        } else {
            NotePanel(
                "You haven't started a run — nothing was loaded out, delivered, or collected. " +
                    "You can end this check-in now.",
                tone = Tone.Info,
            )
        }
        if (state.error != null) NotePanel(state.error!!, tone = Tone.Danger)
    }
}

@Composable
private fun ReconciledBody(state: CheckOutState) {
    val s = state.session
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PpCard {
            SectionLabel("Cans")
            Spacer(Modifier.height(8.dp))
            LabeledRow("All lines balanced", if (s?.allBalanced == true) "Yes" else "No", labelColor = PpColor.Ink700)
        }
        s?.cash?.let { c ->
            PpCard {
                SectionLabel("Cash")
                Spacer(Modifier.height(8.dp))
                LabeledRow("Declared cash", money(c.declaredCash))
                Spacer(Modifier.height(6.dp))
                LabeledRow("Variance", money(c.cashVariance, signed = true))
            }
        }
        val ready = s?.readyToClose == true
        NotePanel(
            if (ready) "Cans and cash are reconciled. You're clear to check out."
            else "Check-out unlocks only after cans and cash both balance. A variance goes to store admin.",
            tone = if (ready) Tone.Success else Tone.Pending,
        )
        if (state.error != null) NotePanel(state.error!!, tone = Tone.Danger)
    }
}
