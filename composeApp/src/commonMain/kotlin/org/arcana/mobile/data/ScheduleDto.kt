package org.arcana.mobile.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Response shape for `GET /api/v1/classes/`. Mirrors the server contract at
 * `arcana-server/docs/superpowers/specs/2026-05-26-phase-3-classes-browse-design.md`
 * §3.3 verbatim — when the server changes, this DTO changes in lockstep.
 *
 * Datetime fields (`startAt`, `endAt`, `lastSuccessfulSyncAt`) arrive as
 * ISO-8601 strings with offset (e.g. `2026-05-26T11:15:00-04:00`). We keep
 * them as `String` here and parse to `Instant` at the consumer (via
 * `kotlin.time.Instant.parse(...)`). This avoids depending on a particular
 * serializer location that has churned between kotlinx-datetime versions.
 */
@Serializable
data class ScheduleSessionDto(
    val id: Int,
    @SerialName("start_at") val startAt: String,
    @SerialName("end_at") val endAt: String,
    @SerialName("duration_minutes") val durationMinutes: Int,
    val status: String,
    @SerialName("platform_capacity") val platformCapacity: Int,
    @SerialName("platform_booked") val platformBooked: Int,
    @SerialName("arcana_spots_offered") val arcanaSpotsOffered: Int,
    @SerialName("arcana_spots_available") val arcanaSpotsAvailable: Int,
    val template: TemplateBriefDto,
    val instructors: List<InstructorBriefDto>,
    val location: LocationBriefDto,
)

@Serializable
data class TemplateBriefDto(
    val id: Int,
    val name: String,
    val modality: String,
    @SerialName("hero_image_url") val heroImageUrl: String,
    @SerialName("spot_selection_mode") val spotSelectionMode: String,
    val description: String = "",
    @SerialName("layout_metadata") val layoutMetadata: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class InstructorBriefDto(
    val id: Int,
    val name: String,
    @SerialName("photo_url") val photoUrl: String,
)

@Serializable
data class LocationBriefDto(
    val id: Int,
    val name: String,
    val timezone: String,
    val studio: StudioBriefDto,
    val address: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
)

@Serializable
data class StudioBriefDto(
    val id: Int,
    val slug: String,
    val name: String,
    @SerialName("logo_url") val logoUrl: String,
    @SerialName("primary_color") val primaryColor: String,
    /** Null when the studio has never had a successful sync. Mobile renders a
     *  "schedule may be out of date" banner if this is more than 4h stale. */
    @SerialName("last_successful_sync_at") val lastSuccessfulSyncAt: String? = null,
)
