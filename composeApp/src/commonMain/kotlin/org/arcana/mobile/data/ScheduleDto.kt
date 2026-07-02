package org.arcana.mobile.data

import kotlin.time.Instant
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
    val spots: List<SpotDto> = emptyList(),
    // Detail-only: true when we haven't yet asked this member whether they've
    // been to this studio (brand) before. Defaulted so list responses (which
    // omit it) still deserialize. Drives the one-time booking-time prompt.
    @SerialName("should_ask_studio_visit") val shouldAskStudioVisit: Boolean = false,
    // Mariana Tek per-class booking window: the ISO-8601 instant the studio
    // opens reservations for this class. Null = no window / always open (all
    // Mindbody, Arketa, ClubReady, and windowless MT classes). When this is in
    // the future the server reports `arcana_spots_available = 0` and rejects the
    // booking; the client renders "NOT OPEN" (see [isNotOpenYet]). Defaulted so
    // older/again-shared payloads keep deserializing.
    @SerialName("bookable_at") val bookableAt: String? = null,
)

/**
 * True when a class's Mariana Tek booking window has not opened yet — i.e.
 * [bookableAt] parses to an instant strictly after [now]. A null window (always
 * open) or a past window ⇒ false. Parse failures **fail open** (return false):
 * a malformed value must never hide a class that may be bookable.
 *
 * Pure + top-level so the Schedule row, Class detail, and unit tests share one
 * definition of "not open yet".
 */
fun isNotOpenYet(bookableAt: String?, now: Instant): Boolean {
    val iso = bookableAt ?: return false
    val opensAt = try {
        Instant.parse(iso)
    } catch (_: IllegalArgumentException) {
        return false
    }
    return now < opensAt
}

@Serializable
data class TemplateBriefDto(
    val id: Int,
    val name: String,
    val modality: String,
    @SerialName("hero_image_url") val heroImageUrl: String,
    @SerialName("spot_selection_mode") val spotSelectionMode: String,
    val description: String = "",
    @SerialName("layout_metadata") val layoutMetadata: JsonObject = JsonObject(emptyMap()),
    // Static, per-class-type spot *preference* options shown as a booking-time
    // dropdown (e.g. ["Bag","Bench"]). DISTINCT from real spot selection
    // (`spotSelectionMode`/`SpotDto`) — the chosen value rides along on the
    // booking as free text. Defaulted so payloads without them deserialize.
    @SerialName("spot_preference_options") val spotPreferenceOptions: List<String> = emptyList(),
    @SerialName("spot_preference_label") val spotPreferenceLabel: String? = null,
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
    /** False for studios (e.g. ID Hot Yoga) that don't publish real class
     *  capacity through their platform API — their own first-party app also
     *  hides it. Schedule + Detail collapse to binary AVAILABLE / FULL when
     *  this is false, instead of the four-tier label and "N of M spots open"
     *  count. Default true so older server responses (and most studios) keep
     *  the precise UI. */
    @SerialName("publishes_capacity") val publishesCapacity: Boolean = true,
    /** Resolved late-cancel window in minutes (per-studio override or the
     *  platform default). Drives the booking sheet's "free to cancel up to N
     *  hours before class" copy. Null only when talking to an older server that
     *  predates the field — the sheet falls back to generic copy in that case. */
    @SerialName("late_cancel_cutoff_minutes") val lateCancelCutoffMinutes: Int? = null,
)
