package com.pureplus.delivery.ui.shift

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pureplus.delivery.ui.components.BackHeader
import com.pureplus.delivery.ui.components.CountStepper
import com.pureplus.delivery.ui.components.LoadingState
import com.pureplus.delivery.ui.components.NotePanel
import com.pureplus.delivery.ui.components.PpCard
import com.pureplus.delivery.ui.components.PrimaryButton
import com.pureplus.delivery.ui.components.Tone
import com.pureplus.delivery.ui.theme.PpColor
import com.pureplus.delivery.ui.theme.PpType

/** DP-04 · Loading confirmation. Sets the shift's reconciliation baseline. */
@Composable
fun LoadOutScreen(
    onBack: () -> Unit,
    onLoadedOut: () -> Unit,
    viewModel: LoadOutViewModel = viewModel(factory = LoadOutViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.done) { if (state.done) onLoadedOut() }

    Column(Modifier.fillMaxSize().background(PpColor.Canvas)) {
        BackHeader(
            title = "Load out",
            onBack = onBack,
            backLabel = "Today's run",
            subtitle = "This sets your shift baseline for can reconciliation.",
        )

        if (state.loading) {
            androidx.compose.foundation.layout.Box(Modifier.weight(1f).fillMaxWidth()) { LoadingState() }
            return@Column
        }

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.rows.forEach { row ->
                PpCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(row.productName, style = PpType.cardTitle)
                            Text("Required ${row.required}", style = PpType.bodySm.copy(color = PpColor.Ink500))
                        }
                        CountStepper(
                            value = row.loaded,
                            onChange = { viewModel.setLoaded(row.productId, it) },
                            valueColor = if (row.short > 0) PpColor.Danger else PpColor.Ink900,
                            modifier = Modifier.weight(1.1f),
                        )
                    }
                }
            }

            if (state.rows.isEmpty()) {
                NotePanel("No items to load for this shift yet.", tone = Tone.Neutral)
            }
            if (state.shortages.isNotEmpty()) {
                val n = state.shortages.sumOf { it.short }
                NotePanel(
                    "$n short across ${state.shortages.size} product(s). Affected stops are flagged now so you don't find out at the door — store admin is alerted before you leave.",
                    tone = Tone.Danger,
                )
            }
            if (state.error != null) NotePanel(state.error!!, tone = Tone.Danger)
        }

        Column(Modifier.fillMaxWidth().background(PpColor.Canvas).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("Confirm loaded quantities", onClick = viewModel::confirm, loading = state.submitting)
        }
        Spacer(Modifier.height(6.dp))
    }
}
