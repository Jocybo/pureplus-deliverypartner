package com.pureplus.delivery.ui.self

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pureplus.delivery.ui.components.BottomNavBar
import com.pureplus.delivery.ui.components.DarkHeader
import com.pureplus.delivery.ui.components.LabeledRow
import com.pureplus.delivery.ui.components.LoadingState
import com.pureplus.delivery.ui.components.NavTab
import com.pureplus.delivery.ui.components.NotePanel
import com.pureplus.delivery.ui.components.PpCard
import com.pureplus.delivery.ui.components.SectionLabel
import com.pureplus.delivery.ui.components.TextLink
import com.pureplus.delivery.ui.components.Tone
import com.pureplus.delivery.ui.theme.PpColor
import com.pureplus.delivery.ui.theme.PpType
import com.pureplus.delivery.util.money

/** DP-17 · Earnings & incentives. The retention lever — base + per-delivery bonus + goal component. */
@Composable
fun EarningsScreen(
    onTab: (NavTab) -> Unit,
    onOpenGoal: () -> Unit,
    viewModel: EarningsViewModel = viewModel(factory = EarningsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val e = state.earnings

    Column(Modifier.fillMaxSize().background(PpColor.Canvas)) {
        DarkHeader(
            title = money(e?.totalEarnings ?: 0.0),
            eyebrow = "Month to date",
            subtitle = e?.let { "${it.completedDeliveries} deliveries · ${it.periodDays} days" },
        )
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            when {
                state.loading -> LoadingState()
                // Error is wrapped in a scroll container so the pull-to-refresh gesture still works.
                state.error != null -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    NotePanel(state.error!!, tone = Tone.Danger, modifier = Modifier.padding(16.dp))
                }
                e != null -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PpCard {
                        SectionLabel("How it adds up")
                        Spacer(Modifier.height(12.dp))
                        LabeledRow("Base salary", money(e.baseSalary))
                        Spacer(Modifier.height(8.dp))
                        LabeledRow("Per-delivery bonus", money(e.perDeliveryBonusTotal))
                        Text("${e.completedDeliveries} completed deliveries · failed attempts excluded", style = PpType.bodySm.copy(color = PpColor.Ink500), modifier = Modifier.padding(top = 4.dp))
                        Spacer(Modifier.height(8.dp))
                        LabeledRow("Goal achievement", money(e.goalComponent))
                        Text("Average ${e.achievementPercent.toInt()}% of target", style = PpType.bodySm.copy(color = PpColor.Ink500), modifier = Modifier.padding(top = 4.dp))
                    }
                    TextLink("View today's goal", onOpenGoal)
                    NotePanel("Cash you hand over at shift end is company money — it never appears here.", tone = Tone.Neutral)
                }
            }
        }
        BottomNavBar(current = NavTab.Earnings, onSelect = onTab)
    }
}
