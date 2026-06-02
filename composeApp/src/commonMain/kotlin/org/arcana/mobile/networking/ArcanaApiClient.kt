package org.arcana.mobile.networking

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.authProviders
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import org.arcana.mobile.auth.TokenStorage
import org.arcana.mobile.data.BookingDto
import org.arcana.mobile.data.CancelBookingResponse
import org.arcana.mobile.data.CompleteSignupRequest
import org.arcana.mobile.data.CompleteSignupResponse
import org.arcana.mobile.data.CreateBookingRequest
import org.arcana.mobile.data.CreateBookingResponse
import org.arcana.mobile.data.LoginRequest
import org.arcana.mobile.data.MembershipMeDto
import org.arcana.mobile.data.MyBookingsDto
import org.arcana.mobile.data.ScheduleSessionDto
import org.arcana.mobile.data.RefreshRequest
import org.arcana.mobile.data.RefreshTokenResponse
import org.arcana.mobile.data.TokenResponse
import org.arcana.mobile.signup.CompleteSignupResult

class ArcanaApiClient(
    private val tokenStorage: TokenStorage,
    private val baseUrlProvider: BaseUrlProvider,
) : BookingApi, MembershipApi {

    private val _isAuthenticated = MutableStateFlow(tokenStorage.isLoggedIn)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated

    // Re-read the URL per-request so Developer Settings edits apply to the
    // very next outbound call without recreating the HttpClient.
    private fun v1(path: String) = "${baseUrlProvider.get()}/api/v1/$path"

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(Auth) {
            bearer {
                loadTokens {
                    val access = tokenStorage.accessToken ?: return@loadTokens null
                    val refresh = tokenStorage.refreshToken ?: return@loadTokens null
                    BearerTokens(access, refresh)
                }
                refreshTokens {
                    val refresh = tokenStorage.refreshToken
                        ?: run {
                            tokenStorage.clear()
                            _isAuthenticated.value = false
                            return@refreshTokens null
                        }
                    try {
                        val tokens = client.post(v1("auth/token/refresh/")) {
                            contentType(ContentType.Application.Json)
                            setBody(RefreshRequest(refresh))
                            markAsRefreshTokenRequest()
                        }.body<RefreshTokenResponse>()
                        tokenStorage.accessToken = tokens.access
                        tokens.refresh?.let { tokenStorage.refreshToken = it }
                        BearerTokens(tokens.access, tokenStorage.refreshToken ?: return@refreshTokens null)
                    } catch (e: Exception) {
                        tokenStorage.clear()
                        _isAuthenticated.value = false
                        null
                    }
                }
                sendWithoutRequest { request ->
                    !request.url.encodedPath.contains("token") &&
                        !request.url.encodedPath.contains("register") &&
                        !request.url.encodedPath.contains("complete-signup")
                }
            }
        }
    }

    suspend fun login(email: String, password: String) {
        val tokens = client.post(v1("auth/token/")) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email, password))
        }.body<TokenResponse>()
        tokenStorage.accessToken = tokens.access
        tokenStorage.refreshToken = tokens.refresh
        clearBearerTokenCache()
        _isAuthenticated.value = true
    }

    suspend fun completeSignup(
        token: String,
        password: String,
        displayName: String,
    ): CompleteSignupResult {
        return try {
            val response = client.post(v1("auth/complete-signup")) {
                contentType(ContentType.Application.Json)
                setBody(CompleteSignupRequest(token, password, displayName))
            }
            when (response.status) {
                HttpStatusCode.OK -> {
                    val payload = response.body<CompleteSignupResponse>()
                    tokenStorage.accessToken = payload.access
                    tokenStorage.refreshToken = payload.refresh
                    clearBearerTokenCache()
                    _isAuthenticated.value = true
                    CompleteSignupResult.Success(payload)
                }
                HttpStatusCode.Gone -> CompleteSignupResult.TokenExpiredOrConsumed
                else -> CompleteSignupResult.Other(response.status.value, response.bodyAsText())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            CompleteSignupResult.NetworkError(e)
        }
    }

    /**
     * `GET /api/v1/classes/` — DB-backed schedule browse (Phase 3).
     *
     * @param from inclusive start date (local NY date — server interprets in
     *             `America/New_York`)
     * @param to inclusive end date; must be >= [from] and ≤ 14 days from it
     * @param studioSlugs optional studio-slug whitelist; matches any in the list
     * @param locationIds optional location-id whitelist; matches any in the list
     * @param modality optional case-insensitive modality match
     * @param availableOnly when true, hides sessions with no remaining spots
     */
    suspend fun fetchSchedule(
        from: LocalDate,
        to: LocalDate,
        studioSlugs: List<String>? = null,
        locationIds: List<Int>? = null,
        modality: String? = null,
        availableOnly: Boolean = false,
    ): List<ScheduleSessionDto> {
        return client.get(v1("classes/")) {
            parameter("from", from.toString())
            parameter("to", to.toString())
            studioSlugs?.takeIf { it.isNotEmpty() }
                ?.let { parameter("studio_slug", it.joinToString(",")) }
            locationIds?.takeIf { it.isNotEmpty() }
                ?.let { parameter("location_id", it.joinToString(",")) }
            modality?.let { parameter("modality", it) }
            if (availableOnly) parameter("available_only", "true")
        }.body()
    }

    /**
     * `GET /api/v1/classes/<id>/` — single-class drill-down with sync-on-read.
     * Server refreshes from the platform if its cached row is > 30s old. May
     * take 300-900ms on a stale read; the UI should show a loading state.
     */
    suspend fun fetchClassDetail(id: Int): ScheduleSessionDto {
        return client.get(v1("classes/$id/")).body()
    }

    override suspend fun createBooking(sessionId: Int, requestedSpotId: Int?): BookingDto {
        val response = client.post(v1("bookings/")) {
            contentType(ContentType.Application.Json)
            setBody(CreateBookingRequest(sessionId, requestedSpotId))
        }
        if (response.status == HttpStatusCode.Created || response.status == HttpStatusCode.OK) {
            val created = response.body<CreateBookingResponse>()
            return client.get(v1("bookings/${created.bookingId}/")).body()
        }
        // Non-2xx: the server returns {"error": "<reason_code>"}. Surface the code.
        val code = try {
            response.body<Map<String, String>>()["error"]
        } catch (_: Exception) {
            null
        } ?: "booking_failed"
        throw BookingError(code)
    }

    override suspend fun myBookings(): MyBookingsDto =
        client.get(v1("bookings/me/")).body()

    override suspend fun cancelBooking(bookingId: Int): CancelBookingResponse =
        client.delete(v1("bookings/$bookingId/")).body()

    override suspend fun membershipMe(): MembershipMeDto =
        client.get(v1("memberships/me")).body()

    fun logout() {
        tokenStorage.clear()
        clearBearerTokenCache()
        _isAuthenticated.value = false
    }

    /**
     * Clear the Auth plugin's in-memory bearer-token cache so the next request
     * reloads from TokenStorage. Ktor's bearer provider caches the result of
     * `loadTokens` and does NOT re-read storage on its own — without this, after
     * logout + login as a different user, requests keep going out with the
     * previous user's cached access token (showing the wrong account's data).
     */
    private fun clearBearerTokenCache() {
        client.authProviders
            .filterIsInstance<BearerAuthProvider>()
            .firstOrNull()
            ?.clearToken()
    }
}
