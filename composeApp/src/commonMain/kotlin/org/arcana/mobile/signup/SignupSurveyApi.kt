package org.arcana.mobile.signup

import kotlinx.serialization.json.JsonObject
import org.arcana.mobile.networking.ArcanaApiClient

sealed interface SignupSurveyResult {
    data object Success : SignupSurveyResult
    data object TokenExpiredOrConsumed : SignupSurveyResult
    data class Other(val statusCode: Int) : SignupSurveyResult
    data class NetworkError(val cause: Throwable) : SignupSurveyResult
}

/**
 * Abstraction the SignupSurveyViewModel depends on, so it can be unit-tested
 * against a fake without an HttpClient (mirrors [CompleteSignupCallable]).
 */
interface SignupSurveyCallable {
    suspend fun submit(token: String, answers: JsonObject): SignupSurveyResult
}

/** Thin adapter over [ArcanaApiClient.submitSignupSurvey]. */
class SignupSurveyApi(
    private val apiClient: ArcanaApiClient,
) : SignupSurveyCallable {
    override suspend fun submit(token: String, answers: JsonObject): SignupSurveyResult =
        apiClient.submitSignupSurvey(token, answers)
}
