package org.arcana.mobile.discover

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions
import org.arcana.mobile.theme.Lime
import org.arcana.mobile.theme.Moss
import org.arcana.mobile.theme.Stone
import kotlin.math.roundToInt

// Zoom that frames DiscoverMapDefaults.SPAN_METERS on a phone-width map at this latitude.
private const val DEFAULT_ZOOM = 12.2f
// Past this the streets are as large as they get; a cluster still standing is
// pins at one address, so a tap opens its first instead of zooming again.
private const val CLUSTER_SPLIT_LIMIT = 17f
private const val PIN_DP = 34f
private const val SELECTED_PIN_DP = 42f
private const val CLUSTER_SMALL_DP = 40f
private const val CLUSTER_LARGE_DP = 46f
private const val CLUSTER_LARGE_FROM = 10
private const val CLUSTER_HALO_DP = 5f
private const val CLUSTER_HALO_ALPHA = 0.22f
private const val PIN_CLEARANCE_DP = 28f

// Quietens Google's basemap toward the muted map iOS shows: no business or
// transit icons, softer roads and water.
private const val MAP_STYLE = """[
 {"featureType":"poi","elementType":"labels","stylers":[{"visibility":"off"}]},
 {"featureType":"poi.park","elementType":"geometry","stylers":[{"color":"#dfe6cf"}]},
 {"featureType":"transit","stylers":[{"visibility":"off"}]},
 {"featureType":"road","elementType":"labels.icon","stylers":[{"visibility":"off"}]},
 {"featureType":"road","elementType":"geometry","stylers":[{"color":"#ffffff"}]},
 {"featureType":"road.arterial","elementType":"geometry","stylers":[{"color":"#fbf9f5"}]},
 {"featureType":"road.highway","elementType":"geometry","stylers":[{"color":"#ece7dc"}]},
 {"featureType":"landscape","elementType":"geometry","stylers":[{"color":"#f5f2ed"}]},
 {"featureType":"water","elementType":"geometry","stylers":[{"color":"#c9d8dc"}]},
 {"elementType":"labels.text.fill","stylers":[{"color":"#6b6e5f"}]},
 {"elementType":"labels.text.stroke","stylers":[{"color":"#f5f2ed"}]}
]"""

@Composable
actual fun StudioMap(
    pins: List<DiscoverPin>,
    selectedPinId: Int?,
    pinsEpoch: Int,
    focusEpoch: Int,
    camera: MapCameraMemory,
    onPinTapped: (Int) -> Unit,
    onMapTapped: () -> Unit,
    modifier: Modifier,
    bottomInset: Dp,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val pinTapped by rememberUpdatedState(onPinTapped)
    val mapTapped by rememberUpdatedState(onMapTapped)
    val mapView = remember { MapView(context, initialOptions(camera)) }
    var map by remember { mutableStateOf<GoogleMap?>(null) }
    var zoom by remember { mutableStateOf(camera.zoom ?: DEFAULT_ZOOM) }
    val icons = remember { PinIcons(context) }

    MapLifecycle(mapView)
    LaunchedEffect(mapView) {
        mapView.getMapAsync { ready ->
            ready.setMapStyle(MapStyleOptions(MAP_STYLE))
            ready.uiSettings.isMapToolbarEnabled = false
            ready.uiSettings.isMyLocationButtonEnabled = false
            ready.setOnCameraIdleListener {
                val position = ready.cameraPosition
                zoom = position.zoom
                camera.latitude = position.target.latitude
                camera.longitude = position.target.longitude
                camera.zoom = position.zoom
            }
            ready.setOnMapClickListener { mapTapped() }
            ready.setOnMarkerClickListener { marker ->
                val cluster = marker.tag as? PinCluster ?: return@setOnMarkerClickListener true
                if (cluster.single || ready.cameraPosition.zoom >= CLUSTER_SPLIT_LIMIT) {
                    pinTapped(cluster.pinIds.first())
                } else {
                    ready.animateCamera(CameraUpdateFactory.newLatLngZoom(marker.position, ready.cameraPosition.zoom + 2f))
                }
                true
            }
            map = ready
        }
    }

    val insetPx = with(density) { bottomInset.roundToPx() }
    LaunchedEffect(map, insetPx) { map?.setPadding(0, 0, 0, insetPx) }

    // Marks are redrawn when the pins, the selection or the clusters change. A
    // zoom that leaves every cluster as it was (most pinches do) redraws nothing.
    var drawn by remember { mutableStateOf<Pair<List<PinCluster>, Int?>?>(null) }
    LaunchedEffect(map, pins, zoom, selectedPinId) {
        val ready = map ?: return@LaunchedEffect
        val clusters = clusterPins(pins, zoom.toDouble(), alone = selectedPinId)
        if (drawn == clusters to selectedPinId) return@LaunchedEffect
        drawn = clusters to selectedPinId
        ready.clear()
        clusters.forEach { cluster ->
            val selected = selectedPinId != null && selectedPinId in cluster.pinIds
            val only = if (cluster.single) pins.first { it.locationId == cluster.pinIds.first() } else null
            val label = only?.monogram ?: cluster.pinIds.size.toString()
            ready.addMarker(
                MarkerOptions()
                    .position(LatLng(cluster.latitude, cluster.longitude))
                    .icon(icons.icon(label, selected, members = cluster.pinIds.size))
                    .contentDescription(only?.let(::pinAccessibilityLabel) ?: "${cluster.pinIds.size} studios")
                    .anchor(0.5f, 0.5f)
                    .zIndex(if (selected) 1f else 0f),
            )?.tag = cluster
        }
    }

    // The camera stays where the member left it unless the card would cover the pin.
    LaunchedEffect(map, selectedPinId, insetPx) {
        val ready = map ?: return@LaunchedEffect
        val pin = pins.firstOrNull { it.locationId == selectedPinId } ?: return@LaunchedEffect
        val at = LatLng(pin.latitude, pin.longitude)
        val clearance = with(density) { Dp(PIN_CLEARANCE_DP).roundToPx() }
        if (ready.projection.toScreenLocation(at).y > mapView.height - insetPx - clearance) {
            ready.animateCamera(CameraUpdateFactory.newLatLng(at))
        }
    }

    LaunchedEffect(map, pinsEpoch) {
        val ready = map ?: return@LaunchedEffect
        if (pinsEpoch == camera.framedEpoch) return@LaunchedEffect
        camera.framedEpoch = pinsEpoch
        framePins(pins)?.let { ready.show(it) }
    }

    LaunchedEffect(map, focusEpoch) {
        val ready = map ?: return@LaunchedEffect
        if (focusEpoch == camera.focusedEpoch) return@LaunchedEffect
        camera.focusedEpoch = focusEpoch
        framePins(pins.filter { it.locationId == selectedPinId })?.let { ready.show(it) }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private fun GoogleMap.show(frame: MapFrame) {
    val bounds = LatLngBounds(LatLng(frame.south, frame.west), LatLng(frame.north, frame.east))
    animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 0))
}

