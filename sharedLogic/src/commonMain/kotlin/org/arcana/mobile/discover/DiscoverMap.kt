package org.arcana.mobile.discover

import org.arcana.mobile.data.DiscoverCategoryDto
import org.arcana.mobile.data.DiscoverStudioDto

/** The three lenses on Discover. [key] is the telemetry value. */
enum class DiscoverMode(val key: String, val label: String) {
    Studios("studios", "Studios"),
    Map("map", "Map"),
    Feedback("feedback", "Feedback"),
}

/** One studio location drawn on the map, with what its card says. */
data class DiscoverPin(
    val locationId: Int,
    val brandSlug: String,
    val brandName: String,
    val primaryColor: String,
    val locationName: String,
    val neighborhood: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val monogram: String,
    val categories: List<DiscoverCategoryDto>,
)

/**
 * Where the map opens: lower Manhattan, wide enough to take in Midtown and the
 * near side of Brooklyn. The app asks for no location permission, so there is
 * no "near me" to centre on.
 */
object DiscoverMapDefaults {
    const val LATITUDE = 40.7335
    const val LONGITUDE = -73.9925
    const val SPAN_METERS = 9_500.0
    /** Google's zoom for the couple of kilometres [framePins] gives one pin. */
    const val FOCUS_ZOOM = 14.5f
}

/**
 * Where the member left the map. The view model keeps it, so a trip to a studio
 * page and back lands on the same view instead of the opening frame. Each
 * platform stores what its own camera speaks: MapKit a region, Google a zoom.
 */
class MapCameraMemory {
    var latitude: Double? = null
    var longitude: Double? = null
    var latitudeDelta: Double? = null
    var longitudeDelta: Double? = null
    var zoom: Float? = null
    /** The last [DiscoverUiState.Success.pinsEpoch] the camera was framed for. */
    var framedEpoch: Int = 0
    /** The last [DiscoverUiState.Success.focusEpoch] the camera went to. */
    var focusedEpoch: Int = 0
}

/**
 * The pins for a directory. The server's neighborhood filter keeps a brand when
 * ANY of its locations matches, so the match is repeated per location here: a
 * map filtered to Tribeca shows Tribeca's pins, not every pin of a brand that
 * has one there. Locations without coordinates draw nothing.
 */
fun discoverPins(studios: List<DiscoverStudioDto>, neighborhoods: Set<String>): List<DiscoverPin> =
    studios.flatMap { studio ->
        studio.locations.mapNotNull { location ->
            val latitude = location.latitude ?: return@mapNotNull null
            val longitude = location.longitude ?: return@mapNotNull null
            if (neighborhoods.isNotEmpty() && location.neighborhood !in neighborhoods) return@mapNotNull null
            DiscoverPin(
                locationId = location.id,
                brandSlug = studio.slug,
                brandName = studio.name,
                primaryColor = studio.primaryColor,
                locationName = location.name,
                neighborhood = location.neighborhood,
                address = location.address,
                latitude = latitude,
                longitude = longitude,
                monogram = monogramFor(studio.name),
                categories = studio.categories,
            )
        }
    }

/** A rectangle of map to show: a centre and how many degrees it spans each way. */
data class MapFrame(val latitude: Double, val longitude: Double, val latitudeDelta: Double, val longitudeDelta: Double) {
    val south: Double get() = latitude - latitudeDelta / 2
    val north: Double get() = latitude + latitudeDelta / 2
    val west: Double get() = longitude - longitudeDelta / 2
    val east: Double get() = longitude + longitudeDelta / 2
}

// Room around the outermost pins, and the closest a frame may zoom: about two
// kilometres, so one pin in Harlem shows Harlem rather than one building.
private const val FRAME_MARGIN = 1.35
private const val MIN_FRAME_DEGREES = 0.018

/** The frame that takes in every pin, or null when there is nothing to frame. */
fun framePins(pins: List<DiscoverPin>): MapFrame? {
    if (pins.isEmpty()) return null
    val south = pins.minOf { it.latitude }
    val north = pins.maxOf { it.latitude }
    val west = pins.minOf { it.longitude }
    val east = pins.maxOf { it.longitude }
    return MapFrame(
        latitude = (south + north) / 2,
        longitude = (west + east) / 2,
        latitudeDelta = maxOf((north - south) * FRAME_MARGIN, MIN_FRAME_DEGREES),
        longitudeDelta = maxOf((east - west) * FRAME_MARGIN, MIN_FRAME_DEGREES),
    )
}

/** Up to two initials from the name's first letters, ignoring punctuation. */
fun monogramFor(name: String): String {
    val words = name.split(' ').map { w -> w.trimStart { !it.isLetterOrDigit() } }.filter { it.isNotEmpty() }
    val initials = words.take(2).map { it.first().uppercaseChar() }.joinToString("")
    return initials.ifEmpty { "?" }
}

/** What a screen reader says for a pin: the map draws only a monogram. */
fun pinAccessibilityLabel(pin: DiscoverPin): String =
    listOf(pin.brandName, pinPlaceLine(pin)).filter { it.isNotBlank() }.joinToString(", ")

/** "Flatiron · Flatiron / Chelsea", or the one that is there; a location named
 *  for its neighborhood says it once. */
fun pinPlaceLine(pin: DiscoverPin): String =
    listOf(pin.locationName, pin.neighborhood).filter { it.isNotBlank() }.distinct().joinToString(" · ")
