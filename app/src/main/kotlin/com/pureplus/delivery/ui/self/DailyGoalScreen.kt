package com.pureplus.delivery.ui.self

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pureplus.delivery.ui.components.BackHeader
import com.pureplus.delivery.ui.components.LinearProgress
import com.pureplus.delivery.ui.components.LoadingState
import com.pureplus.delivery.ui.components.NotePanel
import com.pureplus.delivery.ui.components.PpCard
import com.pureplus.delivery.ui.components.SectionLabel
import com.pureplus.delivery.ui.components.Tone
import com.pureplus.delivery.ui.theme.PpColor
import com.pureplus.delivery.ui.theme.PpType

/** DP-18 · Daily goal tracking — the achieved % feeds payroll, it's not just a progress bar. */
@Composable
fun DailyGoalScreen(
    onBack: () -> Unit,
    viewModel: DailyGoalViewModel = viewModel(factory = DailyGoalViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val g = state.goal

    Column(Modifier.fillMaxSize().background(PpColor.Canvas)) {
        BackHeader("Today's goal", onBack, backLabel = "Earnings")
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading -> LoadingState()
                g != null -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    PpCard(background = PpColor.Ink900, border = PpColor.Ink900) {
                        Text("${g.achievementPercent.toInt()}%", style = PpType.amountXl.copy(color = PpColor.White), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        Text("achieved", style = PpType.body.copy(color = PpColor.BrandOnDark), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        Text("${g.completedDeliveries} of ${g.dailyGoalTarget} deliveries", style = PpType.amountMd.copy(color = PpColor.White), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    }
                    PpCard {
                        SectionLabel("Progress")
                        Spacer(Modifier.height(12.dp))
                        LinearProgress((g.achievementPercent / 100.0).toFloat())
                    }
                    NotePanel("This percentage goes straight into your salary as a component — not just a progress bar. Failed attempts don't count.", tone = Tone.Info)
                }
                else -> NotePanel(state.error ?: "No goal data.", tone = Tone.Danger, modifier = Modifier.padding(16.dp))
            }
        }
    }
}
