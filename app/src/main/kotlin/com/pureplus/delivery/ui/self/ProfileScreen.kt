package com.pureplus.delivery.ui.self

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pureplus.delivery.data.remote.DriverDocument
import com.pureplus.delivery.data.remote.DriverShiftInfo
import com.pureplus.delivery.ui.components.BottomNavBar
import com.pureplus.delivery.ui.components.LabeledRow
import com.pureplus.delivery.ui.components.NavTab
import com.pureplus.delivery.ui.components.OutlineButton
import com.pureplus.delivery.ui.components.PpCard
import com.pureplus.delivery.ui.components.SectionLabel
import com.pureplus.delivery.ui.components.StatusChip
import com.pureplus.delivery.ui.components.Tone
import com.pureplus.delivery.ui.components.clickableNoRipple
import com.pureplus.delivery.ui.theme.PpColor
import com.pureplus.delivery.ui.theme.PpType
import com.pureplus.delivery.util.shortDate

/** DP-21 · Profile, vehicle, store, shift & zone — all read-only except the driver's own contact number. */
@Composable
fun ProfileScreen(
    onTab: (NavTab) -> Unit,
    onOpenAttendance: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenBulk: () -> Unit,
    onLogout: () -> Unit,
    viewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val p = state.profile
    var editingContact by remember { mutableStateOf(false) }
    var submittingDoc by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(PpColor.Canvas)) {
        Row(Modifier.fillMaxWidth().background(PpColor.Ink900).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            val displayName = p?.name?.ifBlank { null } ?: state.name
            Box(Modifier.size(60.dp).clip(RoundedCornerShape(999.dp)).background(PpColor.Brand500), contentAlignment = Alignment.Center) {
                Text(displayName.take(2).uppercase().ifBlank { "DP" }, style = PpType.cardTitle.copy(color = PpColor.Ink900))
            }
            Column(Modifier.padding(start = 14.dp)) {
                Text(displayName.ifBlank { "Delivery Partner" }, style = PpType.cardTitle.copy(color = PpColor.White))
                Text(
                    listOfNotNull(p?.roleName, p?.storeName ?: state.storeId.takeIf { it >= 0 }?.let { "Store $it" })
                        .joinToString(" · ").ifBlank { "Employee #${state.employeeId}" },
                    style = PpType.bodySm.copy(color = PpColor.BrandOnDark),
                    modifier = Modifier.padding(top = 5.dp),
                )
                p?.status?.let { Text(it, style = PpType.monoSm.copy(color = PpColor.BrandOnDark), modifier = Modifier.padding(top = 3.dp)) }
            }
        }

        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {

            // Quick links
            PpCard(padding = PaddingValues(0.dp)) {
                LinkRow("Notifications", onOpenNotifications, badge = state.unread.takeIf { it > 0 }?.toString())
                Divider()
                LinkRow("Attendance", onOpenAttendance)
                Divider()
                LinkRow("Delivery history", onOpenHistory)
                Divider()
                LinkRow("Bulk / event jobs", onOpenBulk)
            }

            // Personal — contact is editable
            PpCard {
                SectionLabel("Personal")
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Contact", style = PpType.bodyStrong.copy(color = PpColor.Ink700))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p?.contactNumber?.ifBlank { "—" } ?: "—", style = PpType.mono)
                        Text(
                            "Edit",
                            style = PpType.bodyStrong.copy(color = PpColor.Brand700),
                            modifier = Modifier.clickableNoRipple { editingContact = true }.padding(start = 12.dp),
                        )
                    }
                }
                p?.joiningDate?.let { Spacer(Modifier.height(8.dp)); LabeledRow("Joined", shortDate(it)) }
                p?.qualification?.takeIf { it.isNotBlank() }?.let { Spacer(Modifier.height(8.dp)); LabeledRow("Qualification", it) }
                p?.experienceYears?.let { Spacer(Modifier.height(8.dp)); LabeledRow("Experience", "$it yr") }
            }

            // Vehicle
            state.vehicle?.let { v ->
                PpCard {
                    SectionLabel("Vehicle")
                    Spacer(Modifier.height(10.dp))
                    LabeledRow("Number", v.vehicleNumber ?: "—")
                    v.vehicleType?.let { Spacer(Modifier.height(8.dp)); LabeledRow("Type", it) }
                    v.capacity?.let { Spacer(Modifier.height(8.dp)); LabeledRow("Capacity", "$it cans") }
                    v.insuranceValidTo?.let { Spacer(Modifier.height(8.dp)); LabeledRow("Insurance to", shortDate(it)) }
                    v.nextServiceDue?.let { Spacer(Modifier.height(8.dp)); LabeledRow("Next service", shortDate(it)) }
                }
            }

            // Store
            state.store?.let { s ->
                PpCard {
                    SectionLabel("Store")
                    Spacer(Modifier.height(10.dp))
                    Text(s.storeName ?: "Store ${s.storeId}", style = PpType.bodyStrong)
                    listOfNotNull(s.area, s.address).joinToString(" · ").takeIf { it.isNotBlank() }?.let {
                        Text(it, style = PpType.bodySm.copy(color = PpColor.Ink500), modifier = Modifier.padding(top = 4.dp))
                    }
                    s.geofenceRadiusMeters?.let { Spacer(Modifier.height(8.dp)); LabeledRow("Check-in radius", "$it m") }
                }
            }

            // Shift
            state.shift?.let { sh ->
                PpCard {
                    SectionLabel("Shift")
                    Spacer(Modifier.height(10.dp))
                    LabeledRow(sh.shiftName ?: "Shift", timeRange(sh))
                    sh.days.takeIf { it.isNotEmpty() }?.let {
                        Spacer(Modifier.height(8.dp))
                        LabeledRow("Days", it.joinToString(", ") { d -> DAY_LABELS.getOrElse(d) { "?" } })
                    }
                }
            }

            // Zone
            state.zones.takeIf { it.isNotEmpty() }?.let { zones ->
                PpCard {
                    SectionLabel("Delivery zone")
                    Spacer(Modifier.height(10.dp))
                    zones.forEach { z ->
                        Text(z.zoneName ?: "Zone ${z.zoneId}", style = PpType.bodyStrong)
                        if (z.pincodes.isNotEmpty()) {
                            Text(z.pincodes.joinToString(", "), style = PpType.bodySm.copy(color = PpColor.Ink500), modifier = Modifier.padding(top = 2.dp, bottom = 6.dp))
                        }
                    }
                }
            }

            // Documents — real list + a submit form (lands as PENDING for store-admin approval).
            PpCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Documents")
                    Text(
                        "Submit",
                        style = PpType.bodyStrong.copy(color = PpColor.Brand700),
                        modifier = Modifier.clickableNoRipple { submittingDoc = true }.padding(4.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                if (state.documents.isEmpty()) {
                    Text("No documents submitted yet.", style = PpType.bodySm.copy(color = PpColor.Ink500))
                } else {
                    state.documents.forEach { d -> DocumentRow(d); Spacer(Modifier.height(8.dp)) }
                }
                state.docMessage?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, style = PpType.bodySm.copy(color = PpColor.Success))
                }
            }

            OutlineButton("Log out", onLogout, borderColor = PpColor.DangerBorder, contentColor = PpColor.Danger)
        }

        BottomNavBar(current = NavTab.Me, onSelect = onTab)
    }

    if (editingContact) {
        EditContactDialog(
            current = p?.contactNumber.orEmpty(),
            saving = state.savingContact,
            onDismiss = { editingContact = false },
            onSave = { viewModel.updateContactNumber(it) { ok -> if (ok) editingContact = false } },
        )
    }

    if (submittingDoc) {
        SubmitDocumentDialog(
            saving = state.submittingDoc,
            onDismiss = { submittingDoc = false },
            onSubmit = { type, number, url ->
                viewModel.submitDocument(type, number, url, null, null) { ok -> if (ok) submittingDoc = false }
            },
        )
    }
}

