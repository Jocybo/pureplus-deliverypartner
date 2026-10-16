package com.pureplus.delivery.ui.bulk

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pureplus.delivery.data.remote.BulkJob
import com.pureplus.delivery.ui.components.BackHeader
import com.pureplus.delivery.ui.components.EmptyState
import com.pureplus.delivery.ui.components.LoadingState
import com.pureplus.delivery.ui.components.NotePanel
import com.pureplus.delivery.ui.components.PpCard
import com.pureplus.delivery.ui.components.PrimaryButton
import com.pureplus.delivery.ui.components.SectionLabel
import com.pureplus.delivery.ui.components.StatusChip
import com.pureplus.delivery.ui.components.Tag
import com.pureplus.delivery.ui.components.Tone
import com.pureplus.delivery.ui.theme.PpColor
import com.pureplus.delivery.ui.theme.PpType
import com.pureplus.delivery.util.money
import com.pureplus.delivery.util.shortDate

/**
 * DP-14 · Bulk / event delivery. Shows the driver's bulk jobs that are out for delivery; confirming one
 * fulfils the batch (consolidated deposit · B2B ledgering · scheduled empty-can pickup).
 */
@Composable
fun BulkScreen(
    onBack: () -> Unit,
    viewModel: BulkViewModel = viewModel(factory = BulkViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(PpColor.Canvas)) {
        BackHeader("Bulk / event jobs", onBack, backLabel = "Me", subtitle = "Dedicated trips · consolidated deposit")

        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            when {
                state.loading -> LoadingState()
                state.jobs.isEmpty() -> EmptyState(
                    "No bulk jobs",
                    state.error ?: "You have no bulk or event deliveries out for delivery right now.",
                )
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.lastDeliveredId != null) {
                        item { NotePanel("Bulk request #${state.lastDeliveredId} fulfilled.", tone = Tone.Success) }
                    }
                    items(state.jobs, key = { it.id }) { job ->
                        BulkJobCard(
                            job = job,
                            confirming = state.confirmingId == job.id,
                            onConfirm = { viewModel.confirm(job.id) },
                        )
                    }
                    if (state.error != null) {
                        item { NotePanel(state.error!!, tone = Tone.Danger) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BulkJobCard(job: BulkJob, confirming: Boolean, onConfirm: () -> Unit) {
    val isEvent = job.requestType.equals("EVENT", true)
    PpCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("Request #${job.id}", style = PpType.cardTitle)
                Text(
                    if (isEvent) "Event delivery" else "B2B / bulk",
                    style = PpType.bodySm.copy(color = PpColor.Ink500),
                )
            }
            StatusChip(job.status.ifBlank { "OUT_FOR_DELIVERY" }, Tone.Postpaid)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Tag("${job.counterQuantity ?: job.quantity} cans")
            job.scheduledDeliveryDate?.takeIf { it.isNotBlank() }?.let { Tag(shortDate(it)) }
            if (!job.gstin.isNullOrBlank()) Tag("GST")
        }
        job.estimatedAmount?.let {
            Spacer(Modifier.height(10.dp))
            SectionLabel("Estimated amount")
            Text(money(it), style = PpType.amountMd)
        }
        job.eventDetails?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = PpType.bodySm.copy(color = PpColor.Ink700))
        }
        Spacer(Modifier.height(12.dp))
        NotePanel(
            "Confirming fulfils the batch: consolidated deposit, " +
                (if (isEvent) "a scheduled empty-can pickup." else "B2B postpaid ledgering."),
            tone = Tone.Info,
        )
        Spacer(Modifier.height(10.dp))
        PrimaryButton("Confirm bulk delivery", onClick = onConfirm, loading = confirming)
    }
}
