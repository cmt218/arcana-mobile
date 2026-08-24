package org.arcana.mobile.schedule

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * Precedence and copy for the sticky class-detail CTA.
 *
 * The case that motivated pulling this out of the Composable: a direct-
 * integration studio confirms the booking inside the create call, so the
 * member sees "CONFIRMED ✓" the instant they tap. "REQUESTED" is a manual-
 * fulfilment concept and must not leak onto a direct studio's class.
 */
class ClassDetailCtaLabelTest {
    private val opensAt = Instant.parse("2026-06-22T15:00:00Z")

    private fun label(
        isPast: Boolean = false,
        justBooked: Boolean = false,
        bookingStatus: String? = null,
        outsideWindow: Boolean = false,
        opensAt: Instant? = null,
        fallback: String = "BOOK THIS CLASS",
    ) = classDetailCtaLabel(isPast, justBooked, bookingStatus, outsideWindow, opensAt, fallback)

    // --- just booked -------------------------------------------------------

    @Test fun just_booked_at_a_direct_studio_reads_confirmed() {
        assertEquals("CONFIRMED ✓", label(justBooked = true, bookingStatus = "confirmed"))
    }

    @Test fun just_booked_at_a_manual_studio_reads_requested() {
        assertEquals("REQUESTED ✓", label(justBooked = true, bookingStatus = "requested"))
    }

    /** Defensive: never assert a fulfilment state we didn't get told. */
    @Test fun just_booked_with_no_status_reads_booked() {
        assertEquals("BOOKED ✓", label(justBooked = true, bookingStatus = null))
    }

    @Test fun just_booked_with_an_unexpected_status_reads_booked() {
        assertEquals("BOOKED ✓", label(justBooked = true, bookingStatus = "unfulfilled"))
    }

    // --- return visit ------------------------------------------------------

    /** The ✓ means "you just did this", so it must not survive a re-entry. */
    @Test fun return_visit_drops_the_check_on_a_pending_booking() {
        assertEquals("REQUESTED", label(bookingStatus = "requested"))
    }

    @Test fun return_visit_keeps_the_check_on_a_confirmed_booking() {
        assertEquals("CONFIRMED ✓", label(bookingStatus = "confirmed"))
    }

    @Test fun return_visit_uppercases_any_other_status() {
        assertEquals("COMPLETED", label(bookingStatus = "completed"))
    }

    // --- precedence --------------------------------------------------------

    @Test fun past_outranks_everything() {
        assertEquals(
            "CLASS ENDED",
            label(isPast = true, justBooked = true, bookingStatus = "confirmed", outsideWindow = true, opensAt = opensAt),
        )
    }

    /** Holding a booking outranks both gates: the member already got in. */
    @Test fun a_held_booking_outranks_the_window_and_the_membership_gap() {
        assertEquals("CONFIRMED ✓", label(bookingStatus = "confirmed", outsideWindow = true, opensAt = opensAt))
    }

    /** Even once booking opens, July credits still don't cover an August class. */
    @Test fun membership_gap_outranks_the_not_open_window() {
        assertEquals("OUTSIDE YOUR MEMBERSHIP", label(outsideWindow = true, opensAt = opensAt))
    }

    @Test fun not_open_yet_shows_when_it_opens() {
        assertEquals("OPENS MON 11:00 AM ET", label(opensAt = opensAt))
    }

    @Test fun falls_back_to_the_eligibility_label() {
        assertEquals("NO ACTIVE MEMBERSHIP", label(fallback = "NO ACTIVE MEMBERSHIP"))
    }
}
