package com.pureplus.delivery.ui.shift

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.pureplus.delivery.R
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import android.graphics.Color as AndroidColor
import java.io.File

/**
 * DP-02 · The store geofence on an **OpenStreetMap** map (osmdroid — free tiles, no API key or billing).
 * Draws the fence circle **green when the driver is inside**, **red when outside** (amber while the
 * position is unknown), with a marker on the store and one on the driver. The circle/marker logic works
 * offline; only the map tiles need connectivity.
 */
@Composable
fun GeofenceMap(
    storeLat: Double,
    storeLng: Double,
    radiusMeters: Int,
    userLat: Double?,
    userLng: Double?,
    inside: Boolean?,
    storeName: String? = null,
    showStoreMarker: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val fenceArgb = when (inside) {
        true -> Color(0xFF16A34A)   // green — inside the fence
        false -> Color(0xFFDC2626)  // red — outside the fence
        null -> Color(0xFFF59E0B)   // amber — position unknown
    }.toArgb()

    // Build the MapView once and keep the handle — osmdroid isn't lifecycle-aware inside Compose, so we
    // resume/pause/detach it ourselves. Tiles live in the app cache (no storage permission needed) and
    // the user agent identifies the app per OSM's tile-usage policy.
    val mapView = remember {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            osmdroidBasePath = context.cacheDir
            osmdroidTileCache = File(context.cacheDir, "osm_tiles")
        }
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setTilesScaledToDpi(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(16.0)
            controller.setCenter(GeoPoint(storeLat, storeLng))
        }
    }

    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp)),
        update = { map ->
            map.overlays.clear()

            val store = GeoPoint(storeLat, storeLng)
            val user = if (userLat != null && userLng != null) GeoPoint(userLat, userLng) else null

            // Fence circle — filled translucent, solid outline, coloured by inside/outside/unknown.
            val fence = Polygon().apply {
                points = Polygon.pointsAsCircle(store, radiusMeters.toDouble())
                fillPaint.color = AndroidColor.argb(
                    38, AndroidColor.red(fenceArgb), AndroidColor.green(fenceArgb), AndroidColor.blue(fenceArgb),
                )
                outlinePaint.color = fenceArgb
                outlinePaint.strokeWidth = 6f
            }
            map.overlays.add(fence)

            // Store = blue store pin; driver = red person pin — anchored centre so the disc sits on the point.
            if (showStoreMarker) {
                map.overlays.add(
                    Marker(map).apply {
                        position = store
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        // Store pin rendered smaller than the driver pin (28dp vs the 42dp user marker).
                        icon = scaledMarkerIcon(context, R.drawable.ic_marker_store, 28)
                        title = storeName?.takeIf { it.isNotBlank() } ?: "Store"
                        snippet = "Check-in centre"
                    },
                )
            }
            if (user != null) {
                map.overlays.add(
                    Marker(map).apply {
                        position = user
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = ContextCompat.getDrawable(context, R.drawable.ic_marker_user)
                        title = "You"
                    },
                )
            }

            // Frame the fence (and the driver, if outside it) once the view has been laid out.
            val framePoints = ArrayList(fence.actualPoints)
            if (user != null) framePoints.add(user)
            val box = BoundingBox.fromGeoPoints(framePoints)
            map.post { runCatching { map.zoomToBoundingBox(box, true, 80) } }

            map.invalidate()
        },
    )
}

/** Render a marker drawable at a fixed dp size (osmdroid draws a marker at its icon's intrinsic size). */
private fun scaledMarkerIcon(context: Context, resId: Int, sizeDp: Int): Drawable {
    val px = (sizeDp * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
    val src = ContextCompat.getDrawable(context, resId)!!
    val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    src.setBounds(0, 0, px, px)
    src.draw(Canvas(bitmap))
    return BitmapDrawable(context.resources, bitmap)
}
