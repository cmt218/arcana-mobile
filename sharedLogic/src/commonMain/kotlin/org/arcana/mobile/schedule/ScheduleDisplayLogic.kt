package org.arcana.mobile.schedule

import kotlinx.datetime.IllegalTimeZoneException
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone

/*
 * Pure display logic shared by the Schedule/Detail screens and their unit
 * tests. Lives in :sharedLogic (not :sharedUI, the Compose module) so the tests that lock this
 * behavior stay next to the rest of the suite, and any future UI layer reuses
 * the exact same rules. Same package as the screens, so call sites resolve
 * without imports.
 */

/**
 * The day a horizontal swipe lands on, or null at the window's edges.
 * `forward` (a left swipe) advances one day; otherwise steps back one. Pure
 * so the bounds logic is unit-testable without the gesture plumbing.
 */
fun dayAfterSwipe(
    days: List<LocalDate>,
    selected: LocalDate,
    forward: Boolean,
): LocalDate? {
    val idx = days.indexOf(selected)
    if (idx < 0) return null
    return days.getOrNull(if (forward) idx + 1 else idx - 1)
}

/**
 * Resolve a session's location timezone for display: a class shows its own
 * local wall-clock (a 6 PM Williamsburg class reads "18:00" wherever the
 * device is). Server timezone strings are IANA ids, but `TimeZone.of` throws
 * on an unknown id — fall back to the schedule's anchor timezone rather than
 * crash the screen on a bad row.
 */
fun sessionTimeZone(id: String): TimeZone = try {
    TimeZone.of(id)
} catch (_: IllegalTimeZoneException) {
    ScheduleViewModel.ScheduleTimeZone
}

/**
 * The wall-clock date-time embedded in a server ISO timestamp
 * ("2026-08-26T09:00:00-04:00" → 9:00). Class times must render as the
 * studio's clock, never converted into the device zone; use this on surfaces
 * whose payload carries no location timezone (bookings).
 */
fun wallClock(iso: String): LocalDateTime {
    val core = when {
        iso.endsWith("Z") -> iso.dropLast(1)
        iso.length > 6 && iso[iso.length - 3] == ':' &&
            (iso[iso.length - 6] == '+' || iso[iso.length - 6] == '-') -> iso.dropLast(6)
        else -> iso
    }
    return LocalDateTime.parse(core)
}

enum class CapacityTier(val label: String) {
    // The class's Mariana Tek booking window hasn't opened yet — distinct from
    // FULL (the server reports 0 spots until the window opens, but it isn't full).
    NotOpen("NOT OPEN"),
    Full("FULL"),
    AlmostFull("ALMOST FULL"),
    FillingUp("FILLING UP"),
    Available("AVAILABLE"),
}

/**
 * Pure helper for the Schedule row overline + Detail availability block.
 *
 * When `publishesCapacity` is false the studio doesn't expose a real
 * capacity number (e.g. ID Hot Yoga on Mindbody). We collapse to binary
 * AVAILABLE / FULL — we can't show "FILLING UP" or "ALMOST FULL"
 * truthfully because we don't know what fraction is booked.
 */
fun computeCapacityTier(
    available: Int,
    offered: Int,
    publishesCapacity: Boolean,
    notOpen: Boolean = false,
): CapacityTier {
    // A not-open booking window wins over everything: the server zeroes spots
    // until it opens, so without this the row would mislabel as FULL.
    if (notOpen) return CapacityTier.NotOpen
    if (!publishesCapacity) {
        return if (available <= 0) CapacityTier.Full else CapacityTier.Available
    }
    return when {
        // <= 0 mirrors the defensive guard on `isFull` in ClassRow so over-
        // booked sessions (negative available) consistently render as Full
        // across label, color, and CTA.
        available <= 0 -> CapacityTier.Full
        available <= 2 -> CapacityTier.AlmostFull
        offered > 0 && available.toFloat() / offered <= 0.4f -> CapacityTier.FillingUp
        else -> CapacityTier.Available
    }
}
