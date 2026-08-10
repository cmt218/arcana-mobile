package org.arcana.mobile.booking

import kotlin.test.Test
import kotlin.test.assertEquals
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
}
