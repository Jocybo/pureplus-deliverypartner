package com.pureplus.delivery.ui.delivery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pureplus.delivery.ui.components.BackHeader
import com.pureplus.delivery.ui.components.NotePanel
import com.pureplus.delivery.ui.components.OutlineButton
import com.pureplus.delivery.ui.components.PrimaryButton
import com.pureplus.delivery.ui.components.Tone
import com.pureplus.delivery.ui.components.clickableNoRipple
import com.pureplus.delivery.ui.theme.PpColor
import com.pureplus.delivery.ui.theme.PpType

/** DP-12 · Unable to deliver. Honest reason → FAILED (WA-05 queue). Cans stay on the vehicle. */
@Composable
fun UnableToDeliverScreen(
    orderId: Long,
    onDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: UnableToDeliverViewModel = viewModel(key = "unable_$orderId", factory = UnableToDeliverViewModel.factory(orderId)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone() }

    Column(Modifier.fillMaxSize().background(PpColor.Canvas)) {
        BackHeader(
            title = "What went wrong?",
            onBack = onBack,
            backLabel = "Stop",
            subtitle = "Pick the honest reason — it decides the follow-up.",
        )

        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            state.reasons.forEach { reason ->
                ReasonRow(reason, selected = state.selected == reason) { viewModel.select(reason) }
            }

            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::onNote,
                placeholder = { Text("Note (optional) — e.g. called twice, no answer") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                shape = RoundedCornerShape(12.dp),
            )

            NotePanel("Cans stay on your vehicle and are counted at shift end. This stop won't count toward your goal.", tone = Tone.Pending)

            if (state.error != null) NotePanel(state.error!!, tone = Tone.Danger)
        }

        Column(Modifier.fillMaxWidth().background(PpColor.Canvas).padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            PrimaryButton("Mark failed", onClick = { viewModel.submit(reschedule = false) }, loading = state.submitting, container = PpColor.Danger)
            OutlineButton("Attempt reschedule instead", onClick = { viewModel.submit(reschedule = true) })
        }
    }
}

@Composable
private fun ReasonRow(reason: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) PpColor.DangerBg else PpColor.White)
            .border(if (selected) 2.dp else 1.dp, if (selected) PpColor.Danger else PpColor.Line, RoundedCornerShape(12.dp))
            .clickableNoRipple(onClick)
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(22.dp).clip(RoundedCornerShape(999.dp))
                .border(if (selected) 6.dp else 2.dp, if (selected) PpColor.Danger else PpColor.StepperBorder, RoundedCornerShape(999.dp))
                .background(PpColor.White),
        )
        Text(reason, style = PpType.bodyStrong.copy(color = if (selected) PpColor.Danger else PpColor.Ink900))
    }
}
