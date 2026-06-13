package org.arcana.mobile.signup

import org.arcana.mobile.data.CompleteSignupResponse
import org.arcana.mobile.networking.ArcanaApiClient

sealed interface CompleteSignupResult {
    data class Success(val response: CompleteSignupResponse) : CompleteSignupResult
    data object TokenExpiredOrConsumed : CompleteSignupResult
    data class Other(val statusCode: Int, val body: String) : CompleteSignupResult
    data class NetworkError(val cause: Throwable) : CompleteSignupResult
}

/**
 * Abstraction the SignupCompletionViewModel depends on, so it can be unit-tested
 * against a fake without an HttpClient.
 */
interface CompleteSignupCallable {
    suspend fun complete(
        token: String,
        password: String,
        displayName: String,
        phoneNumber: String,
    ): CompleteSignupResult
}

/**
 * Thin adapter over [ArcanaApiClient.completeSignup]. The actual network call,
 * token persistence, and auth-state flip live in ArcanaApiClient (it owns the
 * private HttpClient and isAuthenticated StateFlow), mirroring login()/register().
 */
class CompleteSignupApi(
    private val apiClient: ArcanaApiClient,
) : CompleteSignupCallable {
    override suspend fun complete(
        token: String,
        password: String,
        displayName: String,
        phoneNumber: String,
    ): CompleteSignupResult =
        apiClient.completeSignup(token, password, displayName, phoneNumber)
}
