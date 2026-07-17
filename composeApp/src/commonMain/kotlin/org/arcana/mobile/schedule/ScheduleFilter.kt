package org.arcana.mobile.schedule

import kotlin.math.roundToInt

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

// --- Custom-range slider arithmetic -------------------------------------
// The slider works in minutes-since-midnight rather than whole hours, so its
// tick granularity is a property of TIME_SLIDER_STEP_MINUTES instead of being
// baked into the type. "HH:MM" already carries minutes end to end (TimeFilter,
// the presets' 11:59/16:59, formatTime12h, and the server's start_time_gte /
// start_time_lte), so nothing downstream had to change to allow half-hours.

/** Earliest selectable start time on the custom slider (06:00). */
internal const val TIME_SLIDER_MIN_MINUTE = 6 * 60

/** Latest selectable start time on the custom slider (22:00). */
internal const val TIME_SLIDER_MAX_MINUTE = 22 * 60

/** Slider tick granularity. Both bounds above are multiples of this, so every
 *  reachable value stays step-aligned. */
internal const val TIME_SLIDER_STEP_MINUTES = 30

/** Narrowest range the two handles may span. Deliberately still a full hour,
 *  independent of the tick granularity: finer ticks let a member place the
 *  window at 9:30, not shrink it to 30 minutes. */
internal const val TIME_SLIDER_MIN_GAP_MINUTES = 60

/** "HH:MM" → minutes since midnight; null when absent or unparseable. A missing
 *  minute component reads as :00, matching [formatTime12h]'s leniency. */
internal fun hhmmToMinutes(hhmm: String?): Int? {
    val parts = hhmm?.split(":") ?: return null
    val h = parts.getOrNull(0)?.toIntOrNull() ?: return null
    val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
    return h * 60 + m
}

/** Minutes since midnight → zero-padded 24h "HH:MM". */
internal fun minutesToHhmm(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
}

/** Snap [minutes] to the nearest [step] tick, clamped to [min]..[max]. Rounds to
 *  nearest rather than flooring, so dragging feels symmetric between ticks. */
internal fun snapMinutes(
    minutes: Int,
    step: Int = TIME_SLIDER_STEP_MINUTES,
    min: Int = TIME_SLIDER_MIN_MINUTE,
    max: Int = TIME_SLIDER_MAX_MINUTE,
): Int = ((minutes.toFloat() / step).roundToInt() * step).coerceIn(min, max)
