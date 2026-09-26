package com.pureplus.delivery.ui.shift

import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pureplus.delivery.PurePlusDeliveryApp
import com.pureplus.delivery.data.repository.ApiResult
import com.pureplus.delivery.data.repository.DriverRepository
import com.pureplus.delivery.di.Deps
import com.pureplus.delivery.util.LocationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

/** How the location gate stands right now — decides which primary action the screen shows. */
enum class LocationGate {
    /** Still resolving permission / GPS / a fix. */
    Checking,

    /** Runtime permission was denied but Android will still show the prompt. */
    PermissionDenied,

    /** Permission was blocked ("Don't ask again") — only app settings can fix it. */
    PermissionBlocked,

    /** Permission is granted but the device location switch is off. */
    LocationOff,

    /** Permission + GPS on, a fix is in hand — the driver may check in. */
    Ready,
}

data class CheckInState(
    val driverName: String = "",
    val gate: LocationGate = LocationGate.Checking,
    val locating: Boolean = true,
    val fix: LocationProvider.Fix? = null,
    val submitting: Boolean = false,
    /** pull-to-refresh in flight (re-fetching geofence + check-in status). */
    val refreshing: Boolean = false,
    val alreadyCheckedIn: Boolean = false,
    val checkedIn: Boolean = false,
    /** the driver already checked in AND out today — the shift is done (one check-in per day). */
    val shiftCompleted: Boolean = false,
    /** false until the initial check-in-status call returns — the Run tab waits on this to avoid a flash. */
    val statusResolved: Boolean = false,
    /** the last *server* attempt was rejected (out of fence) — offer a logged override. */
    val needsOverride: Boolean = false,
    /** one-shot: when set, the screen fires the system "turn on location" dialog, then clears it. */
    val gpsPrompt: IntentSenderRequest? = null,
    // DP-02 geofence (store centre + radius) for the map.
    val storeName: String? = null,
    val storeLat: Double? = null,
    val storeLng: Double? = null,
    val radiusMeters: Int = 0,
    val hasStoreLocation: Boolean = false,
    val error: String? = null,
) {
    /** Straight-line distance from the driver's current fix to the store centre, in metres. */
    val distanceMeters: Double?
        get() = if (fix != null && storeLat != null && storeLng != null)
            haversineMeters(fix.lat, fix.lng, storeLat, storeLng) else null

    /** True inside the fence, false outside, null while unknown (no fix or no store centre). */
    val insideFence: Boolean?
        get() = distanceMeters?.let { radiusMeters > 0 && it <= radiusMeters }
}

