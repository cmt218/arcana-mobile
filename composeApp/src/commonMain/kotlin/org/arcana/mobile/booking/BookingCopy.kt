package org.arcana.mobile.booking

/** Member-facing copy for a server booking-error reason code. Voice: direct,
 *  no fluff (brand "Sound of Arcana"). */
fun bookingErrorCopy(code: String): String = when (code) {
    "session_full" -> "This class just filled up."
    "credits_exhausted" -> "You're out of credits for this period."
    "already_booked" -> "You've already booked this class."
    // Server rejects a booking that overlaps a class the member already has
    // (requested/confirmed), cross-studio. Permanent conflict, not a transient
    // retry — say so plainly (no other class/studio named; concierge-safe).
    "time_conflict" -> "You already have a class booked at this time."
    "spot_required" -> "Pick a spot to book."
    "spot_unavailable" -> "That spot was just taken — pick another."
    "invalid_spot_preference" -> "That option isn't available — pick another."
    "session_outside_window" -> outsideWindowCopy(null)
    "no_active_payment" -> "No active membership. Reach out to concierge with any questions."
    "payment_past_due" -> "There's a payment issue on your membership."
    "booking_busy" -> "We're a little busy — try that again."
    else -> "We couldn't book that. Try again in a moment."
}

/** Popup when a class falls outside the member's covered month(s). [coveredMonths]
 *  is a phrase like "July" or "July and August"; null falls back to a generic line.
 *  Names no price and steers nowhere — concierge handles changes (app-store-safe). */
fun outsideWindowCopy(coveredMonths: String?): String =
    if (coveredMonths != null)
        "Your membership covers classes in $coveredMonths. Reach out to concierge with any questions."
    else
        "This class isn't covered by your membership. Reach out to concierge with any questions."
