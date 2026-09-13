package org.arcana.mobile.booking

import kotlinx.datetime.LocalDate
import org.arcana.mobile.data.BookingDto
import org.arcana.mobile.data.CancelPolicyDto
import org.arcana.mobile.data.SessionBriefDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReservationGroupingTest {
    private val today = LocalDate(2026, 9, 9)

    private fun booking(id: Int, startAt: String) = BookingDto(
        id = id, status = "confirmed", spot = null,
        session = SessionBriefDto(id, startAt, startAt, "RUN", "Barry's"),
        cancelPolicy = CancelPolicyDto(false, null),
    )

    @Test fun `labels today and tomorrow then bare dates`() {
        assertEquals("Today · Wed Sep 9", dayHeaderLabel(LocalDate(2026, 9, 9), today))
        assertEquals("Tomorrow · Thu Sep 10", dayHeaderLabel(LocalDate(2026, 9, 10), today))
        assertEquals("Sat Sep 12", dayHeaderLabel(LocalDate(2026, 9, 12), today))
    }

    @Test fun `groups by the studio wall clock date not the UTC date`() {
        // 23:30 New York on the 9th is 03:30 UTC on the 10th; the header must say the 9th.
        val groups = groupReservationsByDay(listOf(booking(1, "2026-09-09T23:30:00-04:00")), today)
        assertEquals(1, groups.size)
        assertEquals(LocalDate(2026, 9, 9), groups[0].date)
        assertEquals("Today · Wed Sep 9", groups[0].label)
    }

    @Test fun `groups ascend by date and keep row order within a day`() {
        val groups = groupReservationsByDay(
            listOf(
                booking(3, "2026-09-12T07:00:00-04:00"),
                booking(1, "2026-09-09T18:00:00-04:00"),
                booking(2, "2026-09-09T06:00:00-04:00"),
            ),
            today,
        )
        assertEquals(listOf(LocalDate(2026, 9, 9), LocalDate(2026, 9, 12)), groups.map { it.date })
        assertEquals(listOf(1, 2), groups[0].bookings.map { it.id })
        assertEquals(listOf(3), groups[1].bookings.map { it.id })
    }

    @Test fun `an unparseable start keeps its row in a trailing group`() {
        val groups = groupReservationsByDay(
            listOf(booking(1, "2026-09-10T09:00:00-04:00"), booking(2, "garbage")),
            today,
        )
        assertEquals(2, groups.size)
        assertNull(groups[1].date)
        assertEquals(listOf(2), groups[1].bookings.map { it.id })
    }

    @Test fun `empty input yields no groups`() {
        assertEquals(emptyList(), groupReservationsByDay(emptyList(), today))
    }
}
