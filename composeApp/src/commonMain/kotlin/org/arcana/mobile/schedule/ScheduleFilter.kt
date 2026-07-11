package org.arcana.mobile.schedule

/**
 * Expand a selection (whole-studio slugs + individual location ids) into the
 * flat, deduped `location_id` list the schedule fetch sends. Whole studios
 * resolve to their location ids via [catalog] (slug → its location ids).
 * `studio_slug` is never sent (the server ANDs the two params), so a mixed
 * multi-studio set MUST be expressed as locations only — this is that.
 */
internal fun expandSelectionToLocationIds(
    studioSlugs: Set<String>,
    locationIds: Set<Int>,
    catalog: Map<String, List<Int>>,
): List<Int> =
    (studioSlugs.flatMap { catalog[it].orEmpty() } + locationIds).distinct()

/**
 * An active start-time-of-day window (NY local wall-clock). Sent to the server
 * as `start_time_gte` / `start_time_lte` ("HH:MM"); either bound may be null
 * (one-sided). [label] is the chip text shown to the member.
 */
data class TimeFilter(
    val startGte: String?,
    val startLte: String?,
    val label: String,
)

/**
 * The quick time-window presets. Boundaries are NY-local "HH:MM"; a null bound
 * is unbounded on that side (Morning has no lower bound, Evening no upper).
 */
enum class TimePreset(val label: String, val startGte: String?, val startLte: String?) {
    Morning("Morning", null, "11:59"),
    Afternoon("Afternoon", "12:00", "16:59"),
    Evening("Evening", "17:00", null);

    fun toFilter(): TimeFilter = TimeFilter(startGte, startLte, label)
}

/** Format a 24h "HH:MM" as a friendly 12h clock, e.g. "18:00" → "6:00 PM".
 *  Avoids JVM-only String.format so it compiles for Kotlin/Native (iOS). */
internal fun formatTime12h(hhmm: String): String {
    val parts = hhmm.split(":")
    val h = parts[0].toIntOrNull() ?: 0
    val m = parts.getOrNull(1) ?: "00"
    val period = if (h < 12) "AM" else "PM"
    val h12 = when {
        h % 12 == 0 -> 12
        else -> h % 12
    }
    return "$h12:$m $period"
}

/** Chip label for a custom range: "6:00 AM – 9:00 PM" (or one-sided variants). */
internal fun customTimeLabel(startGte: String?, startLte: String?): String = when {
    startGte != null && startLte != null -> "${formatTime12h(startGte)} – ${formatTime12h(startLte)}"
    startGte != null -> "After ${formatTime12h(startGte)}"
    startLte != null -> "Before ${formatTime12h(startLte)}"
    else -> "Any time"
}

/** Build a custom TimeFilter from two optional "HH:MM" bounds. */
internal fun customTimeFilter(startGte: String?, startLte: String?): TimeFilter =
    TimeFilter(startGte, startLte, customTimeLabel(startGte, startLte))