private fun initialOptions(camera: MapCameraMemory): GoogleMapOptions =
    GoogleMapOptions()
        .camera(
            CameraPosition.fromLatLngZoom(
                LatLng(camera.latitude ?: DiscoverMapDefaults.LATITUDE, camera.longitude ?: DiscoverMapDefaults.LONGITUDE),
                camera.zoom ?: DEFAULT_ZOOM,
            ),
        )
        .compassEnabled(false)
        .mapToolbarEnabled(false)
        .rotateGesturesEnabled(false)
        .tiltGesturesEnabled(false)

/** MapView is a plain View: it has to be walked through the lifecycle by hand. */
@Composable
private fun MapLifecycle(mapView: MapView) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        mapView.onCreate(Bundle())
        // Google's renderer gives its caches back when the system asks.
        val memory = object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) = Unit
            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = mapView.onLowMemory()
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) mapView.onLowMemory()
            }
        }
        mapView.context.registerComponentCallbacks(memory)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.context.unregisterComponentCallbacks(memory)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }
}

/**
 * Round Moss marks, drawn once per label and state. One location carries its
 * brand's monogram; a group is the larger mark with a soft halo and a count, so
 * it never reads as one studio.
 */
private class PinIcons(context: Context) {
    private val density = context.resources.displayMetrics.density
    private val cache = HashMap<String, BitmapDescriptor>()

    fun icon(label: String, selected: Boolean, members: Int): BitmapDescriptor =
        cache.getOrPut("$label|$selected|$members") { BitmapDescriptorFactory.fromBitmap(draw(label, selected, members)) }

    private fun draw(label: String, selected: Boolean, members: Int): Bitmap {
        val group = members > 1
        val coreDp = when {
            group && members >= CLUSTER_LARGE_FROM -> CLUSTER_LARGE_DP
            group -> CLUSTER_SMALL_DP
            selected -> SELECTED_PIN_DP
            else -> PIN_DP
        }
        val halo = if (group) CLUSTER_HALO_DP * density else 0f
        val size = (coreDp * density + halo * 2).roundToInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val centre = size / 2f
        val core = centre - halo
        val ring = 2f * density
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        if (group) {
            paint.color = Moss.copy(alpha = CLUSTER_HALO_ALPHA).toArgb()
            canvas.drawCircle(centre, centre, centre, paint)
        }
        paint.color = (if (selected) Lime else Stone).toArgb()
        canvas.drawCircle(centre, centre, core, paint)
        paint.color = Moss.toArgb()
        canvas.drawCircle(centre, centre, core - ring, paint)
        paint.color = Stone.toArgb()
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = core * 2 * (if (label.length > 2) 0.34f else 0.40f)
        // Centre on the capital's ink, not the line box.
        val bounds = android.graphics.Rect().also { paint.getTextBounds(label, 0, label.length, it) }
        canvas.drawText(label, centre, centre - bounds.exactCenterY(), paint)
        return bitmap
    }
}
