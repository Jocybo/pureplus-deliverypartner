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
import com.pureplus.delivery.ui.components.TextLink
import com.pureplus.delivery.ui.components.Tone
import com.pureplus.delivery.ui.theme.PpColor
import com.pureplus.delivery.ui.theme.PpType
import com.pureplus.delivery.util.money

/**
 * DP-22 · Shift check-out. Two-step on ShiftV2: **End shift** (→ awaiting reconciliation) → reconcile +
 * declare (DP-15/16) → **Clock out** (hard-gated by the store's verification). While awaiting the store,
 * the driver may **Resume** to undo an end done by mistake. The terminal "shift completed" banner shows
 * only once the backend reports CLOSED / AUTO_CLOSED / FORCE_CLOSED. Legacy stores keep the old
 * both-loops-balance close; a mistaken no-run check-in gets an attendance-only end.
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
            if (!state.directMode && state.phase != CheckOutPhase.Completed) {
                CloseOutStepper(current = 3)
                Spacer(Modifier.height(11.dp))
            }
            val title = when (state.phase) {
                CheckOutPhase.PendingRecon -> "Clock out"
                CheckOutPhase.Completed -> "Shift complete"
                else -> if (state.directMode) "End shift" else "Shift summary"
            }
            Text(title, style = PpType.screenTitle.copy(color = PpColor.White))
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading -> LoadingState()
                // Load failed before any phase resolved — show the error cleanly (the Retry button is below).
                state.phase == CheckOutPhase.Loading && state.error != null -> Column(
                    Modifier.fillMaxSize().padding(16.dp),
                ) { NotePanel(state.error!!, tone = Tone.Danger) }
                state.phase == CheckOutPhase.Completed -> CompletedBody(state)
                state.phase == CheckOutPhase.PendingRecon -> PendingReconBody(state)
                state.directMode -> DirectBody(state)
                else -> ReconciledBody(state)
            }
        }

        Column(Modifier.fillMaxWidth().background(PpColor.Canvas).padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            when (state.phase) {
                CheckOutPhase.Completed -> PrimaryButton("Done", onClick = onCheckedOut, container = PpColor.Ink900, height = 62)

                CheckOutPhase.PendingRecon -> {
                    // Clock out is the terminal action; the backend owns the "verified yet?" gate.
                    PrimaryButton(
                        text = "Clock out",
                        onClick = viewModel::checkOut,
                        loading = state.submitting,
                        container = PpColor.Ink900,
                        height = 62,
                    )
                    // Undo an end done by mistake.
                    TextLink("Ended by mistake? Resume shift", onClick = viewModel::resume, color = PpColor.Ink500)
                }

                CheckOutPhase.EndShift -> when {
                    // Clean mistaken check-in (nothing delivered, nothing assigned) → terminal clock-out in
                    // ONE tap (business rule V5: nothing to reconcile → frictionless). checkOut() in direct
                    // mode calls clockOut() straight away; no PENDING_RECON detour.
                    state.directMode && state.canEndDirect ->
                        PrimaryButton("End shift & clock out", onClick = viewModel::checkOut, loading = state.submitting, container = PpColor.Ink900, height = 62)
                    state.directMode && state.blockedByActivity ->
                        OutlineButton("Go to close-out", onClick = onGoCloseOut)
                    // Orders still assigned but nothing delivered — can't clock out (X3). Only a re-check helps
                    // (after the store reassigns them, M2); no attendance-only escape while holding orders.
                    state.directMode && state.blockedByAssigned ->
                        OutlineButton("Refresh", onClick = viewModel::load)
                    // Real shift (ShiftV2 ACTIVE) → end duty first (ACTIVE → PENDING_RECON), then reconcile + clock out.
                    else ->
                        PrimaryButton("End shift", onClick = viewModel::endShift, loading = state.ending, container = PpColor.Ink900, height = 62)
                }

                CheckOutPhase.LegacyClose ->
                    PrimaryButton(
                        text = "Check out · end shift",
                        onClick = viewModel::checkOut,
                        loading = state.submitting,
                        enabled = state.session?.readyToClose == true,
                        container = PpColor.Ink900,
                        height = 62,
                    )

                // Load failed (phase never advanced past Loading) — offer a retry instead of a dead-end.
                CheckOutPhase.Loading ->
                    if (state.error != null) OutlineButton("Retry", onClick = viewModel::load)
            }
        }
    }
}

@Composable
private fun CompletedBody(state: CheckOutState) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NotePanel(
            "Your shift is closed for the day. Cash and cans are settled; the store has verified your handover. See you next shift.",
            tone = Tone.Success,
        )
        if (state.error != null) NotePanel(state.error!!, tone = Tone.Danger)
    }
}

@Composable
private fun PendingReconBody(state: CheckOutState) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NotePanel(
            "Duty ended — your cash and cans are declared and waiting for the store to verify the handover. " +
                "You can clock out once the store confirms (and no orders are still out for delivery).",
            tone = Tone.Pending,
        )
        state.session?.cash?.let { c ->
            PpCard {
                SectionLabel("Cash")
                Spacer(Modifier.height(8.dp))
                LabeledRow("Declared cash", money(c.declaredCash))
                Spacer(Modifier.height(6.dp))
                LabeledRow("Variance", money(c.cashVariance, signed = true))
            }
        }
        // Any backend clock-out rejection shows here verbatim (e.g. "store hasn't verified yet…").
        if (state.error != null) NotePanel(state.error!!, tone = Tone.Danger)
    }
}

@Composable
private fun DirectBody(state: CheckOutState) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            state.blockedByActivity -> NotePanel(
                "You've completed ${state.completedToday} deliver${if (state.completedToday == 1) "y" else "ies"} today. " +
                    "Company cash and cans must be reconciled before you can check out — please close out.",
                tone = Tone.Danger,
            )
            // Nothing delivered, but orders are still assigned to you → can't clock out while holding orders.
            state.blockedByAssigned -> NotePanel(
                "You still have ${state.pendingStops} order${if (state.pendingStops == 1) "" else "s"} assigned to you. " +
                    "You can't end your shift while holding orders — deliver them, or ask the store to reassign them, " +
                    "then refresh.",
                tone = Tone.Pending,
            )
            else -> NotePanel(
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
