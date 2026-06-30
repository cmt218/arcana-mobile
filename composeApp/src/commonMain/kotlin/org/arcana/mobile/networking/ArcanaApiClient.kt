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
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
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
import org.arcana.mobile.analytics.Telemetry
import org.arcana.mobile.auth.TokenStorage
import org.arcana.mobile.data.BookingDto
import org.arcana.mobile.data.CancelBookingResponse
import org.arcana.mobile.data.CompleteSignupRequest
import org.arcana.mobile.data.SignupProfile
import org.arcana.mobile.data.CompleteSignupResponse
import org.arcana.mobile.data.CreateConciergeRequest
import org.arcana.mobile.data.CreateConciergeResponse
import org.arcana.mobile.data.CreateBookingRequest
import org.arcana.mobile.data.CreateBookingResponse
import org.arcana.mobile.data.FavoritesDto
import org.arcana.mobile.data.LoginRequest
import org.arcana.mobile.data.MeProfileDto
import org.arcana.mobile.data.MembershipMeDto
import org.arcana.mobile.data.MyBookingsDto
import org.arcana.mobile.data.ScheduleOverviewDto
import org.arcana.mobile.data.SchedulePageDto
import org.arcana.mobile.data.UpdateProfileRequest
import org.arcana.mobile.data.ScheduleSessionDto
import org.arcana.mobile.data.RefreshRequest
import org.arcana.mobile.data.RefreshTokenResponse
import org.arcana.mobile.data.StudioDto
import org.arcana.mobile.data.TokenResponse
import org.arcana.mobile.data.UpdateFavoritesRequest
import org.arcana.mobile.signup.CompleteSignupResult

/** Raised by [ArcanaApiClient.login] for a non-2xx token response. [statusCode]
 * is the HTTP status — 401 means the email/password didn't match. */
class LoginError(val statusCode: Int) : Exception("login_failed_$statusCode")

