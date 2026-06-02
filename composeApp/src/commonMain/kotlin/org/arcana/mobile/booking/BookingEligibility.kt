package org.arcana.mobile.booking

import org.arcana.mobile.data.CurrentPeriodDto

/** Coarse Book-button state. Order of precedence matters (see bookCtaState). */
enum class BookCta(val label: String, val enabled: Boolean) {
    Bookable("BOOK THIS CLASS", true),
    AlreadyBooked("ALREADY BOOKED", false),
    Full("CLASS FULL", false),
    OutOfCredits("OUT OF CREDITS", false),
    NotBookable("NOT AVAILABLE", false),
}

/** Derive the CTA state from session availability + the member's current period.
 *  Precedence: already-booked > full > no-period/can't-book > out-of-credits > bookable. */
fun bookCtaState(spotsAvailable: Int, period: CurrentPeriodDto?, alreadyBooked: Boolean): BookCta = when {
    alreadyBooked -> BookCta.AlreadyBooked
    spotsAvailable <= 0 -> BookCta.Full
    period == null || !period.canBook -> BookCta.NotBookable
    period.creditsRemaining <= 0 -> BookCta.OutOfCredits
    else -> BookCta.Bookable
}
