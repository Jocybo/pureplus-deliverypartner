package com.pureplus.delivery.ui.self

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pureplus.delivery.data.remote.NotificationItem
import com.pureplus.delivery.ui.components.BackHeader
import com.pureplus.delivery.ui.components.EmptyState
import com.pureplus.delivery.ui.components.LoadingState
import com.pureplus.delivery.ui.components.NotePanel
import com.pureplus.delivery.ui.components.PpCard
import com.pureplus.delivery.ui.components.Tone
import com.pureplus.delivery.ui.components.clickableNoRipple
import com.pureplus.delivery.ui.theme.PpColor
import com.pureplus.delivery.ui.theme.PpType
import com.pureplus.delivery.util.shortDate
import com.pureplus.delivery.util.timeOfDay

/** In-app notifications inbox. Pull to refresh; tap a row to mark it read; "Mark all" clears the badge. */
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    viewModel: NotificationsViewModel = viewModel(factory = NotificationsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(PpColor.Canvas)) {
        BackHeader("Notifications", onBack, backLabel = "Me")

        Row(
            Modifier.fillMaxWidth().background(PpColor.White).padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (state.unreadCount > 0) "${state.unreadCount} unread" else "Notifications",
                style = PpType.screenTitle.copy(color = PpColor.Ink900),
            )
            if (state.unreadCount > 0) {
                Text(
                    "Mark all read",
                    style = PpType.bodyStrong.copy(color = PpColor.Brand700),
                    modifier = Modifier.clickableNoRipple { viewModel.markAllRead() }.padding(6.dp),
                )
            }
        }

        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            when {
                state.loading -> LoadingState()
                state.error != null -> Column(Modifier.fillMaxSize()) {
                    NotePanel(state.error!!, tone = Tone.Danger, modifier = Modifier.padding(16.dp))
                }
                state.items.isEmpty() -> EmptyState("No notifications", "You're all caught up.")
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.items) { n -> NotificationRow(n, onClick = { viewModel.markRead(n.id) }) }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(n: NotificationItem, onClick: () -> Unit) {
    // Unread rows read a touch stronger; a brand dot marks them.
    PpCard(
        modifier = Modifier.clickableNoRipple(onClick),
        background = if (n.isRead) PpColor.White else PpColor.Brand50,
    ) {
        Row(Modifier.fillMaxWidth()) {
            Box(
                Modifier.padding(top = 6.dp, end = 10.dp).size(8.dp).clip(RoundedCornerShape(999.dp))
                    .background(if (n.isRead) PpColor.Line else PpColor.Brand500),
            )
            Column(Modifier.weight(1f)) {
                n.title?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = PpType.bodyStrong.copy(color = PpColor.Ink900))
                    Spacer(Modifier.height(2.dp))
                }
                Text(n.body, style = PpType.bodySm.copy(color = PpColor.Ink700))
                Text(
                    "${shortDate(n.createdAt)} · ${timeOfDay(n.createdAt)}",
                    style = PpType.monoSm.copy(color = PpColor.Ink500),
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}