class ArcanaApiClient(
    private val tokenStorage: TokenStorage,
    private val baseUrlProvider: BaseUrlProvider,
    private val telemetry: Telemetry = Telemetry.Noop,
) : BookingApi, MembershipApi, FavoritesApi, ScheduleApi, ConciergeApi, ProfileApi {

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
                            forceLogout("refresh_missing")
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
                        // Refresh token rejected/expired or network failure during
                        // refresh → the session is dead through no user action.
                        forceLogout("refresh_error")
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
        val response = client.post(v1("auth/token/")) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email, password))
        }
        // expectSuccess is false, so a non-2xx response never throws here — and
        // calling .body<TokenResponse>() on a 401 would fail deserializing the
        // error body, surfacing as a generic "couldn't connect". Inspect the
        // status explicitly so bad credentials are reported as such.
        if (response.status != HttpStatusCode.OK) {
            throw LoginError(response.status.value)
        }
        val tokens = response.body<TokenResponse>()
        tokenStorage.accessToken = tokens.access
        tokenStorage.refreshToken = tokens.refresh
        clearBearerTokenCache()
        _isAuthenticated.value = true
    }

    suspend fun completeSignup(
        token: String,
        password: String,
        displayName: String,
        phoneNumber: String,
        profile: SignupProfile,
    ): CompleteSignupResult {
        return try {
            val response = client.post(v1("auth/complete-signup")) {
                contentType(ContentType.Application.Json)
                setBody(CompleteSignupRequest(
                    token = token,
                    password = password,
                    display_name = displayName,
                    phone_number = phoneNumber,
                    gender = profile.gender,
                    birthday = profile.birthday,
                    address_line1 = profile.addressLine1,
                    address_line2 = profile.addressLine2,
                    city = profile.city,
                    state = profile.state,
                    postal_code = profile.postalCode,
                ))
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
     * @param to inclusive end date; must be >= [from] and span ≤ 15 days
     *           inclusive (server `MAX_RANGE_DAYS = 15`, i.e. up to today + 14)
     * @param studioSlugs optional studio-slug whitelist; matches any in the list
     * @param locationIds optional location-id whitelist; matches any in the list
     * @param categorySlugs optional modality-category slug whitelist; matches
     *        classes in any of the selected categories. Sent as REPEATED
     *        `category` params; the server resolves slugs to raw modalities.
     * @param availableOnly when true, hides sessions with no remaining spots
     */
    override suspend fun fetchSchedule(
        from: LocalDate,
        to: LocalDate,
        studioSlugs: List<String>?,
        locationIds: List<Int>?,
        categorySlugs: List<String>?,
        availableOnly: Boolean,
    ): List<ScheduleSessionDto> {
        return client.get(v1("classes/")) {
            parameter("from", from.toString())
            parameter("to", to.toString())
            studioSlugs?.takeIf { it.isNotEmpty() }
                ?.let { parameter("studio_slug", it.joinToString(",")) }
            locationIds?.takeIf { it.isNotEmpty() }
                ?.let { parameter("location_id", it.joinToString(",")) }
            categorySlugs?.forEach { parameter("category", it) }
            if (availableOnly) parameter("available_only", "true")
        }.body()
    }

    /**
     * `GET /api/v1/classes/overview/` — the window's chip-rail data (Partners +
     * locations). Filter-independent: the server builds the studios block from
     * the unfiltered window so chips never vanish while a filter is active.
     */
    override suspend fun fetchOverview(
        from: LocalDate,
        to: LocalDate,
        studioSlugs: List<String>?,
        locationIds: List<Int>?,
        categorySlugs: List<String>?,
        availableOnly: Boolean,
    ): ScheduleOverviewDto {
        return client.get(v1("classes/overview/")) {
            parameter("from", from.toString())
            parameter("to", to.toString())
            studioSlugs?.takeIf { it.isNotEmpty() }
                ?.let { parameter("studio_slug", it.joinToString(",")) }
            locationIds?.takeIf { it.isNotEmpty() }
                ?.let { parameter("location_id", it.joinToString(",")) }
            categorySlugs?.forEach { parameter("category", it) }
            if (availableOnly) parameter("available_only", "true")
        }.body()
    }

    /**
     * `GET /api/v1/classes/sessions/` — one keyset page of [date]'s sessions
     * (server scopes to a single day via `from == to`). Pass the previous
     * page's `next_cursor` to continue; omit for page 1. `limit` is left to
     * the server default (50).
     */
    override suspend fun fetchSessionsPage(
        date: LocalDate,
        studioSlugs: List<String>?,
        locationIds: List<Int>?,
        categorySlugs: List<String>?,
        availableOnly: Boolean,
        cursor: String?,
    ): SchedulePageDto {
        return client.get(v1("classes/sessions/")) {
            parameter("from", date.toString())
            parameter("to", date.toString())
            studioSlugs?.takeIf { it.isNotEmpty() }
                ?.let { parameter("studio_slug", it.joinToString(",")) }
            locationIds?.takeIf { it.isNotEmpty() }
                ?.let { parameter("location_id", it.joinToString(",")) }
            categorySlugs?.forEach { parameter("category", it) }
            if (availableOnly) parameter("available_only", "true")
            cursor?.let { parameter("cursor", it) }
        }.body()
    }

    override suspend fun fetchStudios(): List<StudioDto> =
        client.get(v1("studios/")).body()

    override suspend fun fetchFavorites(): FavoritesDto =
        client.get(v1("users/me/favorites/")).body()

    override suspend fun updateFavorites(
        studioSlugs: List<String>,
        locationIds: List<Int>,
    ): FavoritesDto = client.put(v1("users/me/favorites/")) {
        contentType(ContentType.Application.Json)
        setBody(UpdateFavoritesRequest(studioSlugs, locationIds))
    }.body()

    /**
     * `GET /api/v1/classes/<id>/` — single-class drill-down with sync-on-read.
     * Server refreshes from the platform if its cached row is > 30s old. May
     * take 300-900ms on a stale read; the UI should show a loading state.
     */
    suspend fun fetchClassDetail(id: Int): ScheduleSessionDto {
        return client.get(v1("classes/$id/")).body()
    }

    override suspend fun createBooking(
        sessionId: Int,
        requestedSpotId: Int?,
        studioVisitedBefore: Boolean?,
        spotPreference: String?,
    ): BookingDto {
        val response = client.post(v1("bookings/")) {
            contentType(ContentType.Application.Json)
            setBody(CreateBookingRequest(sessionId, requestedSpotId, studioVisitedBefore, spotPreference))
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

    override suspend fun fetchProfile(): MeProfileDto =
        client.get(v1("users/me/")).body()

    override suspend fun updateProfile(body: UpdateProfileRequest): MeProfileDto =
        client.patch(v1("users/me/")) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()

    override suspend fun createConciergeRequest(message: String): Int {
        val response = client.post(v1("concierge-requests/")) {
            contentType(ContentType.Application.Json)
            setBody(CreateConciergeRequest(message))
        }
        if (response.status == HttpStatusCode.Created || response.status == HttpStatusCode.OK) {
            return response.body<CreateConciergeResponse>().id
        }
        // Non-2xx: the server returns {"error": "<reason_code>"} (or DRF field
        // errors). Surface a code; the screen maps it to friendly copy.
        val code = try {
            response.body<Map<String, String>>()["error"]
        } catch (_: Exception) {
            null
        } ?: "concierge_failed"
        throw ConciergeError(code)
    }

    fun logout() {
        telemetry.logoutManual()
        telemetry.reset()
        tokenStorage.clear()
        clearBearerTokenCache()
        _isAuthenticated.value = false
    }

    /** Session ended without the member pressing "sign out" — refresh-token
     *  invalidation/expiry or a refresh network failure. Tracked distinctly from
     *  [logout] so we can monitor unexpected sign-outs as an app-health signal. */
    private fun forceLogout(cause: String) {
        telemetry.forcedLogout(cause)
        telemetry.reset()
        tokenStorage.clear()
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
