package com.pureplus.delivery.ui.delivery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pureplus.delivery.ui.components.BackHeader
import com.pureplus.delivery.ui.components.CountStepper
import com.pureplus.delivery.ui.components.LoadingState
import com.pureplus.delivery.ui.components.NotePanel
import com.pureplus.delivery.ui.components.PpCard
import com.pureplus.delivery.ui.components.PrimaryButton
import com.pureplus.delivery.ui.components.Receipt
import com.pureplus.delivery.ui.components.ReceiptLine
import com.pureplus.delivery.ui.components.LineKind
import com.pureplus.delivery.ui.components.SectionLabel
import com.pureplus.delivery.ui.components.TextLink
import com.pureplus.delivery.ui.components.Tone
import com.pureplus.delivery.ui.components.clickableNoRipple
import com.pureplus.delivery.ui.theme.PpColor
import com.pureplus.delivery.ui.theme.PpType
import com.pureplus.delivery.util.money

/** DP-08 · Delivery execution → DP-09 payment → DP-11 complete (DP-10 offline = queued Done). */
@Composable
fun DeliveryScreen(
    orderId: Long,
    onDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: DeliveryViewModel = viewModel(key = "deliver_$orderId", factory = DeliveryViewModel.factory(orderId)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(orderId) { viewModel.load() }

    Column(Modifier.fillMaxSize().background(PpColor.Canvas)) {
        when {
            state.loading -> {
                BackHeader("At the door", onBack, backLabel = "Stop")
                Box(Modifier.weight(1f).fillMaxWidth()) { LoadingState() }
            }
            state.step == DeliveryStep.Done -> DoneStep(state, onDone)
            state.step == DeliveryStep.Payment -> PaymentStep(viewModel, state, onBack)
            else -> ExecutionStep(viewModel, state, onBack)
        }
    }
}

@Composable
private fun ColumnScope.ExecutionStep(vm: DeliveryViewModel, state: DeliveryState, onBack: () -> Unit) {
    BackHeader(
        title = "At the door · ${state.stop?.receiverName ?: ""}",
        onBack = onBack,
        backLabel = "Stop",
        subtitle = "Record what actually happened. The final amount is confirmed on the next step.",
    )
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        state.lines.forEach { line ->
            PpCard {
                SectionLabel("${if (line.reusable) "Cans delivered" else "Delivered"} · ${line.name}")
                Spacer(Modifier.height(9.dp))
                CountStepper(line.deliveredQty, { vm.setDelivered(line.productId, it) })
                if (line.reusable) {
                    Spacer(Modifier.height(12.dp))
                    SectionLabel("Empties collected back", color = PpColor.Pending)
                    Spacer(Modifier.height(9.dp))
                    CountStepper(line.emptiesCollected, { vm.setEmpties(line.productId, it) }, valueColor = PpColor.Pending)
                    if (line.shortfall > 0) {
                        Spacer(Modifier.height(8.dp))
                        NotePanel("${line.shortfall} short of ${line.deliveredQty} → a fresh deposit is added at confirm. Tell the customer now.", tone = Tone.Pending)
                    }
                } else {
                    Spacer(Modifier.height(6.dp))
                    Text("Disposable · no empty expected", style = PpType.bodySm.copy(color = PpColor.Ink500))
                }
            }
        }

        PpCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SectionLabel("Actual floor")
                Text("entered: ${state.stop?.floorNo?.let { "${it}F" } ?: "—"}", style = PpType.bodySm.copy(color = PpColor.Ink500))
            }
            Spacer(Modifier.height(10.dp))
            FloorSelector(state.actualFloor) { vm.setFloor(it) }
            Spacer(Modifier.height(10.dp))
            LiftToggle(state.actualHasLift) { vm.setLift(it) }
        }

        PpCard(background = PpColor.Canvas, border = PpColor.Line) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Products (est.)", style = PpType.body.copy(color = PpColor.Ink700))
                Text(money(state.productsSubtotal), style = PpType.mono)
            }
            Spacer(Modifier.height(4.dp))
            Text("Deposits, floor charge and any wallet credit are added when you confirm.", style = PpType.bodySm.copy(color = PpColor.Ink500))
        }

        if (state.error != null) NotePanel(state.error!!, tone = Tone.Danger)
    }
    Column(Modifier.fillMaxWidth().background(PpColor.Canvas).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PrimaryButton(if (state.isPostpaid) "Proceed · ledger to account" else "Proceed to payment", onClick = vm::toPayment)
        TextLink("Customer disputes a charge? Flag for review", onClick = vm::flagDispute, color = PpColor.Danger)
    }
}

