package org.arcana.mobile.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ReviewBrandDto(val slug: String = "", val name: String = "")

@Serializable
data class ReviewLocationDto(val id: Int = 0, val name: String = "")

@Serializable
data class ReviewClassTypeDto(val key: String = "", val label: String = "")

@Serializable
data class ReviewInstructorDto(
    @SerialName("profile_id") val profileId: Int? = null,
    val name: String = "",
)

/** One review: a feed item, or the member's own (`booking_id` set). Items
 *  are anonymous by server contract; nothing names the member. */
@Serializable
data class ReviewDto(
    val id: Int,
    @SerialName("created_at") val createdAt: String = "",
    val brand: ReviewBrandDto = ReviewBrandDto(),
    val location: ReviewLocationDto = ReviewLocationDto(),
    @SerialName("class_type") val classType: ReviewClassTypeDto = ReviewClassTypeDto(),
    val instructor: ReviewInstructorDto? = null,
    val again: String = "",
    val intensity: Int? = null,
    @SerialName("instructor_score") val instructorScore: Int? = null,
    @SerialName("class_score") val classScore: Int? = null,
    @SerialName("studio_score") val studioScore: Int? = null,
    val comment: String = "",
    @SerialName("booking_id") val bookingId: Int? = null,
    @SerialName("updated_at") val updatedAt: String = "",
    @SerialName("prompt_surface") val promptSurface: String = "",
)

/** `review_prompt` on `GET bookings/me/?scope=upcoming`: the one class Home
 *  may ask about, with the words the card needs. */
@Serializable
data class ReviewPromptDto(
    @SerialName("booking_id") val bookingId: Int,
    @SerialName("class_session_id") val classSessionId: Int = 0,
    @SerialName("start_at") val startAt: String = "",
    @SerialName("end_at") val endAt: String = "",
    val brand: ReviewBrandDto = ReviewBrandDto(),
    val location: ReviewLocationDto = ReviewLocationDto(),
    @SerialName("class_type") val classType: ReviewClassTypeDto = ReviewClassTypeDto(),
    val instructor: ReviewInstructorDto? = null,
)

@Serializable
data class FeedbackAgainDto(val yes: Int = 0, val maybe: Int = 0, val no: Int = 0)

@Serializable
data class FeedbackScopeDto(
    val type: String = "all",
    val value: String = "",
    val label: String = "",
    /** Only on a location feed: the brand it is one lens on, to widen to. */
    val brand: FeedbackBrandDto? = null,
    val count: Int = 0,
    val again: FeedbackAgainDto = FeedbackAgainDto(),
    @SerialName("intensity_avg") val intensityAvg: Double? = null,
    @SerialName("instructor_avg") val instructorAvg: Double? = null,
    @SerialName("class_avg") val classAvg: Double? = null,
    @SerialName("studio_avg") val studioAvg: Double? = null,
)

@Serializable
data class FeedbackBrandDto(val slug: String = "", val name: String = "", val count: Int = 0)

/** `GET reviews/`: one keyset page, newest first, with the scope's stats. */
@Serializable
data class FeedbackFeedDto(
    val scope: FeedbackScopeDto = FeedbackScopeDto(),
    val items: List<ReviewDto> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String? = null,
)

@Serializable
data class DismissReviewPromptResponse(
    @SerialName("review_prompt") val reviewPrompt: ReviewPromptDto? = null,
)

/** Step one. Everything after it is a PATCH of the fields that changed. */
@Serializable
data class CreateReviewRequest(
    val again: String,
    @SerialName("prompt_surface") val promptSurface: String,
)

/** The directory's "Member feedback" row. */
@Serializable
data class DiscoverFeedbackDto(
    @SerialName("review_count") val reviewCount: Int = 0,
    val latest: DiscoverLatestFeedbackDto? = null,
)

@Serializable
data class DiscoverLatestFeedbackDto(
    val id: Int = 0,
    @SerialName("created_at") val createdAt: String = "",
    val comment: String = "",
    val brand: ReviewBrandDto = ReviewBrandDto(),
    @SerialName("class_type") val classType: ReviewClassTypeDto = ReviewClassTypeDto(),
)