class CheckInViewModel(
    private val driver: DriverRepository,
    private val location: LocationProvider,
) : ViewModel() {
    private val _state = MutableStateFlow(CheckInState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.update { it.copy(driverName = Deps.tokenStore.getFullName()) }
            loadShiftContext()
        }
    }

    /** Pull-to-refresh — re-pull the geofence + check-in status. */
    fun refresh() {
        if (_state.value.refreshing) return
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            loadShiftContext()
            _state.update { it.copy(refreshing = false) }
        }
    }

    /** Fetch the store geofence (for the map) and the current check-in status (the run gate). */
    private suspend fun loadShiftContext() {
        // Store geofence for the map (best-effort; the map just won't draw the fence if missing).
        when (val g = driver.geofence()) {
            is ApiResult.Success -> _state.update {
                it.copy(
                    storeName = g.data.storeName,
                    storeLat = g.data.latitude,
                    storeLng = g.data.longitude,
                    radiusMeters = g.data.radiusMeters,
                    // The backend no longer sends a hasLocation flag — a fence exists iff we got a centre.
                    hasStoreLocation = g.data.latitude != null && g.data.longitude != null,
                )
            }
            is ApiResult.Failure -> Unit
        }
        when (val s = driver.checkInStatus()) {
            is ApiResult.Success -> {
                // "checkedIn" stays true for the whole day once a check-in row exists — so only
                // auto-advance to the run when the shift is genuinely OPEN (not yet checked out).
                // Otherwise a driver who ended their shift gets bounced back into the run.
                val shiftOpen = s.data.checkedIn && s.data.checkIn?.checkedOutAt == null
                // Checked in AND out today = shift done. The backend allows one check-in per day,
                // so we show a "completed" state rather than a check-in button that would be rejected.
                val completed = s.data.checkedIn && s.data.checkIn?.checkedOutAt != null
                if (shiftOpen) _state.update { it.copy(alreadyCheckedIn = true) }
                else if (completed) _state.update { it.copy(shiftCompleted = true) }
            }
            is ApiResult.Failure -> Unit
        }
        _state.update { it.copy(statusResolved = true) }
    }

    fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }

    fun hasLocationPermission(): Boolean = location.hasPermission()

    /** Result of the runtime permission request. [canAskAgain] is false once "Don't ask again" is set. */
    fun onPermissionResult(granted: Boolean, canAskAgain: Boolean) {
        if (granted) {
            ensureLocationOn()
        } else {
            _state.update {
                it.copy(
                    locating = false,
                    gate = if (canAskAgain) LocationGate.PermissionDenied else LocationGate.PermissionBlocked,
                )
            }
        }
    }

    /** Ask the driver to allow location again (re-shows the system prompt via the screen). */
    fun requestPermission() {
        _state.update { it.copy(gate = LocationGate.Checking, locating = true) }
    }

    /** Permission is granted — now make sure the device location switch is actually on. */
    fun ensureLocationOn() {
        viewModelScope.launch {
            _state.update { it.copy(gate = LocationGate.Checking, locating = true, error = null) }
            if (location.isLocationEnabled()) {
                refreshLocation()
            } else {
                val resolvable = location.resolvableLocationSettings()
                _state.update {
                    it.copy(
                        gate = LocationGate.LocationOff,
                        locating = false,
                        gpsPrompt = resolvable?.let { ex -> IntentSenderRequest.Builder(ex.resolution).build() },
                    )
                }
            }
        }
    }

    /** The screen has fired the system dialog — don't fire it again on recomposition. */
    fun onGpsPromptConsumed() = _state.update { it.copy(gpsPrompt = null) }

    /** Result of the in-app "turn on location" dialog. */
    fun onLocationServicesResult(enabled: Boolean) {
        if (enabled) refreshLocation()
        else _state.update { it.copy(gate = LocationGate.LocationOff, locating = false) }
    }

    private fun refreshLocation() {
        viewModelScope.launch {
            _state.update { it.copy(locating = true) }
            val fix = location.current()
            _state.update {
                if (fix != null) {
                    it.copy(locating = false, fix = fix, gate = LocationGate.Ready)
                } else if (!location.isLocationEnabled()) {
                    it.copy(locating = false, gate = LocationGate.LocationOff)
                } else {
                    // Permission + GPS on but no fix yet — let them proceed; the server confirms distance.
                    it.copy(locating = false, gate = LocationGate.Ready)
                }
            }
        }
    }

    fun checkIn() = submit(override = false, reason = null)

    fun overrideCheckIn(reason: String) =
        submit(override = true, reason = reason.ifBlank { "Manual override — GPS unavailable at store." })

    private fun submit(override: Boolean, reason: String?) {
        if (_state.value.submitting) return
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, error = null) }
            val fix = _state.value.fix
            when (val r = driver.checkIn(fix?.lat, fix?.lng, override, reason)) {
                is ApiResult.Success -> _state.update { it.copy(submitting = false, checkedIn = true) }
                is ApiResult.Failure -> _state.update {
                    it.copy(submitting = false, error = r.message, needsOverride = !override)
                }
            }
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            Deps.authRepository.logout()
            onDone()
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                CheckInViewModel(Deps.driverRepository, LocationProvider(PurePlusDeliveryApp.instance))
            }
        }
    }
}

/** Great-circle distance between two lat/lng points, in metres (mirrors the backend's check). */
private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6_371_000.0 // Earth radius, metres
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
        Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
        Math.sin(dLon / 2) * Math.sin(dLon / 2)
    return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
}
