package com.pureplus.delivery.ui.shift

import android.Manifest
import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pureplus.delivery.ui.components.BottomNavBar
import com.pureplus.delivery.ui.components.DarkHeader
import com.pureplus.delivery.ui.components.LogoutIconButton
import com.pureplus.delivery.ui.components.NavTab
import com.pureplus.delivery.ui.components.NotePanel
import com.pureplus.delivery.ui.components.PpCard
import com.pureplus.delivery.ui.components.PrimaryButton
import com.pureplus.delivery.ui.components.SectionLabel
import com.pureplus.delivery.ui.components.TextLink
import com.pureplus.delivery.ui.components.Tone
import com.pureplus.delivery.ui.theme.PpColor
import com.pureplus.delivery.ui.theme.PpType
import com.pureplus.delivery.util.openAppSettings

/**
 * DP-02 · Shift check-in — the **Run tab's state while off shift**. The bottom nav stays visible so the
 * driver can explore Earnings/Me/Stops without checking in. Location is required to check in (permission
 * + GPS prompted in-app with consent); after a genuine *server* geo-fence rejection a logged override is
 * offered. When check-in succeeds (or a shift is already open) the parent switches this tab to the run.
 */
@Composable
fun CheckInScreen(
    onTab: (NavTab) -> Unit,
    onLogout: () -> Unit,
    viewModel: CheckInViewModel = viewModel(factory = CheckInViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity

    // Launches the in-app system dialog that turns GPS on with one tap (from a ResolvableApiException).
    val gpsResolutionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> viewModel.onLocationServicesResult(result.resultCode == Activity.RESULT_OK) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        // After a denial, if the OS still lets us show a rationale the driver can be asked again.
        val canAskAgain = activity?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.ACCESS_FINE_LOCATION)
        } ?: true
        viewModel.onPermissionResult(granted, canAskAgain || granted)
    }

    fun askPermission() =
        permissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
        )

    // On entry: if we already hold permission, go straight to the GPS-on check; else ask for it.
    LaunchedEffect(Unit) {
        if (viewModel.hasLocationPermission()) viewModel.ensureLocationOn() else askPermission()
    }

    // When the VM asks, fire the one-shot "turn on location" dialog, then clear it.
    LaunchedEffect(state.gpsPrompt) {
        state.gpsPrompt?.let {
            gpsResolutionLauncher.launch(it)
            viewModel.onGpsPromptConsumed()
        }
    }

    // Toasts: success when the check-in lands; the same error text (also shown in the panel below) on failure.
    LaunchedEffect(state.checkedIn) {
        if (state.checkedIn) {
            Toast.makeText(context, "Checked in successfully — your shift has started.", Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(PpColor.Canvas),
    ) {
        DarkHeader(
            title = "${viewModel.greeting()}, ${state.driverName.ifBlank { "Partner" }}",
            eyebrow = "Start of shift",
            subtitle = "Check in at the store to open your run.",
            trailing = { LogoutIconButton(onClick = { viewModel.logout(onLogout) }) },
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.shiftCompleted) {
                NotePanel(
                    "You've completed your shift for today — checked in and checked out. " +
                        "See you next shift! (One shift per day.)",
                    tone = Tone.Success,
                )
            }

            if (!state.shiftCompleted) {
                if (state.hasStoreLocation && state.storeLat != null && state.storeLng != null) {
                    PpCard {
                        SectionLabel(
                            state.storeName?.takeIf { it.isNotBlank() }
                                ?.let { "Store geofence · $it" } ?: "Store geofence",
                        )
                        Spacer(Modifier.height(10.dp))
                        GeofenceMap(
                            storeLat = state.storeLat!!,
                            storeLng = state.storeLng!!,
                            radiusMeters = state.radiusMeters,
                            userLat = state.fix?.lat,
                            userLng = state.fix?.lng,
                            inside = state.insideFence,
                            storeName = state.storeName,
                        )
                        Spacer(Modifier.height(10.dp))
                        val (line, colour) = when (state.insideFence) {
                            true -> "You're inside the ${state.radiusMeters} m store fence" +
                                (state.distanceMeters?.let { " · ${it.toInt()} m from centre" } ?: "") to PpColor.Success
                            false -> "You're outside the fence" +
                                (state.distanceMeters?.let { " · ${it.toInt()} m from the store (limit ${state.radiusMeters} m)" } ?: "") to PpColor.Danger
                            null -> "Locating you on the map…" to PpColor.Ink500
                        }
                        Text(line, style = PpType.bodyStrong.copy(color = colour))
                    }
                }

                PpCard {
                    SectionLabel("Location")
                    Spacer(Modifier.height(10.dp))
                    val text = when (state.gate) {
                        LocationGate.Checking ->
                            if (state.locating) "Getting your location…" else "Checking location…"
                        LocationGate.PermissionDenied ->
                            "Location access is needed to check in. Please allow it to continue."
                        LocationGate.PermissionBlocked ->
                            "Location access is blocked. Open Settings and allow location for this app to check in."
                        LocationGate.LocationOff ->
                            "GPS is switched off. Turn on location to check in at the store."
                        LocationGate.Ready ->
                            state.fix?.let { "Location ready · ±${it.accuracy.toInt()} m accuracy" }
                                ?: "Location on. Confirming distance at check-in."
                    }
                    Text(text, style = PpType.body.copy(color = PpColor.Ink700))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Check-in is only possible inside the store fence. Distance is confirmed by the server.",
                        style = PpType.bodySm.copy(color = PpColor.Ink500),
                    )
                }
            }

            if (state.error != null) {
                NotePanel(state.error!!, tone = Tone.Danger)
            }
        }
        }

        Column(
            modifier = Modifier.fillMaxWidth().background(PpColor.Canvas).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Check-in actions are hidden once the shift is done for the day (one check-in per day).
            if (!state.shiftCompleted) {
                when (state.gate) {
                    LocationGate.PermissionDenied -> PrimaryButton(
                        text = "Allow location access",
                        onClick = { askPermission() },
                        height = 60,
                    )
                    LocationGate.PermissionBlocked -> PrimaryButton(
                        text = "Open settings",
                        onClick = { openAppSettings(context) },
                        height = 60,
                    )
                    LocationGate.LocationOff -> PrimaryButton(
                        text = "Turn on GPS",
                        onClick = { viewModel.ensureLocationOn() },
                        height = 60,
                    )
                    else -> PrimaryButton(
                        text = "Check in & start shift",
                        onClick = viewModel::checkIn,
                        loading = state.submitting,
                        enabled = state.gate == LocationGate.Ready,
                        height = 60,
                    )
                }

                // Override is only offered after the server itself rejects the fix (out of fence),
                // never as a shortcut around turning GPS on.
                if (state.needsOverride) {
                    TextLink(
                        "Still can't check in? Request manual override",
                        onClick = { viewModel.overrideCheckIn("GPS unavailable / outside fence at store") },
                    )
                    Text(
                        "Overrides are logged as an exception and need store admin approval.",
                        style = PpType.bodySm.copy(color = PpColor.Ink500),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                // Shift done for the day — nudge toward the rest of the app (bottom nav below).
                Text(
                    "Browse your earnings, attendance and profile from the tabs below.",
                    style = PpType.bodySm.copy(color = PpColor.Ink500),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // The bottom nav is always present — the app is explorable without an active shift.
        BottomNavBar(current = NavTab.Run, onSelect = onTab)
    }
}
