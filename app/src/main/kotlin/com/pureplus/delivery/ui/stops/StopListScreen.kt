package com.pureplus.delivery.ui.stops

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.pureplus.delivery.ui.components.EmptyState
import com.pureplus.delivery.ui.components.FilterChip
import com.pureplus.delivery.ui.components.LoadingState
import com.pureplus.delivery.ui.components.NavTab
import com.pureplus.delivery.ui.components.NotePanel
import com.pureplus.delivery.ui.components.OutlineButton
import com.pureplus.delivery.ui.components.Tone
import com.pureplus.delivery.ui.theme.PpColor
import com.pureplus.delivery.ui.theme.PpType

/** DP-05 · Stop list / route sequence. */
@Composable
fun StopListScreen(
    onTab: (NavTab) -> Unit,
    onOpenStop: (Long) -> Unit,
    onCloseOut: () -> Unit,
    viewModel: StopListViewModel = viewModel(factory = StopListViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(PpColor.Canvas)) {
        Column(Modifier.fillMaxWidth().background(PpColor.White).padding(16.dp, 13.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Stops", style = PpType.screenTitle)
                Text("${state.doneCount} / ${state.all.size} done", style = PpType.mono.copy(color = PpColor.Ink500))
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                StopFilter.entries.forEach { f ->
                    FilterChip(f.label, selected = state.filter == f) { viewModel.setFilter(f) }
                }
            }
        }

        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            when {
                state.loading -> LoadingState()
                // Error/empty are wrapped in a scroll container so the pull-to-refresh gesture still works.
                state.error != null -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    NotePanel(state.error!!, tone = Tone.Danger, modifier = Modifier.padding(16.dp))
                }
                state.visible.isEmpty() -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    EmptyState("No stops here", "Nothing matches this filter right now.")
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(state.visible) { i, stop ->
                        StopCard(stop = stop, index = i + 1, onOpen = { onOpenStop(stop.orderId.toLong()) })
                    }
                    item {
                        OutlineButton(
                            "Close out shift",
                            onClick = onCloseOut,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }

        BottomNavBar(current = NavTab.Stops, onSelect = onTab)
    }
}
