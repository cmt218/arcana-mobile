package org.arcana.mobile.schedule

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Class times render as the STUDIO's wall clock, never converted into the
 * device zone. Found 2026-08-25: a member in Costa Rica saw a 9:00 AM ET class
 * as 9:00 on the schedule but 7:00 on class detail, Home, and My Bookings —
 * every surface that converted the instant through the device timezone.
 */
class WallClockTest {

    @Test
    fun negative_offset_keeps_the_literal_wall_time() {
        val dt = wallClock("2026-08-26T09:00:00-04:00")
        assertEquals(9, dt.hour)
        assertEquals(0, dt.minute)
        assertEquals(26, dt.date.day)
    }

    @Test
    fun positive_offset_keeps_the_literal_wall_time() {
        val dt = wallClock("2026-08-26T18:30:00+05:30")
        assertEquals(18, dt.hour)
        assertEquals(30, dt.minute)
    }

    @Test
    fun zulu_suffix_parses_as_its_own_wall_time() {
        val dt = wallClock("2026-08-26T13:00:00Z")
        assertEquals(13, dt.hour)
    }

    @Test
    fun fractional_seconds_survive() {
        val dt = wallClock("2026-08-26T09:15:00.123456-04:00")
        assertEquals(9, dt.hour)
        assertEquals(15, dt.minute)
    }

    @Test
    fun offsetless_input_parses_as_is() {
        val dt = wallClock("2026-08-26T07:45:00")
        assertEquals(7, dt.hour)
        assertEquals(45, dt.minute)
    }
}
