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

    // ---- Signup funnel ----------------------------------------------------

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

    fun forcedLogout(cause: String) =
        track(Events.FORCED_LOGOUT, mapOf("type" to "forced", "cause" to cause))

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

    fun bookingCancelFailed(bookingId: Int) =
        track(Events.BOOKING_CANCEL_FAILED, mapOf("booking_id" to bookingId))

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
    object Events {
        const val TAB_TAPPED = "tab_tapped"
        const val SCHEDULE_DAY_CHANGED = "schedule_day_changed"
        const val SCHEDULE_LOAD_MORE = "schedule_load_more"
        const val SCHEDULE_FILTER_CHANGED = "schedule_filter_changed"

        const val SIGNUP_STARTED = "signup_started"
        const val SIGNUP_SUBMITTED = "signup_submitted"
        const val SIGNUP_FAILED = "signup_failed"
        const val SIGNUP_COMPLETED = "signup_completed"

        const val LOGIN_SUBMITTED = "login_submitted"
        const val LOGIN_FAILED = "login_failed"
        const val LOGIN_SUCCEEDED = "login_succeeded"
        const val LOGOUT = "logout"
        const val FORCED_LOGOUT = "forced_logout"

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
        const val CLASS_DETAIL = "ClassDetail"
        const val AUTH = "Auth"
        const val PASSWORD_RESET_REQUEST = "PasswordResetRequest"
        const val SIGNUP = "SignupCompletion"
    }
}
