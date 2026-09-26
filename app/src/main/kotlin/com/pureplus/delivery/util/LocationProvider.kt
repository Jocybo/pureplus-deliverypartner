package com.pureplus.delivery.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.await

/** Simple one-shot GPS read + the settings checks that let us *enforce* location before check-in. */
class LocationProvider(private val context: Context) {

    private val client = LocationServices.getFusedLocationProviderClient(context)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** True when device location (the GPS master switch) is turned on at the OS level. */
    fun isLocationEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return LocationManagerCompat.isLocationEnabled(lm)
    }

    /**
     * Asks Play Services whether current settings can satisfy a high-accuracy request. Returns:
     * - `null` when location is already on (nothing to do), or
     * - a [ResolvableApiException] whose `resolution` launches the in-app system dialog that turns
     *   GPS on with a single tap (the recommended consent flow — no trip to Settings).
     */
    suspend fun resolvableLocationSettings(): ResolvableApiException? {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10_000L).build()
        val settings = LocationSettingsRequest.Builder()
            .addLocationRequest(request)
            .setAlwaysShow(true)
            .build()
        return try {
            LocationServices.getSettingsClient(context).checkLocationSettings(settings).await()
            null
        } catch (e: ResolvableApiException) {
            e
        } catch (e: Exception) {
            null
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun current(): Fix? {
        if (!hasPermission()) return null
        return try {
            val loc = client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
                ?: client.lastLocation.await()
                ?: return null
            Fix(loc.latitude, loc.longitude, if (loc.hasAccuracy()) loc.accuracy.toDouble() else 0.0)
        } catch (e: Exception) {
            null
        }
    }

    data class Fix(val lat: Double, val lng: Double, val accuracy: Double)
}
