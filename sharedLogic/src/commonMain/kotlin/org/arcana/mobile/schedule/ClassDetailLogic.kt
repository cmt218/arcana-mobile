package org.arcana.mobile.schedule

/** Sessions with <= 2 remaining spots are visually marked as "scarce". */
private const val SCARCE_THRESHOLD = 2

enum class DetailCapacity { Open, Scarce, Full, NotOpen }

/**
 * Pure helper for the Detail availability block. When `publishesCapacity`
 * is false we collapse Scarce into Open — for a studio that hides
 * capacity, a "1 spot left" signal is unreliable because we don't know
 * what fraction of the room is booked.
 *
 * A not-open Mariana Tek booking window wins over everything: the server
 * zeroes spots until it opens, so without this the detail block would
 * mislabel a not-open class as FULL.
 */
fun computeDetailCapacity(
    available: Int,
    publishesCapacity: Boolean,
    notOpen: Boolean = false,
): DetailCapacity {
    if (notOpen) return DetailCapacity.NotOpen
    if (!publishesCapacity) {
        return if (available <= 0) DetailCapacity.Full else DetailCapacity.Open
    }
    return when {
        available <= 0 -> DetailCapacity.Full
        available <= SCARCE_THRESHOLD -> DetailCapacity.Scarce
        else -> DetailCapacity.Open
    }
}
