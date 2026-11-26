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
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
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
import com.pureplus.delivery.ui.components.CountStepper
import com.pureplus.delivery.ui.components.LoadingState
import com.pureplus.delivery.ui.components.NotePanel
import com.pureplus.delivery.ui.components.OutlineButton
import com.pureplus.delivery.ui.components.PpCard
import com.pureplus.delivery.ui.components.PrimaryButton
import com.pureplus.delivery.ui.components.Tone
import com.pureplus.delivery.ui.theme.PpColor
import com.pureplus.delivery.ui.theme.PpType

/**
 * DP-15 · Empty can return, counted against the load-out baseline.
 *
 * `openSession()` is a get-or-create — it returns the shift's reconciliation session. Three outcomes:
 *  1. Failure          → no valid session → error + Retry (can't advance).
 *  2. Success + rows    → reconcile each product → "Confirm returned quantities".
 *  3. Success + 0 rows  → NO LOAD-OUT was recorded this shift. The backend can only declare/close a
 *     session that has can lines (ReconciliationService.DeclareAsync/CloseAsync both reject an empty
 *     session), and in STORE stock mode can lines come ONLY from load-out (deliveries don't create them).
 *     So the driver CANNOT close from here — they must load out first. We route them to DP-04, not forward
 *     into a declare that is guaranteed to fail. A no-run mistaken check-in uses "End shift" from the Run tab.
 */
@Composable
fun CanReturnScreen(
    onNext: (Long) -> Unit,
    onLoadOut: () -> Unit,
    viewModel: CanReturnViewModel = viewModel(factory = CanReturnViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done && state.sessionId > 0) onNext(state.sessionId) }

    // Session opened but carries no can lines → no load-out this shift → can't reconcile or close yet.
    val noLoadOut = !state.loading && state.error == null && state.rows.isEmpty()

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

                // 1 · openSession failed — no session to work with. Only a retry can recover.
                state.error != null && state.rows.isEmpty() -> Column(
                    Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    NotePanel(state.error!!, tone = Tone.Danger)
                    OutlineButton(text = "Retry", onClick = viewModel::retry, modifier = Modifier.fillMaxWidth())
                }

                // 3 · No load-out this shift — the driver must load out before they can reconcile/close.
                noLoadOut -> Column(
                    Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    NotePanel(
                        "No load-out recorded for this shift. You must load out your cans before you can " +
                            "reconcile and close. If you checked in by mistake, go back and use “End shift”.",
                        tone = Tone.Pending,
                    )
                    OutlineButton(text = "Reload", onClick = viewModel::retry, modifier = Modifier.fillMaxWidth())
                }

                // 2 · Normal reconciliation — one card per loaded-out product.
                else -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (!state.loadOutNote.isNullOrBlank()) {
                        NotePanel(state.loadOutNote!!, tone = Tone.Pending)
                    }

                    state.rows.forEach { row ->
                        PpCard {
                            // Title + read-only context on a tight header.
                            Text(row.productName ?: "Product ${row.productId}", style = PpType.cardTitle)
                            Text(
                                "Loaded ${row.loadedOut} · Delivered ${row.delivered}",
                                style = PpType.bodySm.copy(color = PpColor.Ink500),
                                modifier = Modifier.padding(top = 2.dp),
                            )
                            Spacer(Modifier.height(8.dp))
                            StepperRow("Empties collected", row.emptiesCollected) { viewModel.setEmpties(row.productId, it) }
                            Spacer(Modifier.height(6.dp))
                            StepperRow("Full cans returning", row.fullsReturned) { viewModel.setFullsReturned(row.productId, it) }
                            if (row.variance != 0) {
                                Spacer(Modifier.height(6.dp))
                                StepperRow(
                                    "Breakage / loss",
                                    row.breakageLoss,
                                    labelColor = PpColor.Danger,
                                    valueColor = PpColor.Danger,
                                ) { viewModel.setBreakage(row.productId, it) }
                            }
                        }
                    }

                    if (state.unaccounted > 0) {
                        NotePanel(
                            "${state.unaccounted} can(s) unaccounted for. Record a reason (breakage/loss). Unexplained shrinkage blocks shift close and goes to store admin.",
                            tone = Tone.Danger,
                        )
                    }
                    if (state.error != null) NotePanel(state.error!!, tone = Tone.Danger)
                }
            }
        }

        // Bottom action — context-aware so the driver is never trapped:
        //   · loading / openSession-error  → no button (Retry lives in the body)
        //   · no load-out                  → "Go to load out" (the only way to get can lines to reconcile)
        //   · rows present                 → "Confirm returned quantities"
        Column(Modifier.fillMaxWidth().background(PpColor.Canvas).padding(16.dp)) {
            when {
                state.loading -> Unit
                state.error != null && state.rows.isEmpty() -> Unit
                noLoadOut -> PrimaryButton("Go to load out", onClick = onLoadOut)
                else -> PrimaryButton(
                    "Confirm returned quantities",
                    onClick = viewModel::submit,
                    loading = state.submitting,
                    enabled = !state.submitting,
                )
            }
        }
    }
}

/**
 * Compact editable count row: label on the left, [CountStepper] on the right, on a single line.
 * Keeps the per-product card short while leaving the steppers fully functional.
 */
@Composable
private fun StepperRow(
    label: String,
    value: Int,
    labelColor: androidx.compose.ui.graphics.Color = PpColor.Ink700,
    valueColor: androidx.compose.ui.graphics.Color = PpColor.Ink900,
    onChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = PpType.bodyStrong.copy(color = labelColor),
            modifier = Modifier.weight(1f),
        )
        CountStepper(
            value,
            onChange,
            valueColor = valueColor,
            modifier = Modifier.width(150.dp),
        )
    }
}
