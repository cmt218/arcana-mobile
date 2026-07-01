package org.arcana.mobile.networking

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Locks the token-refresh decision table that gates forced logout.
 *
 * The rule the app depends on: a member is signed out ONLY when the refresh
 * endpoint genuinely rejects their token (401/403). Every other outcome — a
 * 5xx, a 429, a 4xx that isn't an auth rejection, or (handled in the client) a
 * request that never completes — must be TRANSIENT so the session survives and
 * the next request retries.
 *
 * Regression guard for the 2026-07-01 prod incident: a refresh the server
 * answered `200` threw client-side on cellular and force-logged-out a member
 * whose refresh token was still valid. Only [RefreshOutcome.REJECTED] may
 * trigger `forceLogout`.
 */
class RefreshOutcomeTest {

    @Test
    fun successStatusesRefresh() {
        for (status in listOf(200, 201, 204)) {
            assertEquals(RefreshOutcome.REFRESHED, refreshOutcomeForStatus(status), "status $status")
        }
    }

    @Test
    fun onlyUnauthorizedAndForbiddenReject() {
        assertEquals(RefreshOutcome.REJECTED, refreshOutcomeForStatus(401))
        assertEquals(RefreshOutcome.REJECTED, refreshOutcomeForStatus(403))
    }

    @Test
    fun serverAndRateLimitErrorsAreTransient() {
        for (status in listOf(500, 502, 503, 504, 429, 408)) {
            assertEquals(RefreshOutcome.TRANSIENT, refreshOutcomeForStatus(status), "status $status")
        }
    }

    @Test
    fun otherClientErrorsAreTransientNotLogout() {
        // A 400/404 from the refresh endpoint doesn't prove the token is dead,
        // so we keep the session rather than sign the member out.
        for (status in listOf(400, 404)) {
            assertEquals(RefreshOutcome.TRANSIENT, refreshOutcomeForStatus(status), "status $status")
        }
    }
}
