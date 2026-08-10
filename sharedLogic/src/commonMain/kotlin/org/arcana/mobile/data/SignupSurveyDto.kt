package org.arcana.mobile.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Wire request for `POST /api/v1/beta/signup-survey` — the token-gated
 * onboarding survey submitted between the signup deep link and the
 * claim-your-name screen. `answers` is the flat question-id → value map built
 * by [org.arcana.mobile.signup.answersToJson]; the server stores it opaquely.
 * The response body is just `{"status": "ok"}` — callers check the HTTP status.
 */
@Serializable
data class SignupSurveyRequest(
    val token: String,
    val answers: JsonObject,
)
