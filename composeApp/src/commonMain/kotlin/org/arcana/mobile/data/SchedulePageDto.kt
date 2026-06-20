package org.arcana.mobile.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `GET /api/v1/classes/overview/` — the window's Partner/location block that
 *  feeds the filter chip rails. Filter-independent (the server builds it from
 *  the window-only queryset, so chips never vanish under narrowing). Contract:
 *  docs/superpowers/specs/2026-06-10-schedule-loading-design.md. */
@Serializable
data class ScheduleOverviewDto(
    val studios: List<OverviewStudioDto> = emptyList(),
)

@Serializable
data class OverviewStudioDto(
    val id: Int,
    val slug: String,
    val name: String,
    @SerialName("logo_url") val logoUrl: String = "",
    @SerialName("primary_color") val primaryColor: String = "",
    @SerialName("publishes_capacity") val publishesCapacity: Boolean = true,
    @SerialName("last_successful_sync_at") val lastSuccessfulSyncAt: String? = null,
    val locations: List<OverviewLocationDto> = emptyList(),
)

@Serializable
data class OverviewLocationDto(val id: Int, val name: String, val timezone: String = "")

/** `GET /api/v1/classes/sessions/` — one keyset page, normalized: sessions
 *  reference templates/locations/studios/instructors by id; the lookup maps
 *  (string keys — JSON object keys) carry each referenced object once. */
@Serializable
data class SchedulePageDto(
    val sessions: List<SessionFlatDto> = emptyList(),
    val templates: Map<String, TemplateBriefDto> = emptyMap(),
    val locations: Map<String, LocationFlatDto> = emptyMap(),
    val studios: Map<String, StudioBriefDto> = emptyMap(),
    val instructors: Map<String, InstructorBriefDto> = emptyMap(),
    @SerialName("next_cursor") val nextCursor: String? = null,
)

@Serializable
data class SessionFlatDto(
    val id: Int,
    @SerialName("start_at") val startAt: String,
    @SerialName("end_at") val endAt: String,
    @SerialName("duration_minutes") val durationMinutes: Int,
    val status: String,
    @SerialName("platform_capacity") val platformCapacity: Int,
    @SerialName("platform_booked") val platformBooked: Int,
    @SerialName("arcana_spots_offered") val arcanaSpotsOffered: Int,
    @SerialName("arcana_spots_available") val arcanaSpotsAvailable: Int,
    @SerialName("template_id") val templateId: Int,
    @SerialName("location_id") val locationId: Int,
    @SerialName("instructor_ids") val instructorIds: List<Int> = emptyList(),
    // Mariana Tek per-class booking window — see ScheduleSessionDto.bookableAt.
    // Defaulted so older/again-shared payloads keep deserializing.
    @SerialName("bookable_at") val bookableAt: String? = null,
)

@Serializable
data class LocationFlatDto(
    val id: Int,
    val name: String,
    val timezone: String,
    @SerialName("studio_id") val studioId: Int,
)
