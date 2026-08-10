package org.arcana.mobile.session

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Framework-agnostic session orchestration, extracted from App.kt so the
 * welcome-token state machine, the survey-done gate, first-launch recovery,
 * and session teardown are plain testable Kotlin instead of logic embedded in
 * Composables. The UI layer (App.kt today; any future shell) collects
 * [welcomeToken]/[isAuthenticated] and calls the event methods — it holds no
 * session rules of its own.
 *
 * Wired in Koin (see di/AppModule.kt) against ArcanaApiClient.isAuthenticated,
 * SecureStorage, FavoritesRepository, and PendingTokenSource; constructor takes
 * narrow lambdas so tests need no fakes of those concrete types.
 */
class AppSessionController(
    /** App-wide auth state — mirrors ArcanaApiClient.isAuthenticated. */
    val isAuthenticated: StateFlow<Boolean>,
    private val loadKey: (String) -> String?,
    private val saveKey: (key: String, value: String) -> Unit,
    /** Wipes session-scoped singletons that outlive the UI ViewModelStore
     *  (today: FavoritesRepository) so the next member sees no stale data. */
    private val onSessionCleared: () -> Unit,
    /** Platform recovery source (iOS: none; Android: Install Referrer). */
    private val pendingTokenProvider: suspend () -> String?,
) {
    companion object {
        const val RECOVERY_ATTEMPTED_KEY = "first_launch_recovery_attempted"

        /**
         * SecureStorage key prefix marking the onboarding survey done for one
         * signup token. Persisted (not just in-memory) so a member who finished
         * the survey, backed out of claim-your-name, and re-tapped their email
         * link goes straight to the claim screen — the survey is one-and-done
         * per link.
         */
        const val SURVEY_DONE_KEY_PREFIX = "signup_survey_done:"

        /** Lets a cold-start deep link land before first-launch recovery runs,
         *  so a deep-link launch never triggers the platform recovery source
         *  (on iOS that would mean a pasteboard permission prompt). */
        const val RECOVERY_DEEP_LINK_GRACE_MS = 700L
    }

    private val _welcomeToken = MutableStateFlow<String?>(null)

    /** Pending welcome deep-link token; null when none is in flight. */
    val welcomeToken: StateFlow<String?> = _welcomeToken.asStateFlow()

    /** A (re-)delivered deep-link token — cold or warm start, either platform. */
    fun onDeepLinkToken(token: String?) {
        if (token != null) _welcomeToken.value = token
    }

    /** Clears any pending token. Returns true when one was actually pending —
     *  callers only notify the platform (to drop its pending-link reference)
     *  when this is true, mirroring the original App.kt semantics. */
    fun consumeWelcomeToken(): Boolean {
        val had = _welcomeToken.value != null
        _welcomeToken.value = null
        return had
    }

    fun isSurveyDone(token: String): Boolean =
        loadKey(SURVEY_DONE_KEY_PREFIX + token) == "1"

    fun markSurveyDone(token: String) {
        saveKey(SURVEY_DONE_KEY_PREFIX + token, "1")
    }

    /**
     * First-launch welcome-token recovery — runs at most ONCE per install, and
     * only when signed out with no deep link pending. The grace delay lets a
     * cold-start deep link arrive first (the platform bridge feeds
     * [onDeepLinkToken]), so a deep-link launch never consults the recovery
     * source. Marks the attempt persistently in every non-authenticated case,
     * matching the pre-extraction behavior.
     */
    suspend fun attemptFirstLaunchRecovery() {
        if (isAuthenticated.value) return
        if (loadKey(RECOVERY_ATTEMPTED_KEY) == "1") return
        delay(RECOVERY_DEEP_LINK_GRACE_MS)
        if (_welcomeToken.value == null && !isAuthenticated.value) {
            pendingTokenProvider()?.let { _welcomeToken.value = it }
        }
        saveKey(RECOVERY_ATTEMPTED_KEY, "1")
    }

    /** Auth flipped ON (login or completed signup). Returns true when a pending
     *  welcome token was consumed by the flip (caller then notifies platform). */
    fun onAuthenticated(): Boolean = consumeWelcomeToken()

    /** Auth flipped OFF (manual or forced logout): wipe session-scoped data.
     *  The UI layer separately clears its ViewModelStore. */
    fun onSessionEnded() {
        onSessionCleared()
    }
}
