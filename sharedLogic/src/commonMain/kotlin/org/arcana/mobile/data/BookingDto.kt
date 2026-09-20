package org.arcana.mobile.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SpotDto(
    val id: Int,
    val label: String,
    @SerialName("external_spot_id") val externalSpotId: String = "",
    @SerialName("position_x") val positionX: Double? = null,
    @SerialName("position_y") val positionY: Double? = null,
    val tier: String = "",
    val status: String = "available",
)

@Serializable
data class SessionBriefDto(
    val id: Int,
    @SerialName("start_at") val startAt: String,
    @SerialName("end_at") val endAt: String,
    val name: String,
    val studio: String,
    val location: String? = null,
    val instructor: String? = null,
    @SerialName("brand_slug") val brandSlug: String? = null,
    @SerialName("studio_slug") val studioSlug: String? = null,
    @SerialName("location_id") val locationId: Int? = null,
    @SerialName("location_address") val locationAddress: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("class_type_key") val classTypeKey: String? = null,
    @SerialName("instructor_profile_id") val instructorProfileId: Int? = null,
)

@Serializable
data class CancelPolicyDto(
    @SerialName("will_forfeit_credit") val willForfeitCredit: Boolean,
    @SerialName("cutoff_at") val cutoffAt: String? = null,
)

@Serializable
data class BookingDto(
    val id: Int,
    val status: String,
    @SerialName("requested_spot") val requestedSpot: SpotDto? = null,
    @SerialName("fulfilled_spot") val fulfilledSpot: SpotDto? = null,
    val spot: SpotDto? = null,
    val session: SessionBriefDto,
    @SerialName("cancel_policy") val cancelPolicy: CancelPolicyDto,
    // The chosen static spot *preference* (e.g. "Bag"), DISTINCT from the
    // requested/fulfilled SpotDto above. Null/absent when not applicable.
    @SerialName("spot_preference") val spotPreference: String? = null,
    // Member-facing note ops attaches (e.g. a door code). Null/absent when none.
    // Defaulted so older server responses (no field) still deserialize.
    @SerialName("member_note") val memberNote: String? = null,
    // "studio" when the studio cancelled the class; "" otherwise. Only the
    // scoped upcoming list surfaces studio-cancelled rows.
    @SerialName("cancelled_by") val cancelledBy: String = "",
    // none | reviewed_here | reviewed_elsewhere | ineligible. "none" means
    // this booking can take a review and its combination has none yet.
    @SerialName("review_state") val reviewState: String = "ineligible",
) {
    val cancelledByStudio: Boolean get() = status == "cancelled" && cancelledBy == "studio"
    val canReview: Boolean get() = reviewState == "none"
    val reviewed: Boolean get() = reviewState == "reviewed_here" || reviewState == "reviewed_elsewhere"
}

@Serializable
data class MyBookingsDto(
    val upcoming: List<BookingDto>,
    val past: List<BookingDto>,
)

/** `GET bookings/me/?scope=upcoming`: live reservations plus studio-cancelled
 *  ones until class time. */
@Serializable
data class MyUpcomingDto(
    val upcoming: List<BookingDto>,
    @SerialName("review_prompt") val reviewPrompt: ReviewPromptDto? = null,
)

/** `GET bookings/me/?scope=past`: one keyset page, newest first. */
@Serializable
data class MyPastDto(
    val past: List<BookingDto>,
    @SerialName("next_cursor") val nextCursor: String? = null,
)

@Serializable
data class CreateBookingRequest(
    @SerialName("session_id") val sessionId: Int,
    @SerialName("requested_spot_id") val requestedSpotId: Int? = null,
    // The one-time "have you been to this studio before?" answer, when asked.
    // Null (omitted) when not asked — the server records it on the user↔studio
    // relationship, never on the booking.
    @SerialName("studio_visited_before") val studioVisitedBefore: Boolean? = null,
    // Chosen static spot preference (e.g. "Bag"). Null/absent when not
    // applicable — the server treats null/absent as "".
    @SerialName("spot_preference") val spotPreference: String? = null,
)

@Serializable
data class CreateBookingResponse(
    @SerialName("booking_id") val bookingId: Int,
    val status: String,
)

@Serializable
data class CancelBookingResponse(
    val status: String,
    @SerialName("credit_refunded") val creditRefunded: Boolean,
    @SerialName("late_cancel") val lateCancel: Boolean,
)
