package com.pureplus.delivery.ui.self

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pureplus.delivery.data.remote.CheckInDto
import com.pureplus.delivery.data.remote.Geofence
import com.pureplus.delivery.ui.components.BackHeader
import com.pureplus.delivery.ui.components.LabeledRow
import com.pureplus.delivery.ui.components.LoadingState
import com.pureplus.delivery.ui.components.NotePanel
import com.pureplus.delivery.ui.components.PpCard
import com.pureplus.delivery.ui.components.SectionLabel
import com.pureplus.delivery.ui.components.StatusChip
import com.pureplus.delivery.ui.components.Tone
import com.pureplus.delivery.ui.shift.GeofenceMap
import com.pureplus.delivery.ui.theme.PpColor
import com.pureplus.delivery.ui.theme.PpType
import com.pureplus.delivery.util.shortDate
import com.pureplus.delivery.util.timeOfDay

/** DP-19 · One attendance record in full, with a map of where the driver checked in vs. the store fence. */
@Composable
fun AttendanceDetailScreen(
    checkInId: Long,
    onBack: () -> Unit,
    viewModel: AttendanceDetailViewModel = viewModel(
        key = "attendance_$checkInId",
        factory = AttendanceDetailViewModel.factory(checkInId),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(PpColor.Canvas)) {
        BackHeader(state.record?.let { shortDate(it.shiftDate) } ?: "Attendance", onBack, backLabel = "Attendance")

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading -> LoadingState()
                state.error != null -> NotePanel(state.error!!, tone = Tone.Danger, modifier = Modifier.padding(16.dp))
                state.record != null -> AttendanceDetailBody(state.record!!, state.geofence)
            }
        }
    }
}

@Composable
private fun AttendanceDetailBody(record: CheckInDto, geofence: Geofence?) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Status header — date + the check-in outcome.
        val (statusLabel, statusTone) = when {
            record.isOverride -> "Override" to Tone.Pending
            record.withinFence -> "On time · inside fence" to Tone.Success
            else -> record.status to Tone.Neutral
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(shortDate(record.shiftDate), style = PpType.cardTitle)
                Text("Shift ${record.shiftDate}", style = PpType.bodySm.copy(color = PpColor.Ink500))
            }
            StatusChip(statusLabel, statusTone)
        }

        // Map: the store fence (centre + config radius from the geofence API) with a small store pin at the
        // centre and a larger driver pin where they actually checked in.
        val hasFence = geofence?.latitude != null && geofence.longitude != null
        val hasFix = record.latitude != null && record.longitude != null
        if (hasFence) {
            GeofenceMap(
                storeLat = geofence!!.latitude!!,
                storeLng = geofence.longitude!!,
                radiusMeters = geofence.radiusMeters,
                userLat = record.latitude,
                userLng = record.longitude,
                inside = if (hasFix) record.withinFence else null,
                storeName = geofence.storeName,
                showStoreMarker = true,
                modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp)),
            )
        }

        PpCard {
            SectionLabel("Timing")
            Spacer(Modifier.height(10.dp))
            LabeledRow("Checked in", timeOfDay(record.checkedInAt))
            Spacer(Modifier.height(8.dp))
            LabeledRow("Checked out", record.checkedOutAt?.let { timeOfDay(it) } ?: "—")
        }

        PpCard {
            SectionLabel("Location")
            Spacer(Modifier.height(10.dp))
            LabeledRow("Within fence", if (record.withinFence) "Yes" else "No", labelColor = PpColor.Ink700)
            Spacer(Modifier.height(8.dp))
            record.distanceMeters?.let {
                LabeledRow("Distance from store", "${it.toInt()} m")
                Spacer(Modifier.height(8.dp))
            }
            val radius = if (geofence != null && geofence.radiusMeters > 0) geofence.radiusMeters else record.radiusMeters
            if (radius > 0) {
                LabeledRow("Fence radius", "$radius m")
                Spacer(Modifier.height(8.dp))
            }
            if (hasFix) {
                LabeledRow("Checked-in at", "${record.latitude}, ${record.longitude}", valueStyle = PpType.monoSm)
            } else {
                Text("No GPS fix was recorded at check-in.", style = PpType.bodySm.copy(color = PpColor.Ink500))
            }
        }

        // Override reason — shown only when this was an override AND a reason was recorded.
        if (record.isOverride && !record.overrideReason.isNullOrBlank()) {
            PpCard {
                SectionLabel("Override reason")
                Spacer(Modifier.height(8.dp))
                Text(record.overrideReason!!, style = PpType.bodyStrong.copy(color = PpColor.Ink900))
                Spacer(Modifier.height(6.dp))
                Text(
                    "Overrides are logged and reviewed by your store admin.",
                    style = PpType.bodySm.copy(color = PpColor.Ink500),
                )
            }
        }

        NotePanel("Read-only. Wrong entry? Your store admin corrects it — every correction is logged.", tone = Tone.Neutral)
    }
}
