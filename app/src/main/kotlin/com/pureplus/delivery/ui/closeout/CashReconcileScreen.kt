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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pureplus.delivery.ui.components.LabeledRow
import com.pureplus.delivery.ui.components.LoadingState
import com.pureplus.delivery.ui.components.NotePanel
import com.pureplus.delivery.ui.components.PpCard
import com.pureplus.delivery.ui.components.PrimaryButton
import com.pureplus.delivery.ui.components.SectionLabel
import com.pureplus.delivery.ui.components.Tone
import com.pureplus.delivery.ui.theme.PpColor
import com.pureplus.delivery.ui.theme.PpType
import com.pureplus.delivery.util.money

/** DP-16 · Shift-end cash reconciliation. Company money going back — separate from salary. */
@Composable
fun CashReconcileScreen(
    sessionId: Long,
    onNext: (Long) -> Unit,
    viewModel: CashReconcileViewModel = viewModel(key = "cash_$sessionId", factory = CashReconcileViewModel.factory(sessionId)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(PpColor.Canvas)) {
        Column(Modifier.fillMaxWidth().background(PpColor.White).padding(16.dp, 13.dp)) {
            CloseOutStepper(current = 2)
            Spacer(Modifier.height(11.dp))
            Text("Cash reconciliation", style = PpType.screenTitle)
            Text("This is company money going back — separate from your salary.", style = PpType.bodySm.copy(color = PpColor.Ink500), modifier = Modifier.padding(top = 4.dp))
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading -> LoadingState()
                else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val c = state.cash
                    PpCard {
                        SectionLabel("System expects")
                        Spacer(Modifier.height(10.dp))
                        LabeledRow("Cash collected", money(c?.expectedCash ?: 0.0))
                        Spacer(Modifier.height(6.dp))
                        LabeledRow("UPI confirmed", money(c?.expectedUpi ?: 0.0))
                        Spacer(Modifier.height(6.dp))
                        LabeledRow("Postpaid ledgered", money(c?.expectedPostpaid ?: 0.0))
                        Spacer(Modifier.height(6.dp))
                        LabeledRow("Pending verification", money(c?.expectedPending ?: 0.0))
                    }

                    PpCard(background = PpColor.Ink900, border = PpColor.Ink900) {
                        SectionLabel("Cash you are handing over", color = PpColor.BrandOnDark)
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = state.declaredCash,
                            onValueChange = viewModel::onDeclaredCash,
                            singleLine = true,
                            prefix = { Text("₹ ", style = PpType.mono.copy(color = PpColor.White)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            textStyle = PpType.amountMd.copy(color = PpColor.White),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = PpColor.Ink700,
                                unfocusedContainerColor = PpColor.Ink700,
                                focusedTextColor = PpColor.White,
                                unfocusedTextColor = PpColor.White,
                                cursorColor = PpColor.Brand500,
                                focusedIndicatorColor = PpColor.Brand500,
                                unfocusedIndicatorColor = PpColor.Ink700,
                            ),
                        )
                    }

                    // Live variance = expected − declared
                    val declared = state.declaredCash.toDoubleOrNull()
                    if (declared != null && c != null) {
                        val variance = c.expectedCash - declared
                        if (variance != 0.0) {
                            NotePanel("Variance ${money(variance, signed = true)} — raised as a dispute for store admin, never written off.", tone = Tone.Danger)
                        }
                    }
                    // ShiftV2: once declared, the handover is locked for the driver and awaits the store.
                    if (state.declared) {
                        NotePanel(
                            "Handover declared. Waiting for the store to verify your cash and cans — you can't edit this now. " +
                                "Clock out on the next step once the store confirms.",
                            tone = Tone.Pending,
                        )
                    }
                    if (state.error != null) NotePanel(state.error!!, tone = Tone.Danger)
                }
            }
        }

        Column(Modifier.fillMaxWidth().background(PpColor.Canvas).padding(16.dp)) {
            val label = when {
                state.declared -> "Awaiting store verification"
                state.shiftV2 -> "Declare handover"
                else -> "Submit for admin confirmation"
            }
            PrimaryButton(
                label,
                onClick = { viewModel.submit { onNext(sessionId) } },
                loading = state.submitting,
                enabled = !state.declared,
            )
        }
    }
}
