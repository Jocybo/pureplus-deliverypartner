package com.pureplus.delivery.data.location

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.pureplus.delivery.di.Deps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * DP-07 · Live-position reporting. A foreground service that pings the driver's GPS to
 * `POST /api/delivery/me/location` while the shift is open — this is what feeds customer-facing
 * tracking (BRD 6.5). Started right after check-in, stopped at check-out / logout.
 *
 * Balanced-power updates every ~20s (min 10s). The AuthInterceptor attaches the JWT; if the token
 * is gone (logged out) the ping simply fails and is swallowed. Runtime location permission is
 * requested on the check-in screen before this ever starts.
 */
class LocationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var client: FusedLocationProviderClient

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            scope.launch {
                runCatching {
                    Deps.driverRepository.ping(
                        lat = loc.latitude,
                        lng = loc.longitude,
                        accuracy = if (loc.hasAccuracy()) loc.accuracy.toDouble() else 0.0,
                    )
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        client = LocationServices.getFusedLocationProviderClient(this)
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        if (hasLocationPermission()) {
            val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, INTERVAL_MS)
                .setMinUpdateIntervalMillis(MIN_INTERVAL_MS)
                .build()
            runCatching { client.requestLocationUpdates(request, callback, Looper.getMainLooper()) }
                .onFailure { stopSelf() }
        } else {
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { client.removeLocationUpdates(callback) }
        scope.cancel()
        super.onDestroy()
    }

    private fun startAsForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Shift location", NotificationManager.IMPORTANCE_LOW),
                )
            }
        }
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PurePlus Delivery")
            .setContentText("Sharing your location while you're on the run")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        ServiceCompat.startForeground(this, NOTIF_ID, notification, type)
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val CHANNEL_ID = "pp_location"
        private const val NOTIF_ID = 4201
        private const val INTERVAL_MS = 20_000L
        private const val MIN_INTERVAL_MS = 10_000L

        /** Start after check-in. No-op if location permission was denied (service self-stops). */
        fun start(context: Context) {
            val intent = Intent(context, LocationService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        /** Stop at check-out / logout. */
        fun stop(context: Context) {
            context.stopService(Intent(context, LocationService::class.java))
        }
    }
}
