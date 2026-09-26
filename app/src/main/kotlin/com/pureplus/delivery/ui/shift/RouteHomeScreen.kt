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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pureplus.delivery.data.remote.Stop
import com.pureplus.delivery.ui.components.BottomNavBar
import com.pureplus.delivery.ui.components.DarkHeader
import com.pureplus.delivery.ui.components.LinearProgress
import com.pureplus.delivery.ui.components.LogoutIconButton
import com.pureplus.delivery.ui.components.NavTab
import com.pureplus.delivery.ui.components.OutlineButton
import com.pureplus.delivery.ui.components.PpCard
import com.pureplus.delivery.ui.components.PrimaryButton
import com.pureplus.delivery.ui.components.SectionLabel
import com.pureplus.delivery.ui.components.StatTile
import com.pureplus.delivery.ui.components.TextLink
import com.pureplus.delivery.ui.theme.PpColor
import com.pureplus.delivery.ui.theme.PpType
import com.pureplus.delivery.util.money

/** DP-03 · Today's route/home. The driver's single operational view for the shift. */
@Composable
fun RouteHomeScreen(
    onTab: (NavTab) -> Unit,
    onStartLoading: () -> Unit,
    onOpenStops: () -> Unit,
    onOpenStop: (Long) -> Unit,
    onCloseOut: () -> Unit,
    onEndShift: () -> Unit,
    onLogout: () -> Unit,
    viewModel: RouteHomeViewModel = viewModel(factory = RouteHomeViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(PpColor.Canvas)) {
        DarkHeader(
            title = "Today's run",
            eyebrow = "Your route · this shift",
            trailing = { LogoutIconButton(onClick = onLogout) },
        )

        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    value = "${state.stopsDone}/${state.stopsTotal}",
                    caption = "Stops done",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = "${state.summary?.cansToLoad ?: 0}",
                    caption = "Cans to load",
                    modifier = Modifier.weight(1f),
                )
            }

            state.goal?.let { goal ->
                PpCard {
                    SectionLabel("Daily goal")
                    Spacer(Modifier.height(12.dp))
                    LinearProgress((goal.achievementPercent / 100.0).toFloat())
                    Spacer(Modifier.height(9.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${goal.completedDeliveries} of ${goal.dailyGoalTarget} deliveries", style = PpType.bodyStrong.copy(color = PpColor.Ink700))
                        Text("${goal.achievementPercent.toInt()}%", style = PpType.mono.copy(color = PpColor.Ink700))
                    }
                }
            }

            state.nextStop?.let { NextStopCard(it, onOpenStop) }

            if (state.error != null) {
                Text(state.error!!, style = PpType.bodySm.copy(color = PpColor.Danger))
            }
        }
        }

        Column(Modifier.fillMaxWidth().background(PpColor.Canvas).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("Load out & start run", onClick = onStartLoading)
            PrimaryButton("Open stop list", onClick = onOpenStops, container = PpColor.Ink900)
            // End-of-shift: cans → cash → check-out (DP-15/16/22). Gated on both loops balancing.
            OutlineButton("Close out & check out", onClick = onCloseOut)
            // Escape hatch for a mistaken check-in with no run (attendance-only check-out).
            TextLink("Checked in by mistake? End shift", onClick = onEndShift, color = PpColor.Ink500)
        }

        BottomNavBar(current = NavTab.Run, onSelect = onTab)
    }
}

@Composable
private fun NextStopCard(stop: Stop, onOpen: (Long) -> Unit) {
    PpCard(background = PpColor.Ink900, border = PpColor.Ink900) {
        SectionLabel("Next stop", color = PpColor.BrandOnDark)
        Spacer(Modifier.height(8.dp))
        Text(stop.receiverName ?: "Stop #${stop.orderId}", style = PpType.cardTitle.copy(color = PpColor.White))
        Text(
            listOfNotNull(stop.addressLine1, stop.addressLine2).joinToString(" · ").ifBlank { stop.city ?: "" },
            style = PpType.body.copy(color = androidx.compose.ui.graphics.Color(0xFF94AFC2)),
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(money(stop.totalAmount), style = PpType.amountMd.copy(color = PpColor.White))
            Text("Open →", style = PpType.bodyStrong.copy(color = PpColor.Brand500), modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(10.dp))
        PrimaryButton("Open this stop", onClick = { onOpen(stop.orderId.toLong()) }, container = PpColor.Brand500, content = PpColor.Ink900, height = 48)
    }
}
