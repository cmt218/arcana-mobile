package org.arcana.mobile.networking

import kotlinx.datetime.LocalDate
import org.arcana.mobile.data.SchedulePageDto
import org.arcana.mobile.data.SearchEntitiesDto

/** Narrow interface over the search read endpoints — extracted so
 *  SearchViewModel is fakeable in commonTest. */
interface SearchApi {
    /** Chip data (matched studios + instructor names) for [query]. The server
     *  returns empty lists under 2 chars; callers debounce below that anyway. */
    suspend fun searchEntities(query: String): SearchEntitiesDto

    /** One keyset page of upcoming sessions in [from]..[to] matching [query]
     *  (tokenized AND across class/studio/instructor/location text).
     *  [instructor]/[studioSlug] are the chip-tap scopes; instructor is an
     *  exact name on purpose — Instructor rows are per studio site, a name
     *  spans a brand's rows. */
    suspend fun searchSessions(
        from: LocalDate,
        to: LocalDate,
        query: String? = null,
        instructor: String? = null,
        studioSlug: String? = null,
        locationId: Int? = null,
        cursor: String? = null,
    ): SchedulePageDto
}
