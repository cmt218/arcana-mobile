package org.arcana.mobile.booking

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class BookingCopyTest {
    @Test fun `known codes get friendly copy`() {
        assertEquals("This class just filled up.", bookingErrorCopy("session_full"))
        assertEquals("You're out of credits for this period.", bookingErrorCopy("credits_exhausted"))
        assertEquals("You've already booked this class.", bookingErrorCopy("already_booked"))
        assertEquals("You already have a class booked at this time.", bookingErrorCopy("time_conflict"))
        assertEquals("This class was cancelled by the studio.", bookingErrorCopy("class_cancelled"))
    }
    @Test fun `unknown code gets a generic fallback`() {
        assertTrue(bookingErrorCopy("some_new_code").isNotBlank())
    }

    // ── CONNECTION/SERVER conflation (ERR-11) ---------------------------------

    @Test fun `connection and server booking copy are distinct and neither blames the wrong party`() {
        val connection = bookingErrorCopy("connection_failed")
        val server = bookingErrorCopy("server_failed")
        assertNotEquals(connection, server)
        assertFalse(connection.contains("our end", ignoreCase = true))
        assertFalse(connection.contains("server", ignoreCase = true))
    }

    // ERR-11 shipped broken specifically because a 5xx with no reason code
    // collapsed into the SAME code ("booking_failed") an ordinary unrecognized
    // 4xx already used, which routes through bookingErrorCopy's `else` branch
    // to the generic fallback. Pin all three codes apart by their copy so that
    // regression can't quietly return: connection_failed and server_failed
    // must each read as their own category, and neither may coincide with
    // whatever booking_failed happens to render (today the same generic
    // fallback "unknown code" also renders, by design).
    @Test fun `server and connection booking copy never fall back to the generic booking_failed line`() {
        val genericFallback = bookingErrorCopy("booking_failed")
        assertNotEquals(genericFallback, bookingErrorCopy("server_failed"))
        assertNotEquals(genericFallback, bookingErrorCopy("connection_failed"))
    }

    @Test fun `no booking copy contains an em or en dash`() {
        val codes = listOf(
            "session_full", "credits_exhausted", "already_booked", "class_cancelled",
            "time_conflict", "spot_required", "spot_unavailable",
            "invalid_spot_preference", "session_outside_window", "no_active_payment",
            "payment_past_due", "booking_busy", "connection_failed", "server_failed",
            "cancel_failed", "unknown_code",
        )
        codes.forEach { code ->
            val copy = bookingErrorCopy(code)
            assertFalse(copy.contains('—'), "em dash in copy for $code: $copy")
            assertFalse(copy.contains('–'), "en dash in copy for $code: $copy")
        }
    }

    // ── cancelErrorCopy: same table, cancel-flavoured fallback ---------------

    @Test fun `cancelErrorCopy delegates known codes unchanged`() {
        assertEquals(bookingErrorCopy("connection_failed"), cancelErrorCopy("connection_failed"))
        assertEquals(bookingErrorCopy("server_failed"), cancelErrorCopy("server_failed"))
    }

    @Test fun `cancelErrorCopy overrides only the unrecognized-code fallback`() {
        val fallback = cancelErrorCopy("some_new_cancel_code")
        assertEquals("Couldn't cancel. Try again.", fallback)
        assertFalse(fallback.contains("book"), "cancel fallback must not use booking-flavoured copy")
    }
}
