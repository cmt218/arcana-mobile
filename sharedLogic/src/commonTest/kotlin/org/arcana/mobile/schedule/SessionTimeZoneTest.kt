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
}
