package org.arcana.mobile.booking

import org.arcana.mobile.data.BookingDto

/** The member-facing booking note, or null when there is nothing to show.
 *  The single gate every Booking-info UI surface uses. */
fun bookingInfoOrNull(booking: BookingDto?): String? =
    booking?.memberNote?.trim()?.takeIf { it.isNotEmpty() }
