package com.pureplus.delivery.ui.bulk

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pureplus.delivery.ui.components.BackHeader
import com.pureplus.delivery.ui.components.NotePanel
import com.pureplus.delivery.ui.components.PrimaryButton
import com.pureplus.delivery.ui.components.Tone
import com.pureplus.delivery.ui.theme.PpColor

/** DP-14 · Bulk / event delivery. Dedicated trip outside normal route batching. */
@Composable
fun BulkScreen(
    bulkRequestId: Long,
    onDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: BulkViewModel = viewModel(key = "bulk_$bulkRequestId", factory = BulkViewModel.factory(bulkRequestId)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone() }

    Column(Modifier.fillMaxSize().background(PpColor.Canvas)) {
        BackHeader("Bulk / event delivery", onBack, backLabel = "Stops", subtitle = "Dedicated bulk trip · consolidated deposit")

        Column(Modifier.weight(1f).fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            NotePanel("Bulk request #$bulkRequestId. Confirming fulfils the batch: consolidated deposit, B2B ledgering, and a scheduled empty-can pickup.", tone = Tone.Info)
            NotePanel("Full bulk detail (approved quantity, venue, balance to collect) will show here once the delivery API exposes the bulk-request detail.", tone = Tone.Neutral)
            if (state.error != null) NotePanel(state.error!!, tone = Tone.Danger)
        }

        Column(Modifier.fillMaxWidth().background(PpColor.Canvas).padding(16.dp)) {
            PrimaryButton("Confirm bulk delivery", onClick = viewModel::confirm, loading = state.submitting)
        }
        Spacer(Modifier.height(4.dp))
    }
}
