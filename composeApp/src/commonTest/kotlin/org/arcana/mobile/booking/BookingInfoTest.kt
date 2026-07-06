package org.arcana.mobile.booking

import org.arcana.mobile.data.BookingDto
import org.arcana.mobile.data.CancelPolicyDto
import org.arcana.mobile.data.SessionBriefDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BookingInfoTest {
    private fun booking(note: String?) = BookingDto(
        id = 1, status = "confirmed",
        session = SessionBriefDto(
            id = 1, startAt = "2026-07-07T10:00:00Z", endAt = "2026-07-07T10:50:00Z",
            name = "RUN x LIFT", studio = "Barry's",
        ),
        cancelPolicy = CancelPolicyDto(willForfeitCredit = false),
        memberNote = note,
    )

    @Test
    fun `returns trimmed note when present`() {
        assertEquals("Door code 1234", bookingInfoOrNull(booking("  Door code 1234  ")))
    }

    @Test
    fun `null when null, blank, or whitespace`() {
        assertNull(bookingInfoOrNull(booking(null)))
        assertNull(bookingInfoOrNull(booking("")))
        assertNull(bookingInfoOrNull(booking("   ")))
        assertNull(bookingInfoOrNull(null))
    }
}
