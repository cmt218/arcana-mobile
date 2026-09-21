package org.arcana.mobile.schedule

import org.arcana.mobile.data.ReviewDto

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

/** What class detail shows about reviews. */
enum class ReviewPlacement {
    None,
    /** The member's own review of this combination, compact, with a way into editing it. */
    Summary,
    /** A class the member attended and has not reviewed: step one of the card. */
    Prompt,
}

data class ClassDetailReviews(
    val review: ReviewPlacement,
    /** The booking the card reads and writes through; null with [ReviewPlacement.None]. */
    val bookingId: Int?,
    /** Whether the reserve control (book, cancel) stays on screen. */
    val showsReserveControl: Boolean,
)

/**
 * `my_review` rides on EVERY session of a reviewed combination, upcoming ones
 * included, so only a class that has ENDED gives the reserve control's place to
 * the review. Otherwise a reviewed class could be neither booked nor cancelled.
 */
fun classDetailReviewPlacement(
    myReview: ReviewDto?,
    promptEligible: Boolean,
    reviewBookingId: Int?,
    isPast: Boolean,
): ClassDetailReviews {
    val placement = when {
        myReview != null -> ReviewPlacement.Summary
        promptEligible && reviewBookingId != null && isPast -> ReviewPlacement.Prompt
        else -> ReviewPlacement.None
    }
    return ClassDetailReviews(
        review = placement,
        bookingId = if (placement == ReviewPlacement.None) null else myReview?.bookingId ?: reviewBookingId,
        showsReserveControl = placement == ReviewPlacement.None || !isPast,
    )
}
