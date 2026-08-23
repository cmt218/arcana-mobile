package org.arcana.mobile.analytics

import org.arcana.mobile.isDebugBuild
import org.arcana.mobile.logDebug

/**
 * The single, type-safe entry point for all product instrumentation.
 *
 * Every event name and property key lives here — call sites never pass raw
 * strings. This keeps the taxonomy in one place, makes renames safe, and lets
 * tests assert against typed calls (see `FakeAnalytics` in commonTest).
 *
 * Each tracked event also drops a Sentry breadcrumb so a later crash report
 * shows the member's lead-up actions. Handled exceptions go through
 * [recordError]; identify/reset keep PostHog and Sentry user context in sync.
 *
 * Volume note (50-user beta, PostHog free tier): this set is intentionally
 * scoped to state transitions + errors. High-frequency signals (raw scroll,
 * silent token refresh) are deliberately omitted — see the project plan.
 */
class Telemetry(
    private val analytics: Analytics,
    private val crash: CrashReporter,
) {

    private fun track(event: String, props: Map<String, Any?> = emptyMap()) {
        val clean = props.filterValues { it != null }
        analytics.capture(event, clean)
        crash.addBreadcrumb(event, category = "analytics", data = clean)
        debugLog("▶ $event" + if (clean.isEmpty()) "" else " $clean")
    }

    /** Echo every telemetry call to logcat / Xcode console in debug builds, so
     *  the analytics stream can be eyeballed live while QA-ing the app. No-op in
     *  release. This is the single place all events flow through. */
    private fun debugLog(message: String) {
        if (isDebugBuild) logDebug(LOG_TAG, message)
    }

    // ---- Identity ---------------------------------------------------------

    /** The member already identified this session — so the two ProfileViewModel
     *  instances (nav-bar avatar + Profile screen, separately scoped by
     *  Navigation Compose) don't each re-identify. Cleared on [reset]. */
    private var lastIdentifiedId: String? = null

    /** Call on the first successful `/memberships/me` (covers login + signup).
     *  Idempotent within a session — only the first call per member id fires. */
    fun identify(memberId: String, email: String?, displayName: String?) {
        if (memberId == lastIdentifiedId) return
        lastIdentifiedId = memberId
        analytics.identify(
            memberId,
            mapOf("email" to email, "display_name" to displayName).filterValues { it != null },
        )
        crash.setUser(memberId, email)
        debugLog("▶ identify $memberId")  // id only — no PII in logs
    }

    /** Register the analytics `environment` (prod/local/tunnel/other) as a
     *  super-property on every subsequent event, so the perf dashboard can
     *  exclude dev traffic. Driven by [org.arcana.mobile.networking.BaseUrlProvider]
     *  whenever the API base URL is loaded or changed. */
    fun setEnvironment(environment: String) {
        analytics.setEnvironment(environment)
        debugLog("▶ environment=$environment")
    }

    /** Set/update which studios a member favorites, for cohort segmentation. */
    fun setFavoriteProfile(favoriteStudioCount: Int, favoriteStudios: List<String>) {
        analytics.setPersonProperties(
            mapOf(
                "favorite_studio_count" to favoriteStudioCount,
                "favorite_studios" to favoriteStudios.joinToString(","),
            ),
        )
    }

    /** Call on logout AND forced logout. */
    fun reset() {
        lastIdentifiedId = null
        analytics.reset()
        crash.clearUser()
        debugLog("▶ reset")
    }

    /** Report a handled (nonfatal) exception to Sentry. */
    fun recordError(error: Throwable, context: Map<String, Any?> = emptyMap()) {
        crash.captureException(error, context.filterValues { it != null })
        debugLog("▶ error: ${error.message}")
    }

    // ---- Screens & navigation --------------------------------------------

    fun screen(name: String, props: Map<String, Any?> = emptyMap()) {
        analytics.screen(name, props.filterValues { it != null })
        crash.addBreadcrumb("screen:$name", category = "navigation")
        debugLog("▶ \$screen $name")
    }

    fun tabTapped(tab: String, fromScreen: String?) =
        track(Events.TAB_TAPPED, mapOf("tab" to tab, "from_screen" to fromScreen))

    fun scheduleDayChanged(method: String, direction: String?, dayOffsetFromToday: Int) =
        track(
            Events.SCHEDULE_DAY_CHANGED,
            mapOf("method" to method, "direction" to direction, "day_offset_from_today" to dayOffsetFromToday),
        )

    fun scheduleLoadMore(pageIndex: Int, day: String) =
        track(Events.SCHEDULE_LOAD_MORE, mapOf("page_index" to pageIndex, "day" to day))

    fun scheduleFilterChanged(
        mode: String,
        studioCount: Int,
        locationCount: Int,
        modalityCount: Int = 0,
    ) =
        track(
            Events.SCHEDULE_FILTER_CHANGED,
            mapOf(
                "mode" to mode,
                "studio_count" to studioCount,
                "location_count" to locationCount,
                "modality_count" to modalityCount,
            ),
        )

    // ---- Performance & latency -------------------------------------------

    /** One per HTTP call (via the ArcanaApiClient Ktor plugin). `serverMs` is
     *  from the `X-Arcana-Server-Ms` header (null if absent); `networkMs` is the
     *  derived `total − server`. `statusCode` 0 ⇒ the request never completed. */
    fun apiRequest(
        endpoint: String,
        method: String,
        statusCode: Int,
        outcome: String,
        totalMs: Long,
        serverMs: Long?,
        networkMs: Long?,
        responseBytes: Long?,
    ) = track(
        Events.API_REQUEST,
        mapOf(
            "endpoint" to endpoint,
            "method" to method,
            "status_code" to statusCode,
            "outcome" to outcome,
            "total_ms" to totalMs,
            "server_ms" to serverMs,
            "network_ms" to networkMs,
            "response_bytes" to responseBytes,
        ),
    )

    /** Fired once per process when Home (or the auth screen) first renders. */
    fun appStartCompleted(durationMs: Long, startType: String, authenticated: Boolean, splashMs: Long?) =
        track(
            Events.APP_START_COMPLETED,
            mapOf(
                "duration_ms" to durationMs,
                "start_type" to startType,
                "authenticated" to authenticated,
                "splash_ms" to splashMs,
            ),
        )

    /** A screen reached rendered content. `source` distinguishes a fresh entry
     *  (cold_start/tab_switch) from an in-screen change (day_switch/filter/refresh). */
    fun screenLoadCompleted(screen: String, source: String, durationMs: Long, outcome: String, sessionCount: Int?) =
        track(
            Events.SCREEN_LOAD_COMPLETED,
            mapOf(
                "screen" to screen,
                "source" to source,
                "duration_ms" to durationMs,
                "outcome" to outcome,
                "session_count" to sessionCount,
            ),
        )

    /** Infinite-scroll pagination append completed. */
    fun schedulePageLoaded(durationMs: Long, pageIndex: Int, sessionCount: Int, outcome: String, day: String) =
        track(
            Events.SCHEDULE_PAGE_LOADED,
            mapOf(
                "duration_ms" to durationMs,
                "page_index" to pageIndex,
                "session_count" to sessionCount,
                "outcome" to outcome,
                "day" to day,
            ),
        )

    // ---- Signup funnel ----------------------------------------------------

    /** The onboarding survey (August cohort+) rendered with a valid token —
     *  the funnel step BEFORE signup_started (the claim screen). */
    fun signupSurveyStarted() = track(Events.SIGNUP_SURVEY_STARTED)

    fun signupSurveySubmitted(answeredCount: Int) =
        track(Events.SIGNUP_SURVEY_SUBMITTED, mapOf("answered_count" to answeredCount))

    fun signupSurveyFailed(reason: String, statusCode: Int? = null) =
        track(Events.SIGNUP_SURVEY_FAILED, mapOf("reason" to reason, "status_code" to statusCode))

    /** Member used the "Continue anyway" escape after a failed submit — the
     *  survey never blocks a paid member's signup. */
    fun signupSurveySkipped(reason: String) =
        track(Events.SIGNUP_SURVEY_SKIPPED, mapOf("reason" to reason))

    fun signupStarted(source: String = "deep_link") =
        track(Events.SIGNUP_STARTED, mapOf("source" to source))

    fun signupSubmitted() = track(Events.SIGNUP_SUBMITTED)

    fun signupFailed(reason: String, statusCode: Int? = null) =
        track(Events.SIGNUP_FAILED, mapOf("reason" to reason, "status_code" to statusCode))

    fun signupCompleted() = track(Events.SIGNUP_COMPLETED)

    // ---- Login & session health ------------------------------------------

    fun loginSubmitted() = track(Events.LOGIN_SUBMITTED)

    fun loginFailed(reason: String, statusCode: Int? = null) =
        track(Events.LOGIN_FAILED, mapOf("reason" to reason, "status_code" to statusCode))

    fun loginSucceeded() = track(Events.LOGIN_SUCCEEDED)

    fun logoutManual() = track(Events.LOGOUT, mapOf("type" to "manual"))

    /**
     * The session ended without the member asking for it.
     *
     * [osStatus]/[storageOp]/[storageKey] carry the secure store's last
     * non-success result (see `SecureStorageDiagnostics`). `cause` alone only
     * says "the token was null"; these say *why* it was null — which is the
     * difference between "device was locked, the session was fine" and "the
     * token was genuinely gone". Null when the store reported no failure.
     */
    fun forcedLogout(
        cause: String,
        osStatus: Int? = null,
        storageOp: String? = null,
        storageKey: String? = null,
    ) = track(
        Events.FORCED_LOGOUT,
        mapOf(
            "type" to "forced",
            "cause" to cause,
            "storage_os_status" to osStatus,
            "storage_op" to storageOp,
            "storage_key" to storageKey,
        ),
    )

    /**
     * The platform secure store returned a non-success result. Fires only on
     * failure, so this is ~zero volume in the healthy case (respecting the
     * volume note above) and a direct signal when it isn't.
     */
    fun tokenStorageFailure(op: String, key: String, osStatus: Int) =
        track(
            Events.TOKEN_STORAGE_FAILURE,
            mapOf("op" to op, "key" to key, "os_status" to osStatus),
        )

    /**
     * A token refresh did not yield usable tokens. Deliberately fires ONLY on
     * failure — a successful silent refresh stays unreported, per the volume
     * note above. [outcome] is one of [RefreshFailureOutcome].
     */
    fun authRefreshFailed(outcome: String, statusCode: Int? = null) =
        track(
            Events.AUTH_REFRESH_FAILED,
            mapOf("outcome" to outcome, "status_code" to statusCode),
        )

    // ---- Class view → booking funnel -------------------------------------

    fun classViewed(
        sessionId: Int,
        studioId: Int?,
        studioName: String?,
        locationId: Int?,
        locationName: String?,
        modality: String?,
        spotsAvailable: Int,
        requiresSpot: Boolean,
        isFull: Boolean,
        loadMs: Long?,
    ) = track(
        Events.CLASS_VIEWED,
        mapOf(
            "session_id" to sessionId,
            "studio_id" to studioId,
            "studio_name" to studioName,
            "location_id" to locationId,
            "location_name" to locationName,
            "modality" to modality,
            "spots_available" to spotsAvailable,
            "requires_spot" to requiresSpot,
            "is_full" to isFull,
            "load_ms" to loadMs,
        ),
    )

    fun classViewFailed(sessionId: Int, reason: String) =
        track(Events.CLASS_VIEW_FAILED, mapOf("session_id" to sessionId, "reason" to reason))

    fun bookingSheetOpened(sessionId: Int, studioId: Int?, locationId: Int?, requiresSpot: Boolean) =
        track(
            Events.BOOKING_SHEET_OPENED,
            mapOf(
                "session_id" to sessionId,
                "studio_id" to studioId,
                "location_id" to locationId,
                "requires_spot" to requiresSpot,
            ),
        )

    fun spotSelected(sessionId: Int, spotId: Int, spotLabel: String) =
        track(
            Events.SPOT_SELECTED,
            mapOf("session_id" to sessionId, "spot_id" to spotId, "spot_label" to spotLabel),
        )

    fun bookingSubmitted(sessionId: Int, hasSpot: Boolean) =
        track(Events.BOOKING_SUBMITTED, mapOf("session_id" to sessionId, "has_spot" to hasSpot))

    /** The one-time "have you been to this studio before?" prompt was shown
     *  (member's first booking at this brand). */
    fun studioVisitPromptShown(sessionId: Int, studioId: Int?, studioName: String) =
        track(
            Events.STUDIO_VISIT_PROMPT_SHOWN,
            mapOf("session_id" to sessionId, "studio_id" to studioId, "studio_name" to studioName),
        )

    /** Member answered the studio-visit prompt — `visitedBefore` is the
     *  explore-vs-return signal. */
    fun studioVisitAnswered(sessionId: Int, studioId: Int?, studioName: String, visitedBefore: Boolean) =
        track(
            Events.STUDIO_VISIT_ANSWERED,
            mapOf(
                "session_id" to sessionId,
                "studio_id" to studioId,
                "studio_name" to studioName,
                "visited_before" to visitedBefore,
            ),
        )

    fun bookingSucceeded(
        bookingId: Int,
        status: String,
        sessionId: Int,
        studioId: Int?,
        locationId: Int?,
        hasSpot: Boolean,
    ) = track(
        Events.BOOKING_SUCCEEDED,
        mapOf(
            "booking_id" to bookingId,
            "status" to status,
            "session_id" to sessionId,
            "studio_id" to studioId,
            "location_id" to locationId,
            "has_spot" to hasSpot,
        ),
    )

    fun bookingFailed(reasonCode: String, sessionId: Int) =
        track(Events.BOOKING_FAILED, mapOf("reason_code" to reasonCode, "session_id" to sessionId))

    fun bookingSheetAbandoned(sessionId: Int, reachedSpotSelection: Boolean, hadSelectedSpot: Boolean) =
        track(
            Events.BOOKING_SHEET_ABANDONED,
            mapOf(
                "session_id" to sessionId,
                "reached_spot_selection" to reachedSpotSelection,
                "had_selected_spot" to hadSelectedSpot,
            ),
        )

    // ---- Cancellation -----------------------------------------------------

    fun bookingCancelStarted(bookingId: Int, sessionId: Int, willForfeitCredit: Boolean) =
        track(
            Events.BOOKING_CANCEL_STARTED,
            mapOf(
                "booking_id" to bookingId,
                "session_id" to sessionId,
                "will_forfeit_credit" to willForfeitCredit,
            ),
        )

    fun bookingCancelled(
        bookingId: Int,
        creditRefunded: Boolean,
        lateCancel: Boolean,
        studioId: Int?,
        locationId: Int?,
    ) = track(
        Events.BOOKING_CANCELLED,
        mapOf(
            "booking_id" to bookingId,
            "credit_refunded" to creditRefunded,
            "late_cancel" to lateCancel,
            "studio_id" to studioId,
            "location_id" to locationId,
        ),
    )

    fun bookingCancelFailed(bookingId: Int, reasonCode: String) =
        track(Events.BOOKING_CANCEL_FAILED, mapOf("booking_id" to bookingId, "reason_code" to reasonCode))

    // ---- Favorites (broken down by studio & location) --------------------

    fun favoriteAdded(
        type: String,
        studioId: Int?,
        studioSlug: String?,
        studioName: String?,
        locationId: Int? = null,
        locationName: String? = null,
    ) = track(
        Events.FAVORITE_ADDED,
        mapOf(
            "type" to type,
            "studio_id" to studioId,
            "studio_slug" to studioSlug,
            "studio_name" to studioName,
            "location_id" to locationId,
            "location_name" to locationName,
        ),
    )

    fun favoriteRemoved(
        type: String,
        studioId: Int?,
        studioSlug: String?,
        studioName: String?,
        locationId: Int? = null,
        locationName: String? = null,
    ) = track(
        Events.FAVORITE_REMOVED,
        mapOf(
            "type" to type,
            "studio_id" to studioId,
            "studio_slug" to studioSlug,
            "studio_name" to studioName,
            "location_id" to locationId,
            "location_name" to locationName,
        ),
    )

    fun favoritesSaved(
        studioCount: Int,
        locationCount: Int,
        studioSlugs: List<String>,
        locationIds: List<Int>,
    ) = track(
        Events.FAVORITES_SAVED,
        mapOf(
            "studio_count" to studioCount,
            "location_count" to locationCount,
            "studio_slugs" to studioSlugs.joinToString(","),
            "location_ids" to locationIds.joinToString(","),
        ),
    )

    /** The favorites list opened in the schedule filter panel (Favorites mode). */
    fun favoritesDropdownOpened(studioCount: Int, locationCount: Int) =
        track(
            Events.FAVORITES_DROPDOWN_OPENED,
            mapOf("studio_count" to studioCount, "location_count" to locationCount),
        )

    /** Member tapped "manage in Profile" from the schedule favorites list. */
    fun favoritesManageTapped() =
        track(Events.FAVORITES_MANAGE_TAPPED, mapOf("source" to "schedule_dropdown"))

    // ---- Concierge / support ---------------------------------------------

    fun conciergeSubmitted() = track(Events.CONCIERGE_SUBMITTED)

    fun conciergeFailed(reason: String) =
        track(Events.CONCIERGE_FAILED, mapOf("reason" to reason))

    companion object {
        private const val LOG_TAG = "Telemetry"

        /** A telemetry instance that records nothing — default for tests/previews. */
        val Noop: Telemetry = Telemetry(NoopAnalytics, NoopCrashReporter)
    }

    /** Event-name constants — the canonical PostHog event keys. */
    /** Values for [authRefreshFailed]'s `outcome`. */
    object RefreshFailureOutcome {
        /** The refresh endpoint rejected the token (401/403) — session really is dead. */
        const val REJECTED = "rejected"
        /** The request never completed (network/IO/timeout). Session kept. */
        const val TRANSIENT_EXCEPTION = "transient_exception"
        /** A non-auth status (5xx/429/...). Session kept. */
        const val TRANSIENT_STATUS = "transient_status"
        /** 2xx, but the body didn't parse / never fully arrived. Session kept. */
        const val TRANSIENT_BODY = "transient_body"
        /**
         * The server refreshed us (2xx) but the rotated refresh token was not
         * readable back out of storage immediately after being written. This is
         * the signature of the 2026-07-16 incident — it previously returned null
         * silently, leaving the client with no usable token and no trace.
         */
        const val STORED_REFRESH_MISSING = "stored_refresh_missing"
    }

    object Events {
        const val TAB_TAPPED = "tab_tapped"
        const val SCHEDULE_DAY_CHANGED = "schedule_day_changed"
        const val SCHEDULE_LOAD_MORE = "schedule_load_more"
        const val SCHEDULE_FILTER_CHANGED = "schedule_filter_changed"

        const val API_REQUEST = "api_request"
        const val APP_START_COMPLETED = "app_start_completed"
        const val SCREEN_LOAD_COMPLETED = "screen_load_completed"
        const val SCHEDULE_PAGE_LOADED = "schedule_page_loaded"

        const val SIGNUP_SURVEY_STARTED = "signup_survey_started"
        const val SIGNUP_SURVEY_SUBMITTED = "signup_survey_submitted"
        const val SIGNUP_SURVEY_FAILED = "signup_survey_failed"
        const val SIGNUP_SURVEY_SKIPPED = "signup_survey_skipped"

        const val SIGNUP_STARTED = "signup_started"
        const val SIGNUP_SUBMITTED = "signup_submitted"
        const val SIGNUP_FAILED = "signup_failed"
        const val SIGNUP_COMPLETED = "signup_completed"

        const val LOGIN_SUBMITTED = "login_submitted"
        const val LOGIN_FAILED = "login_failed"
        const val LOGIN_SUCCEEDED = "login_succeeded"
        const val LOGOUT = "logout"
        const val FORCED_LOGOUT = "forced_logout"
        const val TOKEN_STORAGE_FAILURE = "token_storage_failure"
        const val AUTH_REFRESH_FAILED = "auth_refresh_failed"

        const val CLASS_VIEWED = "class_viewed"
        const val CLASS_VIEW_FAILED = "class_view_failed"
        const val BOOKING_SHEET_OPENED = "booking_sheet_opened"
        const val SPOT_SELECTED = "spot_selected"
        const val STUDIO_VISIT_PROMPT_SHOWN = "studio_visit_prompt_shown"
        const val STUDIO_VISIT_ANSWERED = "studio_visit_answered"
        const val BOOKING_SUBMITTED = "booking_submitted"
        const val BOOKING_SUCCEEDED = "booking_succeeded"
        const val BOOKING_FAILED = "booking_failed"
        const val BOOKING_SHEET_ABANDONED = "booking_sheet_abandoned"

        const val BOOKING_CANCEL_STARTED = "booking_cancel_started"
        const val BOOKING_CANCELLED = "booking_cancelled"
        const val BOOKING_CANCEL_FAILED = "booking_cancel_failed"

        const val FAVORITE_ADDED = "favorite_added"
        const val FAVORITE_REMOVED = "favorite_removed"
        const val FAVORITES_SAVED = "favorites_saved"
        const val FAVORITES_DROPDOWN_OPENED = "favorites_dropdown_opened"
        const val FAVORITES_MANAGE_TAPPED = "favorites_manage_tapped"

        const val CONCIERGE_SUBMITTED = "concierge_request_submitted"
        const val CONCIERGE_FAILED = "concierge_request_failed"
    }

    /** Canonical `$screen` names (kept stable for dashboards). */
    object Screens {
        const val HOME = "Home"
        const val SCHEDULE = "Schedule"
        const val PROFILE = "Profile"
        const val STUDIO_SELECTION = "StudioSelection"
        const val MY_BOOKINGS = "MyBookings"
        const val CONCIERGE_REQUEST = "ConciergeRequest"
        const val EDIT_PROFILE = "EditProfile"
        const val CLASS_DETAIL = "ClassDetail"
        const val AUTH = "Auth"
        const val PASSWORD_RESET_REQUEST = "PasswordResetRequest"
        const val SIGNUP = "SignupCompletion"
        const val SIGNUP_SURVEY = "SignupSurvey"
    }
}
