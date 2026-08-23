package org.arcana.mobile.booking

import org.arcana.mobile.data.CurrentPeriodDto

/** Coarse Book-button state. Order of precedence matters (see bookCtaState). */
enum class BookCta(val label: String, val enabled: Boolean) {
    Bookable("BOOK THIS CLASS", true),
    AlreadyBooked("ALREADY BOOKED", false),
    Full("CLASS FULL", false),
    OutOfCredits("OUT OF CREDITS", false),
    // No active/usable membership (no current period, or the period can't book).
    // The class-detail CTA renders this as a single clear line (no time sub-stamp),
    // matching the Home tile's "No active membership."
    NotBookable("NO ACTIVE MEMBERSHIP", false),
    // We have not established anything yet: the membership fetch has not
    // returned, or it failed with nothing previously known. Distinct from
    // NotBookable, which is a claim about the account. Without this the enum
    // had no way to say "unknown", so a failed cold fetch fell through to
    // NotBookable and told a paying member they had no membership.
    Unknown("BOOKING UNAVAILABLE", false),
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
