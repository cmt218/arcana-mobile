package org.arcana.mobile.schedule

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Bounds logic for swipe-to-navigate days (the gesture plumbing is in
 *  ScheduleScreen; this pins the pure index math). */
class DayAfterSwipeTest {
    private val days = listOf(
        LocalDate(2026, 6, 11),
        LocalDate(2026, 6, 12),
        LocalDate(2026, 6, 13),
    )

    @Test fun `forward swipe advances one day`() {
        assertEquals(LocalDate(2026, 6, 12), dayAfterSwipe(days, days[0], forward = true))
    }

    @Test fun `backward swipe steps back one day`() {
        assertEquals(LocalDate(2026, 6, 12), dayAfterSwipe(days, days[2], forward = false))
    }

    @Test fun `forward swipe at the last day is a no-op`() {
        assertNull(dayAfterSwipe(days, days.last(), forward = true))
    }

    @Test fun `backward swipe at the first day is a no-op`() {
        assertNull(dayAfterSwipe(days, days.first(), forward = false))
    }

    @Test fun `unknown selected date yields null`() {
        assertNull(dayAfterSwipe(days, LocalDate(2030, 1, 1), forward = true))
    }
}
