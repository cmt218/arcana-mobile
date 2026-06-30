package org.arcana.mobile.networking

import kotlinx.datetime.LocalDate
import org.arcana.mobile.data.ScheduleOverviewDto
import org.arcana.mobile.data.SchedulePageDto
import org.arcana.mobile.data.ScheduleSessionDto

/** Narrow interface over the schedule read endpoints — extracted so
 *  ScheduleViewModel is fakeable in commonTest. */
interface ScheduleApi {
    suspend fun fetchSchedule(
        from: LocalDate,
        to: LocalDate,
        studioSlugs: List<String>? = null,
        locationIds: List<Int>? = null,
        categorySlugs: List<String>? = null,
        availableOnly: Boolean = false,
    ): List<ScheduleSessionDto>

    /** Chip-rail data for the window (the Partners + locations to render as
     *  filters). Filter-independent — the params are accepted for symmetry but
     *  the server builds the studios block from the unfiltered window. */
    suspend fun fetchOverview(
        from: LocalDate,
        to: LocalDate,
        studioSlugs: List<String>? = null,
        locationIds: List<Int>? = null,
        categorySlugs: List<String>? = null,
        availableOnly: Boolean = false,
    ): ScheduleOverviewDto

    /** One keyset page of [date]'s sessions (server scopes via from == to). */
    suspend fun fetchSessionsPage(
        date: LocalDate,
        studioSlugs: List<String>? = null,
        locationIds: List<Int>? = null,
        categorySlugs: List<String>? = null,
        availableOnly: Boolean = false,
        cursor: String? = null,
    ): SchedulePageDto
}
