package com.pureplus.delivery.ui.stops

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.pureplus.delivery.R
import com.pureplus.delivery.data.remote.Stop
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File

/**
 * DP-05 · Customer addresses for the day's stops on an **OpenStreetMap** map (osmdroid — free tiles, no
 * API key). Each stop with a lat/long is dropped as a violet "home" marker; the camera frames them all.
 * Stops without coordinates are simply skipped (the list below still shows them).
 */
@Composable
fun StopsMap(
    stops: List<Stop>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // Only stops that actually carry coordinates can be plotted.
    val plotted = remember(stops) {
        stops.mapNotNull { s ->
            val lat = s.latitude
            val lng = s.longitude
            if (lat != null && lng != null) PlottedStop(GeoPoint(lat, lng), s.receiverName, s.orderId) else null
        }
    }

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
            controller.setZoom(13.0)
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
        modifier = modifier,
        update = { map ->
            map.overlays.clear()

            plotted.forEach { stop ->
                map.overlays.add(
                    Marker(map).apply {
                        position = stop.point
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = ContextCompat.getDrawable(context, R.drawable.ic_marker_customer)
                        title = stop.receiverName?.takeIf { it.isNotBlank() } ?: "Stop #${stop.orderId}"
                    },
                )
            }

            when {
                plotted.size == 1 -> {
                    map.controller.setZoom(16.0)
                    map.controller.setCenter(plotted.first().point)
                }
                plotted.size > 1 -> {
                    val box = BoundingBox.fromGeoPoints(plotted.map { it.point })
                    map.post { runCatching { map.zoomToBoundingBox(box, false, 100) } }
                }
            }
            map.invalidate()
        },
    )
}

private data class PlottedStop(val point: GeoPoint, val receiverName: String?, val orderId: Int)
