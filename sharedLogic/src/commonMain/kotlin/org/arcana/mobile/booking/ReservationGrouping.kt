package org.arcana.mobile.booking

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.arcana.mobile.data.BookingDto
import org.arcana.mobile.schedule.wallClock

data class ReservationDayGroup(
    val date: LocalDate?,
    val label: String,
    val bookings: List<BookingDto>,
)

/**
 * Groups reservations under day headers in the studio's wall clock:
 * "Today · Wed Sep 9", "Tomorrow · Thu Sep 10", then "Sat Sep 12". Groups are
 * ascending by date; rows keep their incoming order within a day. A row whose
 * start time will not parse becomes its own trailing group labelled with the
 * raw date so nothing silently disappears.
 */
fun groupReservationsByDay(bookings: List<BookingDto>, today: LocalDate): List<ReservationDayGroup> {
    val byDate = LinkedHashMap<LocalDate, MutableList<BookingDto>>()
    val unparsed = mutableListOf<BookingDto>()
    for (b in bookings) {
        val date = runCatching { wallClock(b.session.startAt).date }.getOrNull()
        if (date == null) unparsed += b else byDate.getOrPut(date) { mutableListOf() } += b
    }
    val dated = byDate.entries
        .sortedBy { it.key }
        .map { (date, rows) -> ReservationDayGroup(date, dayHeaderLabel(date, today), rows) }
    val trailing = unparsed.map { ReservationDayGroup(null, it.session.startAt.take(10), listOf(it)) }
    return dated + trailing
}

fun dayHeaderLabel(date: LocalDate, today: LocalDate): String {
    val dow = date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.titlecase() }
    val mon = date.month.name.take(3).lowercase().replaceFirstChar { it.titlecase() }
    val base = "$dow $mon ${date.day}"
    return when (date) {
        today -> "Today · $base"
        today.plus(1, DateTimeUnit.DAY) -> "Tomorrow · $base"
        else -> base
    }
}
