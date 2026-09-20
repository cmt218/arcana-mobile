package org.arcana.mobile.networking

import kotlinx.serialization.json.JsonObject
import org.arcana.mobile.data.FeedbackFeedDto
import org.arcana.mobile.data.ReviewDto
import org.arcana.mobile.data.ReviewPromptDto

/** A named refusal from the review endpoints (`not_eligible`, `review_not_found`, …). */
class ReviewError(val code: String) : Exception(code)

interface ReviewApi {
    /** Step one. A 200 (the combination was already reviewed on another
     *  booking) and a 201 both hand back the review to keep editing. */
    suspend fun createReview(bookingId: Int, again: String, promptSurface: String): ReviewDto

    /** Any subset of again / intensity / instructor_score / class_score /
     *  studio_score / comment, as a JSON object so a null clears a field. */
    suspend fun updateReview(bookingId: Int, fields: JsonObject): ReviewDto

    /** "Not now": answers with the next prompt, if any. */
    suspend fun dismissReviewPrompt(bookingId: Int): ReviewPromptDto?

    /** [scopeType] is all | brand | location | class_type | instructor;
     *  [scopeValue] is empty for `all`. */
    suspend fun fetchFeedback(
        scopeType: String,
        scopeValue: String,
        cursor: String?,
        limit: Int = FEED_PAGE_SIZE,
    ): FeedbackFeedDto

    companion object {
        const val FEED_PAGE_SIZE = 20
    }
}
