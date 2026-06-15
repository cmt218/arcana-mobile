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
)

@Serializable
data class MyBookingsDto(
    val upcoming: List<BookingDto>,
    val past: List<BookingDto>,
)

@Serializable
data class CreateBookingRequest(
    @SerialName("session_id") val sessionId: Int,
    @SerialName("requested_spot_id") val requestedSpotId: Int? = null,
    // The one-time "have you been to this studio before?" answer, when asked.
    // Null (omitted) when not asked — the server records it on the user↔studio
    // relationship, never on the booking.
    @SerialName("studio_visited_before") val studioVisitedBefore: Boolean? = null,
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
