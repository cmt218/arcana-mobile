package org.arcana.mobile.review

import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.arcana.mobile.data.FeedbackFeedDto
import org.arcana.mobile.data.ReviewBrandDto
import org.arcana.mobile.data.ReviewClassTypeDto
import org.arcana.mobile.data.ReviewDto
import org.arcana.mobile.data.ReviewInstructorDto
import org.arcana.mobile.data.ReviewLocationDto
import org.arcana.mobile.data.ReviewPromptDto
import org.arcana.mobile.networking.ReviewApi

/** Keeps one review per booking and applies patches the way the server does. */
internal class FakeReviewApi : ReviewApi {
    val created = mutableListOf<Triple<Int, String, String>>()
    val patched = mutableListOf<Pair<Int, JsonObject>>()
    val dismissed = mutableListOf<Int>()
    val feedCalls = mutableListOf<Triple<String, String, String?>>()
    var reviews = mutableMapOf<Int, ReviewDto>()
    var nextId = 100
    var failWith: Throwable? = null
    /** Set to hold every write in flight until the test completes it. */
    var gate: CompletableDeferred<Unit>? = null
    var nextPrompt: ReviewPromptDto? = null
    var feedPages: (String?) -> FeedbackFeedDto = { FeedbackFeedDto() }

    override suspend fun createReview(bookingId: Int, again: String, promptSurface: String): ReviewDto {
        gate?.await()
        failWith?.let { throw it }
        created += Triple(bookingId, again, promptSurface)
        reviews[bookingId]?.let { return it }
        val review = review(id = nextId++, bookingId = bookingId, again = again, promptSurface = promptSurface)
        reviews[bookingId] = review
        return review
    }

    override suspend fun updateReview(bookingId: Int, fields: JsonObject): ReviewDto {
        gate?.await()
        failWith?.let { throw it }
        patched += bookingId to fields
        val current = reviews.getValue(bookingId)
        val next = current.copy(
            again = fields["again"]?.jsonPrimitive?.contentOrNull ?: current.again,
            intensity = if ("intensity" in fields) fields["intensity"]?.jsonPrimitive?.intOrNull else current.intensity,
            instructorScore = fields["instructor_score"]?.jsonPrimitive?.intOrNull ?: current.instructorScore,
            classScore = fields["class_score"]?.jsonPrimitive?.intOrNull ?: current.classScore,
            studioScore = fields["studio_score"]?.jsonPrimitive?.intOrNull ?: current.studioScore,
            comment = fields["comment"]?.jsonPrimitive?.contentOrNull ?: current.comment,
        )
        reviews[bookingId] = next
        return next
    }

    override suspend fun dismissReviewPrompt(bookingId: Int): ReviewPromptDto? {
        failWith?.let { throw it }
        dismissed += bookingId
        return nextPrompt
    }

    override suspend fun fetchFeedback(scopeType: String, scopeValue: String, cursor: String?, limit: Int): FeedbackFeedDto {
        failWith?.let { throw it }
        feedCalls += Triple(scopeType, scopeValue, cursor)
        return feedPages(cursor)
    }
}

internal fun review(
    id: Int,
    bookingId: Int? = id,
    again: String = "yes",
    intensity: Int? = null,
    comment: String = "",
    promptSurface: String = "detail",
    createdAt: String = "2026-09-12T14:00:00Z",
    instructor: String? = "Dana Levy",
) = ReviewDto(
    id = id,
    createdAt = createdAt,
    brand = ReviewBrandDto("soto-method", "Soto Method"),
    location = ReviewLocationDto(4, "Flatiron"),
    classType = ReviewClassTypeDto("sculpt-50", "Sculpt 50"),
    instructor = instructor?.let { ReviewInstructorDto(7, it) },
    again = again,
    intensity = intensity,
    comment = comment,
    bookingId = bookingId,
    promptSurface = promptSurface,
)

internal fun prompt(bookingId: Int, endAt: String = "2026-09-14T19:00:00Z") = ReviewPromptDto(
    bookingId = bookingId,
    classSessionId = bookingId * 10,
    startAt = "2026-09-14T18:00:00Z",
    endAt = endAt,
    brand = ReviewBrandDto("soto-method", "Soto Method"),
    location = ReviewLocationDto(4, "Flatiron"),
    classType = ReviewClassTypeDto("sculpt-50", "Sculpt 50"),
    instructor = ReviewInstructorDto(7, "Jess"),
)

internal fun JsonObject.int(key: String) = this[key]?.jsonPrimitive?.intOrNull
internal fun JsonObject.str(key: String) = this[key]?.jsonPrimitive?.contentOrNull
internal fun json(vararg pairs: Pair<String, Any?>) = JsonObject(
    pairs.associate { (k, v) ->
        k to when (v) {
            null -> JsonPrimitive(null as String?)
            is Int -> JsonPrimitive(v)
            else -> JsonPrimitive(v.toString())
        }
    },
)
