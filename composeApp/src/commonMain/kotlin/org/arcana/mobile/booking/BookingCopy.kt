package org.arcana.mobile.booking

/** Member-facing copy for a server booking-error reason code. Voice: direct,
 *  no fluff (brand "Sound of Arcana"). */
fun bookingErrorCopy(code: String): String = when (code) {
    "session_full" -> "This class just filled up."
    "credits_exhausted" -> "You're out of credits for this period."
    "already_booked" -> "You've already booked this class."
    "spot_required" -> "Pick a spot to book."
    "spot_unavailable" -> "That spot was just taken — pick another."
    "no_active_payment" -> "Your membership isn't active for this date."
    "payment_past_due" -> "There's a payment issue on your membership."
    "booking_busy" -> "We're a little busy — try that again."
    else -> "We couldn't book that. Try again in a moment."
}
