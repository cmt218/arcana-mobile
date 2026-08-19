package org.arcana.mobile.booking

import org.arcana.mobile.networking.CONNECTION_FAILED
import org.arcana.mobile.networking.SERVER_FAILED
import org.arcana.mobile.networking.transportErrorCopy

/** Fallback for an unrecognized code; also [cancelErrorCopy]'s swap sentinel. */
private const val UNRECOGNIZED_BOOKING_CODE_COPY = "We couldn't book that. Try again in a moment."

/** Member-facing copy for a server booking-error reason code. Voice: direct,
 *  no fluff (brand "Sound of Arcana"). */
fun bookingErrorCopy(code: String): String = when (code) {
    "session_full" -> "This class just filled up."
    "credits_exhausted" -> "You're out of credits for this period."
    "already_booked" -> "You've already booked this class."
    // The studio cancelled/removed this class between browse and booking. Not a
    // retry — the class is gone. (Older app builds fall through to the generic
    // line below, which is graceful, just less specific.)
    "class_cancelled" -> "This class was cancelled by the studio."
    // Server rejects a booking that overlaps a class the member already has
    // (requested/confirmed), cross-studio. Permanent conflict, not a transient
    // retry — say so plainly (no other class/studio named; concierge-safe).
    "time_conflict" -> "You already have a class booked at this time."
    "spot_required" -> "Pick a spot to book."
    "spot_unavailable" -> "That spot was just taken. Pick another."
    "invalid_spot_preference" -> "That option isn't available. Pick another."
    "session_outside_window" -> outsideWindowCopy(null)
    "no_active_payment" -> "No active membership. Reach out to concierge with any questions."
    "payment_past_due" -> "There's a payment issue on your membership."
    "booking_busy" -> "We're a little busy. Try that again."
    // Kept distinct from the generic fallback: "try again in a moment" is
    // wrong advice when the phone itself is offline.
    CONNECTION_FAILED, SERVER_FAILED -> transportErrorCopy(code)!!
    // No current producer (confirmCancel only emits connection/server_failed
    // above) — kept as a back-compat net, not dead code.
    "cancel_failed" -> "Couldn't cancel. Try again."
    else -> UNRECOGNIZED_BOOKING_CODE_COPY
}

/** Cancel-flow copy: same table as [bookingErrorCopy], but swaps the
 *  *fallback* so an unmapped code still reads as a cancel failure rather
 *  than a booking one. */
fun cancelErrorCopy(code: String): String =
    bookingErrorCopy(code).let { if (it == UNRECOGNIZED_BOOKING_CODE_COPY) "Couldn't cancel. Try again." else it }

/** Popup when a class falls outside the member's covered month(s). [coveredMonths]
 *  is a phrase like "July" or "July and August"; null falls back to a generic line.
 *  Names no price and steers nowhere — concierge handles changes (app-store-safe). */
fun outsideWindowCopy(coveredMonths: String?): String =
    if (coveredMonths != null)
        "Your membership covers classes in $coveredMonths. Reach out to concierge with any questions."
    else
        "This class isn't covered by your membership. Reach out to concierge with any questions."
