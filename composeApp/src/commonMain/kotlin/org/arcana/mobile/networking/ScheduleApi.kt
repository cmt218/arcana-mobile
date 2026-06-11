package org.arcana.mobile.networking

import kotlinx.datetime.LocalDate
import org.arcana.mobile.data.ScheduleSessionDto

/** Narrow interface over the schedule read endpoints — extracted so
 *  ScheduleViewModel is fakeable in commonTest. Phase 2 (pagination) extends
 *  this with overview + paged-session methods. */
interface ScheduleApi {
    suspend fun fetchSchedule(
        from: LocalDate,
        to: LocalDate,
        studioSlugs: List<String>? = null,
        locationIds: List<Int>? = null,
        modality: String? = null,
        availableOnly: Boolean = false,
    ): List<ScheduleSessionDto>
}
