package com.pureplus.delivery.ui.self

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pureplus.delivery.data.remote.CheckInDto
import com.pureplus.delivery.ui.components.BackHeader
import com.pureplus.delivery.ui.components.EmptyState
import com.pureplus.delivery.ui.components.LoadingState
import com.pureplus.delivery.ui.components.NotePanel
import com.pureplus.delivery.ui.components.PpCard
import com.pureplus.delivery.ui.components.StatusChip
import com.pureplus.delivery.ui.components.Tone
import com.pureplus.delivery.ui.components.clickableNoRipple
import com.pureplus.delivery.ui.theme.PpColor
import com.pureplus.delivery.ui.theme.PpType
import com.pureplus.delivery.util.shortDate
import com.pureplus.delivery.util.timeOfDay

/** DP-19 · Attendance history. Read-only; corrections are made by the store admin. */
@Composable
fun AttendanceScreen(
    onBack: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    viewModel: AttendanceViewModel = viewModel(factory = AttendanceViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(PpColor.Canvas)) {
        BackHeader("Attendance", onBack, backLabel = "Me")
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading -> LoadingState()
                state.error != null -> NotePanel(state.error!!, tone = Tone.Danger, modifier = Modifier.padding(16.dp))
                state.days.isEmpty() -> EmptyState("No attendance yet", "Your check-ins will appear here.")
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.days) { day -> AttendanceRow(day, onOpen = { onOpenDetail(day.id.toLong()) }) }
                    item { NotePanel("Read-only. Wrong entry? Your store admin corrects it — every correction is logged.", tone = Tone.Neutral) }
                }
            }
        }
    }
}

@Composable
private fun AttendanceRow(day: CheckInDto, onOpen: () -> Unit) {
    PpCard(modifier = Modifier.clickableNoRipple(onOpen)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(shortDate(day.shiftDate), style = PpType.cardTitle)
                val times = "${timeOfDay(day.checkedInAt)} → ${day.checkedOutAt?.let { timeOfDay(it) } ?: "—"}"
                Text(times, style = PpType.monoSm.copy(color = PpColor.Ink500), modifier = Modifier.padding(top = 3.dp))
            }
            val (label, tone) = when {
                day.isOverride -> "Override" to Tone.Neutral
                day.status.equals("late", true) -> "Late" to Tone.Pending
                else -> "On time" to Tone.Success
            }
            StatusChip(label, tone)
        }
    }
}
