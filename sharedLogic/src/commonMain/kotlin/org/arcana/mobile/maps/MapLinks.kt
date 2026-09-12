package org.arcana.mobile.maps

/** A place the member can open in a maps app. Maps apps are asked to search
 *  for the business by name and address, so the member lands on the studio's
 *  listing rather than a bare pin; coordinates only bias that search. */
data class MapTarget(
    val label: String,
    val address: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** What to search for: "Barry's Chelsea". Defaults to [label]. */
    val business: String = label,
) {
    val hasCoordinates: Boolean get() = latitude != null && longitude != null

    val searchQuery: String
        get() = if (address.isBlank()) business else "$business, $address"
}

private const val UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.~"

/** RFC 3986 percent-encoding of a query value (UTF-8 bytes, space as %20). */
fun percentEncode(value: String): String = buildString {
    for (byte in value.encodeToByteArray()) {
        val c = byte.toInt() and 0xFF
        if (c < 0x80 && UNRESERVED.indexOf(c.toChar()) >= 0) append(c.toChar())
        else append('%').append(HEX[c shr 4]).append(HEX[c and 0x0F])
    }
}

private const val HEX = "0123456789ABCDEF"

fun appleMapsUrl(target: MapTarget): String =
    "maps://?q=${percentEncode(target.searchQuery)}" +
        if (target.hasCoordinates) "&sll=${target.latitude},${target.longitude}" else ""

fun googleMapsUrl(target: MapTarget): String =
    "comgooglemaps://?q=${percentEncode(target.searchQuery)}" +
        if (target.hasCoordinates) "&center=${target.latitude},${target.longitude}&zoom=16" else ""

/** Android `geo:` intent URI. A text `q` makes Maps search rather than drop a
 *  pin; the coordinates bias the search to the right spot. */
fun geoUri(target: MapTarget): String =
    if (target.hasCoordinates) {
        "geo:${target.latitude},${target.longitude}?q=${percentEncode(target.searchQuery)}"
    } else {
        "geo:0,0?q=${percentEncode(target.searchQuery)}"
    }
