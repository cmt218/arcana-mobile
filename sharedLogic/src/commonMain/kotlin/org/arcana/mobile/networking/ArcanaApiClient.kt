package org.arcana.mobile.networking

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
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
import kotlinx.serialization.json.JsonObject
import org.arcana.mobile.analytics.Telemetry
import org.arcana.mobile.auth.SecureStorageDiagnostics
import org.arcana.mobile.auth.TokenStorage
import org.arcana.mobile.data.BookingDto
import org.arcana.mobile.data.CancelBookingResponse
import org.arcana.mobile.data.CompleteSignupRequest
import org.arcana.mobile.data.SignupProfile
import org.arcana.mobile.data.CompleteSignupResponse
import org.arcana.mobile.data.SignupSurveyRequest
import org.arcana.mobile.data.CreateConciergeRequest
import org.arcana.mobile.data.CreateConciergeResponse
import org.arcana.mobile.data.CreateBookingRequest
import org.arcana.mobile.data.CreateBookingResponse
import org.arcana.mobile.data.FavoritesDto
import org.arcana.mobile.data.LoginRequest
import org.arcana.mobile.data.MeProfileDto
import org.arcana.mobile.data.MembershipMeDto
import org.arcana.mobile.data.MyBookingsDto
import org.arcana.mobile.data.PasswordResetRequest
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
import org.arcana.mobile.signup.SignupSurveyResult

/** Raised by [ArcanaApiClient.login] for a non-2xx token response. [statusCode]
 * is the HTTP status — 401 means the email/password didn't match. */
class LoginError(val statusCode: Int) : Exception("login_failed_$statusCode")

/**
 * Not a crash — a synthetic Throwable so a forced logout reaches Sentry with its
 * breadcrumb trail attached.
 *
 * Sentry only ships breadcrumbs alongside a captured event, and a forced logout
 * throws nothing, so the most member-hostile thing the app can do was previously
 * invisible there.
 *
 * Grouping is per exception class, so forced logouts get their own Sentry issue
 * rather than sharing one with every other Kotlin nonfatal; `cause` is attached
 * as a tag, though `refresh_error` is now the only value: an affirmative server
 * rejection is the sole thing that may end a session.
 * On iOS that depends on `SwiftCrashReporter` deriving the NSError domain from
 * this type — Sentry titles and groups NSError by domain+code, so hardcoding
 * either one silently merges unrelated issues.
 */
class ForcedLogoutSignal(cause: String) : Exception("forced_logout: $cause")

/**
 * How the client should react to a token-refresh attempt. A member is only
 * force-logged-out when the server genuinely *rejects* the refresh token
 * ([REJECTED] — a 401/403). Every other failure — a 5xx, a 429, a timeout, or a
 * response that never fully arrives on a flaky connection — is [TRANSIENT] and
 * MUST leave the session intact so the next request can retry. Treating a
 * transient failure as a logout signs out members whose credentials are
 * perfectly valid: observed in prod on 2026-07-01, where a refresh the server
 * answered `200` threw client-side on cellular and force-logged-out the member.
 */
internal enum class RefreshOutcome { REFRESHED, REJECTED, TRANSIENT }

/**
 * Classify a *completed* refresh HTTP response by status code. Only 401/403 —
 * the refresh endpoint's "this token is no good" answers — are [REJECTED]. Any
 * other non-2xx (5xx, 429, 408, 400, ...) is [TRANSIENT], so we never sign out
 * a member on a status that doesn't prove their refresh token is dead. A 2xx is
 * [REFRESHED].
 */
internal fun refreshOutcomeForStatus(statusCode: Int): RefreshOutcome = when {
    statusCode in 200..299 -> RefreshOutcome.REFRESHED
    statusCode == 401 || statusCode == 403 -> RefreshOutcome.REJECTED
    else -> RefreshOutcome.TRANSIENT
}