@Composable
private fun ColumnScope.PaymentStep(vm: DeliveryViewModel, state: DeliveryState, onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(PpColor.Ink900).padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        SectionLabel(if (state.isPostpaid) "Ledger to account" else "Collect from ${state.stop?.receiverName ?: "customer"}", color = PpColor.BrandOnDark)
        Spacer(Modifier.height(8.dp))
        Text(money(state.productsSubtotal), style = PpType.amountXl.copy(color = PpColor.White))
        Text("estimate · final on confirm", style = PpType.bodySm.copy(color = PpColor.BrandOnDark))
    }
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        if (state.isPostpaid) {
            NotePanel("Subscription / B2B stop — auto-confirms as LEDGERED. No cash is collected.", tone = Tone.Postpaid)
        } else {
            SectionLabel("Payment method")
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                PayOption("Cash", state.paymentMode == "CASH", Modifier.weight(1f)) { vm.setPaymentMode("CASH") }
                PayOption("UPI", state.paymentMode == "UPI", Modifier.weight(1f)) { vm.setPaymentMode("UPI") }
            }
            NotePanel("UPI uses the company QR generated by the system for this order. Never use a personal QR.", tone = Tone.Info)
        }
        if (state.error != null) NotePanel(state.error!!, tone = Tone.Danger)
        TextLink("Back — change what was delivered", onClick = vm::backToExecution)
    }
    Column(Modifier.fillMaxWidth().background(PpColor.Canvas).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val label = when {
            state.isPostpaid -> "Confirm · ledger to account"
            state.paymentMode == "UPI" -> "UPI received · confirm"
            else -> "Cash received · confirm"
        }
        PrimaryButton(label, onClick = vm::confirm, loading = state.submitting, container = if (state.isPostpaid) PpColor.Postpaid else PpColor.Success, height = 62)
        Text("Payment must be confirmed before the stop can close.", style = PpType.bodySm.copy(color = PpColor.Ink500), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ColumnScope.DoneStep(state: DeliveryState, onDone: () -> Unit) {
    val offline = state.queuedOffline
    Column(Modifier.fillMaxWidth().background(if (offline) PpColor.Pending else PpColor.Success).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (offline) "Delivered — payment pending" else "Stop complete", style = PpType.screenTitle.copy(color = PpColor.White))
        val amt = state.result?.amountPayable ?: state.productsSubtotal
        Text(
            if (offline) "Saved on this phone. It will sync automatically when signal returns."
            else "${state.stop?.receiverName ?: ""} · ${money(amt)} ${if (state.isPostpaid) "ledgered" else "collected"}",
            style = PpType.body.copy(color = PpColor.White), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp),
        )
    }
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        state.result?.let { r ->
            val rows = buildList {
                add(ReceiptLine("Products", r.orderAmount))
                if (r.extraDeposit > 0) add(ReceiptLine("New can deposit", r.extraDeposit, LineKind.Addition))
                if (r.actualFloorCharge > 0) add(ReceiptLine("Floor charge · ${r.actualFloor}F", r.actualFloorCharge, LineKind.Addition))
                if (r.walletCredit > 0) add(ReceiptLine("Wallet credit issued", -r.walletCredit, LineKind.Credit))
            }
            Receipt(rows, if (state.isPostpaid) "Ledgered" else "Collected", r.amountPayable)
        }
        if (offline) {
            NotePanel("Order marked DELIVERED — PAYMENT PENDING and queued. Your store admin matches it against your cash at shift end.", tone = Tone.Pending)
        }
    }
    Column(Modifier.fillMaxWidth().background(PpColor.Canvas).padding(16.dp)) {
        PrimaryButton("Continue to next stop", onClick = onDone)
    }
}

@Composable
private fun FloorSelector(selected: Int, onSelect: (Int) -> Unit) {
    val options = listOf(0, 1, 2, 3, 4, 5)
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        options.forEach { f ->
            val active = f == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (active) PpColor.Brand50 else PpColor.White)
                    .border(if (active) 2.dp else 1.dp, if (active) PpColor.Brand700 else PpColor.Line, RoundedCornerShape(9.dp))
                    .clickableNoRipple { onSelect(f) }
                    .padding(horizontal = 13.dp, vertical = 10.dp),
            ) {
                Text(if (f == 0) "G" else if (f == 5) "5+" else "$f", style = PpType.mono.copy(color = if (active) PpColor.Brand700 else PpColor.NeutralChip))
            }
        }
    }
}

@Composable
private fun LiftToggle(hasLift: Boolean, onChange: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        PayOption("Lift working", hasLift, Modifier.weight(1f)) { onChange(true) }
        PayOption("No lift", !hasLift, Modifier.weight(1f)) { onChange(false) }
    }
}

@Composable
private fun PayOption(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) PpColor.SuccessBg else PpColor.White)
            .border(if (selected) 2.dp else 1.dp, if (selected) PpColor.Success else PpColor.Line, RoundedCornerShape(12.dp))
            .clickableNoRipple(onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = PpType.bodyStrong.copy(color = if (selected) PpColor.Success else PpColor.Ink700))
    }
}