@Composable
private fun DocumentRow(doc: DriverDocument) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(DOC_LABELS[doc.docType] ?: (doc.docType ?: "Document"), style = PpType.bodyStrong.copy(color = PpColor.Ink900))
            doc.docNumber?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = PpType.monoSm.copy(color = PpColor.Ink500))
            }
        }
        val tone = when (doc.status?.uppercase()) {
            "APPROVED", "VERIFIED" -> Tone.Success
            "REJECTED" -> Tone.Danger
            else -> Tone.Pending
        }
        StatusChip(doc.status ?: "Pending", tone)
    }
}

private val DOC_LABELS = mapOf(
    "AADHAAR" to "Aadhaar",
    "PAN" to "PAN",
    "DRIVING_LICENCE" to "Driving licence",
    "BANK_PASSBOOK" to "Bank passbook",
    "VEHICLE_RC" to "Vehicle RC",
    "VEHICLE_INSURANCE" to "Vehicle insurance",
    "OTHER" to "Other",
)

@Composable
private fun SubmitDocumentDialog(saving: Boolean, onDismiss: () -> Unit, onSubmit: (String, String, String) -> Unit) {
    var type by remember { mutableStateOf("AADHAAR") }
    var number by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Submit a document", style = PpType.cardTitle) },
        text = {
            Column {
                Text("Type", style = PpType.label.copy(color = PpColor.Ink500))
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DOC_LABELS.forEach { (value, label) ->
                        val selected = type == value
                        Text(
                            label,
                            style = PpType.bodySm.copy(color = if (selected) PpColor.White else PpColor.Ink700),
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (selected) PpColor.Ink900 else PpColor.Canvas)
                                .clickableNoRipple { type = value }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = number, onValueChange = { number = it },
                    singleLine = true, label = { Text("Document number") }, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    singleLine = true, label = { Text("File link (optional)") }, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(type, number, url) }, enabled = !saving) {
                Text(if (saving) "Submitting…" else "Submit")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") } },
    )
}


@Composable
private fun EditContactDialog(current: String, saving: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(TextFieldValue(current)) }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Edit contact number", style = PpType.cardTitle) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                label = { Text("Mobile number") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(value.text) }, enabled = !saving && value.text.isNotBlank()) {
                Text(if (saving) "Saving…" else "Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") } },
    )
}

private val DAY_LABELS = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

private fun timeRange(shift: DriverShiftInfo): String {
    val start = shift.startTime?.take(5)
    val end = shift.endTime?.take(5)
    return when {
        start != null && end != null -> "$start – $end"
        start != null -> start
        else -> "—"
    }
}

@Composable
private fun LinkRow(label: String, onClick: () -> Unit, badge: String? = null) {
    Row(
        Modifier.fillMaxWidth().clickableNoRipple(onClick).padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = PpType.bodyStrong.copy(color = PpColor.Ink900))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (badge != null) {
                Box(
                    Modifier.clip(RoundedCornerShape(999.dp)).background(PpColor.Danger).padding(horizontal = 8.dp, vertical = 2.dp),
                ) { Text(badge, style = PpType.monoSm.copy(color = PpColor.White)) }
            }
            Text("›", style = PpType.cardTitle.copy(color = PpColor.Ink500), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(PpColor.LineSoft))
}