class ArcanaApiClient(
    private val tokenStorage: TokenStorage,
    private val baseUrlProvider: BaseUrlProvider,
    private val telemetry: Telemetry = Telemetry.Noop,
) : BookingApi, MembershipApi, FavoritesApi, ScheduleApi, ConciergeApi, ProfileApi, PasswordResetApi {

    private val _isAuthenticated = MutableStateFlow(tokenStorage.isLoggedIn)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated

    // Re-read the URL per-request so Developer Settings edits apply to the
    // very next outbound call without recreating the HttpClient.
    //
    // Telemetry: every call below auto-emits an `api_request` event via
    // PerfTimingPlugin (no per-call-site code). When you ADD an endpoint, add a
    // case to `analytics.normalizeEndpoint` so it's tracked individually on the
    // Mobile Performance dashboard — otherwise it buckets as `other`. See
    // arcana-mobile CLAUDE.md → "Telemetry" → performance instrumentation.
    private fun v1(path: String) = "${baseUrlProvider.get()}/api/v1/$path"

    private val client = HttpClient {
        // Without this a stalled connection hangs forever. ~9x headroom over the
        // slowest real call (booking, ~6.5s server-side). iOS caveat: Darwin
        // ignores socketTimeoutMillis, so it is bounded by requestTimeoutMillis
        // instead. Trello vVs2x4jG.
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 30_000
            requestTimeoutMillis = 60_000
        }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(PerfTimingPlugin) {
            telemetry = this@ArcanaApiClient.telemetry
            // 100% during beta (tiny volume); dial down here if we approach
            // the PostHog free-tier ceiling.
            sampleRate = 1.0
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
                            // A null read is not a rejection. The server never
                            // said the token was bad, so this is as unproven as
                            // a timeout: keep the session and let the next
                            // request retry, like every other branch below.
                            val failure = SecureStorageDiagnostics
                                .lastFailureFor(TokenStorage.REFRESH_TOKEN_KEY)
                            telemetry.authRefreshFailed(
                                Telemetry.RefreshFailureOutcome.NO_STORED_REFRESH,
                                osStatus = failure?.status,
                                storageOp = failure?.op,
                            )
                            return@refreshTokens null
                        }
                    // A refresh fails two very different ways: the server *rejects*
                    // the token (401/403 → session is truly dead), or the request
                    // just doesn't complete (5xx, timeout, a response dropped on
                    // cellular). Only the former may sign the member out; the latter
                    // must leave the tokens untouched so the next request retries.
                    // `expectSuccess` is false, so we inspect the status ourselves
                    // rather than let `.body()` throw on an error body (as `login()`
                    // does). Never forceLogout on a transient failure — doing so
                    // logged out valid sessions in prod (see [RefreshOutcome]).
                    val response = try {
                        client.post(v1("auth/token/refresh/")) {
                            contentType(ContentType.Application.Json)
                            setBody(RefreshRequest(refresh))
                            markAsRefreshTokenRequest()
                        }
                    } catch (e: Exception) {
                        // Couldn't complete the request (network/IO/timeout). Not an
                        // auth failure — keep the session and let the caller retry.
                        telemetry.authRefreshFailed(Telemetry.RefreshFailureOutcome.TRANSIENT_EXCEPTION)
                        return@refreshTokens null
                    }
                    when (refreshOutcomeForStatus(response.status.value)) {
                        RefreshOutcome.REFRESHED -> {
                            val tokens = try {
                                response.body<RefreshTokenResponse>()
                            } catch (e: Exception) {
                                // 2xx but the body didn't parse / never fully arrived
                                // (seen on flaky cellular). Transient — do NOT sign out.
                                telemetry.authRefreshFailed(
                                    Telemetry.RefreshFailureOutcome.TRANSIENT_BODY,
                                    response.status.value,
                                )
                                return@refreshTokens null
                            }
                            tokenStorage.accessToken = tokens.access
                            tokens.refresh?.let { tokenStorage.refreshToken = it }
                            // Read the rotated token back out of storage. If it
                            // doesn't come back, the server refreshed us but we have
                            // no usable session — every later request then goes out
                            // unauthenticated. This used to be a silent `?: return
                            // null`; it is the prime suspect for the 2026-07-16
                            // logout, so report it (behavior unchanged).
                            val stored = tokenStorage.refreshToken
                            if (stored == null) {
                                val failure = SecureStorageDiagnostics
                                    .lastFailureFor(TokenStorage.REFRESH_TOKEN_KEY)
                                telemetry.authRefreshFailed(
                                    Telemetry.RefreshFailureOutcome.STORED_REFRESH_MISSING,
                                    response.status.value,
                                    osStatus = failure?.status,
                                    storageOp = failure?.op,
                                )
                                return@refreshTokens null
                            }
                            BearerTokens(tokens.access, stored)
                        }
                        RefreshOutcome.REJECTED -> {
                            // Server explicitly rejected the refresh token — the
                            // session is dead through no user action.
                            telemetry.authRefreshFailed(
                                Telemetry.RefreshFailureOutcome.REJECTED,
                                response.status.value,
                            )
                            forceLogout("refresh_error")
                            null
                        }
                        // 5xx / 429 / timeout-shaped status: keep tokens, let retry.
                        RefreshOutcome.TRANSIENT -> {
                            telemetry.authRefreshFailed(
                                Telemetry.RefreshFailureOutcome.TRANSIENT_STATUS,
                                response.status.value,
                            )
                            null
                        }
                    }
                }
                sendWithoutRequest { request ->
                    !request.url.encodedPath.contains("token") &&
                        !request.url.encodedPath.contains("register") &&
                        !request.url.encodedPath.contains("complete-signup") &&
                        !request.url.encodedPath.contains("signup-survey") &&
                        !request.url.encodedPath.contains("request-password-reset")
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

    override suspend fun requestPasswordReset(email: String) {
        val response = client.post(v1("auth/request-password-reset")) {
            contentType(ContentType.Application.Json)
            setBody(PasswordResetRequest(email))
        }
        if (response.status != HttpStatusCode.OK) {
            throw PasswordResetRequestError(response.status.value)
        }
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
     * `POST /api/v1/beta/signup-survey` — the pre-claim onboarding survey.
     * Token-gated but unauthenticated (no account exists yet); validating never
     * consumes the token, so complete-signup still works afterwards.
     */
    suspend fun submitSignupSurvey(
        token: String,
        answers: JsonObject,
    ): SignupSurveyResult {
        return try {
            val response = client.post(v1("beta/signup-survey")) {
                contentType(ContentType.Application.Json)
                setBody(SignupSurveyRequest(token = token, answers = answers))
            }
            when (response.status) {
                HttpStatusCode.OK -> SignupSurveyResult.Success
                HttpStatusCode.Gone -> SignupSurveyResult.TokenExpiredOrConsumed
                else -> SignupSurveyResult.Other(response.status.value)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            SignupSurveyResult.NetworkError(e)
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
        }.bodyOrThrow()
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
        startTimeGte: String?,
        startTimeLte: String?,
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
            startTimeGte?.let { parameter("start_time_gte", it) }
            startTimeLte?.let { parameter("start_time_lte", it) }
            if (availableOnly) parameter("available_only", "true")
        }.bodyOrThrow()
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
        startTimeGte: String?,
        startTimeLte: String?,
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
            startTimeGte?.let { parameter("start_time_gte", it) }
            startTimeLte?.let { parameter("start_time_lte", it) }
            if (availableOnly) parameter("available_only", "true")
            cursor?.let { parameter("cursor", it) }
        }.bodyOrThrow()
    }

    override suspend fun fetchStudios(): List<StudioDto> =
        client.get(v1("studios/")).bodyOrThrow()

    override suspend fun fetchFavorites(): FavoritesDto =
        client.get(v1("users/me/favorites/")).bodyOrThrow()

    override suspend fun updateFavorites(
        studioSlugs: List<String>,
        locationIds: List<Int>,
    ): FavoritesDto = client.put(v1("users/me/favorites/")) {
        contentType(ContentType.Application.Json)
        setBody(UpdateFavoritesRequest(studioSlugs, locationIds))
    }.bodyOrThrow()

    /**
     * `GET /api/v1/classes/<id>/` — single-class drill-down with sync-on-read.
     * Server refreshes from the platform if its cached row is > 30s old. May
     * take 300-900ms on a stale read; the UI should show a loading state.
     */
    suspend fun fetchClassDetail(id: Int): ScheduleSessionDto {
        return client.get(v1("classes/$id/")).bodyOrThrow()
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
            // bodyOrThrow: a 5xx here is about a booking the server already
            // created, so it must not read as a connection failure.
            return client.get(v1("bookings/${created.bookingId}/")).bodyOrThrow()
        }
        throw bookingFailureFor(response.status.value, response.parsedErrorCode())
    }

    override suspend fun myBookings(): MyBookingsDto =
        client.get(v1("bookings/me/")).bodyOrThrow()

    override suspend fun cancelBooking(bookingId: Int): CancelBookingResponse =
        client.delete(v1("bookings/$bookingId/")).bodyOrThrow()

    override suspend fun membershipMe(): MembershipMeDto =
        client.get(v1("memberships/me")).bodyOrThrow()

    override suspend fun fetchProfile(): MeProfileDto =
        client.get(v1("users/me/")).bodyOrThrow()

    // bodyOrThrow, not body: MeProfileDto defaults every field, so a bare
    // .body() on a 5xx used to silently return an empty-but-"successful" profile.
    override suspend fun updateProfile(body: UpdateProfileRequest): MeProfileDto =
        client.patch(v1("users/me/")) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.bodyOrThrow()

    override suspend fun createConciergeRequest(message: String): Int {
        val response = client.post(v1("concierge-requests/")) {
            contentType(ContentType.Application.Json)
            setBody(CreateConciergeRequest(message))
        }
        if (response.status == HttpStatusCode.Created || response.status == HttpStatusCode.OK) {
            return response.body<CreateConciergeResponse>().id
        }
        // A named reason wins; a 5xx has none to give, so route it through
        // ApiHttpError and let toErrorType() classify it SERVER.
        val code = response.parsedErrorCode()
        if (code != null) throw ConciergeError(code)
        if (response.status.value >= 500) throw ApiHttpError(response.status.value)
        throw ConciergeError("concierge_failed")
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
        // What did the secure store last do *for the refresh token*? `cause` only
        // says the token was null; this says why — locked vs genuinely absent vs a
        // failed write. Scoped to the refresh-token key so an unrelated miss on
        // another key (e.g. the usually-absent base-URL override) can't be
        // misattributed to this logout.
        val failure = SecureStorageDiagnostics.lastFailureFor(TokenStorage.REFRESH_TOKEN_KEY)
        telemetry.forcedLogout(
            cause = cause,
            osStatus = failure?.status,
            storageOp = failure?.op,
            storageKey = failure?.key,
        )
        // Report to Sentry BEFORE reset(): reset() calls clearUser(), so a
        // nonfatal raised after it arrives with no member attached. A forced
        // logout throws nothing, so without this explicit capture Sentry never
        // hears about it at all — breadcrumbs only ship when something is
        // captured, which is exactly why the 2026-07-16 logout left no trace.
        telemetry.recordError(
            ForcedLogoutSignal(cause),
            mapOf(
                "cause" to cause,
                "storage_os_status" to failure?.status,
                "storage_op" to failure?.op,
                "storage_key" to failure?.key,
            ),
        )
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
