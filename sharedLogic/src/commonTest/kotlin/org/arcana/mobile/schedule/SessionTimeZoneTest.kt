package org.arcana.mobile.schedule

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.TimeZone

/** [sessionTimeZone] resolves a session's IANA timezone id for display and
 *  falls back to the schedule anchor timezone on an unknown id instead of
 *  letting [kotlinx.datetime.IllegalTimeZoneException] crash the screen. */
class SessionTimeZoneTest {

    @Test
    fun validIanaIdResolves() {
        assertEquals(TimeZone.of("America/Chicago"), sessionTimeZone("America/Chicago"))
    }

    @Test
    fun scheduleAnchorIdResolvesToItself() {
        assertEquals(ScheduleViewModel.ScheduleTimeZone, sessionTimeZone("America/New_York"))
    }

    @Test
    fun unknownIdFallsBackToScheduleTimeZone() {
        assertEquals(ScheduleViewModel.ScheduleTimeZone, sessionTimeZone("Not/A_Real_Zone"))
    }

    @Test
    fun emptyIdFallsBackToScheduleTimeZone() {
        assertEquals(ScheduleViewModel.ScheduleTimeZone, sessionTimeZone(""))
    }

    // Resolution is memoized (the Schedule resolves one id per row, thousands of
    // times per scroll). These lock the two ways a cache could go wrong: a repeat
    // hit must not drift, and a fallback must not be cached as a real zone.

    @Test
    fun repeatedResolutionIsStable() {
        val first = sessionTimeZone("America/Chicago")
        repeat(3) { assertEquals(first, sessionTimeZone("America/Chicago")) }
        assertEquals(TimeZone.of("America/Chicago"), first)
    }

    @Test
    fun repeatedUnknownIdKeepsFallingBack() {
        repeat(3) {
            assertEquals(ScheduleViewModel.ScheduleTimeZone, sessionTimeZone("Not/A_Real_Zone"))
        }
    }

    @Test
    fun distinctIdsDoNotCollide() {
        assertEquals(TimeZone.of("Europe/London"), sessionTimeZone("Europe/London"))
        assertEquals(TimeZone.of("America/Chicago"), sessionTimeZone("America/Chicago"))
        assertEquals(TimeZone.of("Europe/London"), sessionTimeZone("Europe/London"))
    }
}
