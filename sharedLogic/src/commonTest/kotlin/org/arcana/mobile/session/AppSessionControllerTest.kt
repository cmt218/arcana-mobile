package org.arcana.mobile.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Locks the session state machine extracted from App.kt: the welcome-token
 * lifecycle, the survey-done gate, first-launch recovery semantics, and
 * session teardown. These rules guard the signup deep-link flow and the
 * logout data-wipe — regressions here are member-visible.
 */
class AppSessionControllerTest {

    private class Harness(
        authenticated: Boolean = false,
        preAttempted: Boolean = false,
        var pendingToken: String? = null,
    ) {
        val auth = MutableStateFlow(authenticated)
        val store = mutableMapOf<String, String>().apply {
            if (preAttempted) put(AppSessionController.RECOVERY_ATTEMPTED_KEY, "1")
        }
        var cleared = 0
        var providerCalls = 0
        val controller = AppSessionController(
            isAuthenticated = auth,
            loadKey = { store[it] },
            saveKey = { k, v -> store[k] = v },
            onSessionCleared = { cleared++ },
            pendingTokenProvider = { providerCalls++; pendingToken },
        )
    }

    // ── Welcome-token machine ────────────────────────────────────────────

    @Test
    fun deepLinkTokenIsExposedAndNullIsIgnored() {
        val h = Harness()
        assertNull(h.controller.welcomeToken.value)
        h.controller.onDeepLinkToken(null)
        assertNull(h.controller.welcomeToken.value)
        h.controller.onDeepLinkToken("tok-1")
        assertEquals("tok-1", h.controller.welcomeToken.value)
        // A later link replaces the pending token (warm-start re-delivery).
        h.controller.onDeepLinkToken("tok-2")
        assertEquals("tok-2", h.controller.welcomeToken.value)
    }

    @Test
    fun consumeReturnsWhetherATokenWasPending() {
        val h = Harness()
        assertFalse(h.controller.consumeWelcomeToken())
        h.controller.onDeepLinkToken("tok")
        assertTrue(h.controller.consumeWelcomeToken())
        assertNull(h.controller.welcomeToken.value)
        assertFalse(h.controller.consumeWelcomeToken())
    }

    @Test
    fun authFlipConsumesPendingTokenAndReportsIt() {
        val h = Harness()
        h.controller.onDeepLinkToken("tok")
        assertTrue(h.controller.onAuthenticated())
        assertNull(h.controller.welcomeToken.value)
        // A normal logged-in launch (no token) must NOT report a consumption —
        // App.kt only fires onWelcomeTokenConsumed when this is true.
        assertFalse(h.controller.onAuthenticated())
    }

    // ── Survey gate ──────────────────────────────────────────────────────

    @Test
    fun surveyDoneGateIsPerTokenAndPersisted() {
        val h = Harness()
        assertFalse(h.controller.isSurveyDone("tok-a"))
        h.controller.markSurveyDone("tok-a")
        assertTrue(h.controller.isSurveyDone("tok-a"))
        assertFalse(h.controller.isSurveyDone("tok-b"))
        assertEquals("1", h.store[AppSessionController.SURVEY_DONE_KEY_PREFIX + "tok-a"])
    }

    // ── First-launch recovery ────────────────────────────────────────────

    @Test
    fun recoveryRecoversTokenAndMarksAttempted() = runTest {
        val h = Harness(pendingToken = "recovered")
        h.controller.attemptFirstLaunchRecovery()
        assertEquals("recovered", h.controller.welcomeToken.value)
        assertEquals(1, h.providerCalls)
        assertEquals("1", h.store[AppSessionController.RECOVERY_ATTEMPTED_KEY])
    }

    @Test
    fun recoveryMarksAttemptedEvenWhenNothingRecovered() = runTest {
        val h = Harness(pendingToken = null)
        h.controller.attemptFirstLaunchRecovery()
        assertNull(h.controller.welcomeToken.value)
        assertEquals("1", h.store[AppSessionController.RECOVERY_ATTEMPTED_KEY])
    }

    @Test
    fun recoveryRunsAtMostOncePerInstall() = runTest {
        val h = Harness(preAttempted = true, pendingToken = "late")
        h.controller.attemptFirstLaunchRecovery()
        assertEquals(0, h.providerCalls)
        assertNull(h.controller.welcomeToken.value)
    }

    @Test
    fun recoverySkipsWhenAuthenticatedAndDoesNotMarkAttempted() = runTest {
        val h = Harness(authenticated = true, pendingToken = "x")
        h.controller.attemptFirstLaunchRecovery()
        assertEquals(0, h.providerCalls)
        // Pre-extraction behavior: the authenticated early-return happens
        // BEFORE the attempted flag is written, so a later signed-out launch
        // still gets its one recovery attempt.
        assertNull(h.store[AppSessionController.RECOVERY_ATTEMPTED_KEY])
    }

    @Test
    fun recoveryYieldsToADeepLinkThatArrivesDuringTheGraceDelay() = runTest {
        val h = Harness(pendingToken = "should-not-win")
        // Simulate the deep link landing while recovery is inside its delay:
        // launch recovery, then deliver the link before advancing virtual time.
        val job = launch { h.controller.attemptFirstLaunchRecovery() }
        h.controller.onDeepLinkToken("deep-link")
        job.join()
        assertEquals("deep-link", h.controller.welcomeToken.value)
        assertEquals(0, h.providerCalls)
        assertEquals("1", h.store[AppSessionController.RECOVERY_ATTEMPTED_KEY])
    }

    // ── Teardown ─────────────────────────────────────────────────────────

    @Test
    fun sessionEndClearsSessionScopedData() {
        val h = Harness()
        h.controller.onSessionEnded()
        assertEquals(1, h.cleared)
    }
}
