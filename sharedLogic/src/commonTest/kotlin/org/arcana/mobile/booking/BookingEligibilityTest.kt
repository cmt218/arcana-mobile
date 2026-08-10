package org.arcana.mobile.booking

import org.arcana.mobile.data.CurrentPeriodDto
import kotlin.test.Test
import kotlin.test.assertEquals

class BookingEligibilityTest {
    private fun period(remaining: Int, canBook: Boolean = true) =
        CurrentPeriodDto(1, 30, 30 - remaining, remaining, canBrowse = true, canBook = canBook)

    @Test fun `bookable when spots and credits and canBook`() {
        assertEquals(BookCta.Bookable, bookCtaState(spotsAvailable = 5, period = period(10), alreadyBooked = false))
    }
    @Test fun `full when no spots`() {
        assertEquals(BookCta.Full, bookCtaState(spotsAvailable = 0, period = period(10), alreadyBooked = false))
    }
    @Test fun `already booked wins`() {
        assertEquals(BookCta.AlreadyBooked, bookCtaState(spotsAvailable = 5, period = period(10), alreadyBooked = true))
    }
    @Test fun `out of credits when zero remaining`() {
        assertEquals(BookCta.OutOfCredits, bookCtaState(spotsAvailable = 5, period = period(0), alreadyBooked = false))
    }
    @Test fun `cannot book when canBook false`() {
        assertEquals(BookCta.NotBookable, bookCtaState(spotsAvailable = 5, period = period(10, canBook = false), alreadyBooked = false))
    }
    @Test fun `no membership period is not bookable`() {
        assertEquals(BookCta.NotBookable, bookCtaState(spotsAvailable = 5, period = null, alreadyBooked = false))
    }
}
