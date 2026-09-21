package org.arcana.mobile.discover

import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin

/** Pins that would overlap at a zoom, drawn as one mark at their centre. */
data class PinCluster(val latitude: Double, val longitude: Double, val pinIds: List<Int>) {
    val single: Boolean get() = pinIds.size == 1
}

// Web Mercator: the world is 256dp square at zoom 0 and doubles per level.
private const val WORLD_DP = 256.0
// A pin is about 36dp across; a cell a little wider keeps neighbours apart.
private const val CELL_DP = 52.0
private const val MAX_MERCATOR_LATITUDE = 85.05112878

/**
 * Groups pins by the screen cell they fall in at [zoom]. For the Android map:
 * MapKit clusters natively on iOS. Stable for a given input, so a camera that
 * comes to rest at the same zoom redraws the same marks. Pins that share a
 * coordinate never separate, whatever the zoom, except [alone]: the selected
 * pin is always its own mark, or a place reached from a class page could sit
 * unseen inside a group.
 */
fun clusterPins(pins: List<DiscoverPin>, zoom: Double, alone: Int? = null): List<PinCluster> {
    val scale = WORLD_DP * 2.0.pow(zoom)
    val (single, rest) = pins.partition { it.locationId == alone }
    return single.map { PinCluster(it.latitude, it.longitude, listOf(it.locationId)) } + rest
        .groupBy { pin ->
            val x = (pin.longitude + 180.0) / 360.0 * scale
            val latitude = pin.latitude.coerceIn(-MAX_MERCATOR_LATITUDE, MAX_MERCATOR_LATITUDE)
            val sine = sin(latitude * PI / 180.0)
            val y = (0.5 - ln((1 + sine) / (1 - sine)) / (4 * PI)) * scale
            floor(x / CELL_DP).toLong() to floor(y / CELL_DP).toLong()
        }
        .values
        .map { members ->
            PinCluster(
                latitude = members.sumOf { it.latitude } / members.size,
                longitude = members.sumOf { it.longitude } / members.size,
                pinIds = members.map { it.locationId },
            )
        }
}
