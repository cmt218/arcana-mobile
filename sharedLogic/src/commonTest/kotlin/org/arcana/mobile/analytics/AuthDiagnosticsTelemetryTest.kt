package org.arcana.mobile.analytics

import org.arcana.mobile.auth.SecureStorageDiagnostics
import org.arcana.mobile.auth.TokenStorage
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Locks the auth-diagnostics taxonomy added after the 2026-07-16 forced logout,
 * which could not be explained because the storage and refresh layers failed
 * silently.
 *
 * These events exist to answer one question the next time it happens: when the
 * token came back null, *why* — was the store locked, was the token genuinely
 * gone, or did a write fail? If an edit drops these properties, the next
 * incident becomes archaeology again, so they're pinned here.
 */
class AuthDiagnosticsTelemetryTest {

    @AfterTest fun tearDown() = SecureStorageDiagnostics.resetForTest()

    /** A 401 arrived (so an access token existed) but the paired refresh read
     *  back null. That is unproven, not a rejection, so it must report and keep
     *  the session — never forceLogout. Carrying the OSStatus matters: this
     *  path used to emit `forced_logout`, which was the only event with it. */
    @Test
    fun noStoredRefreshReportsStorageContextWithoutLoggingOut() {
        val (telemetry, analytics, _) = fakeTelemetry()

        telemetry.authRefreshFailed(
            Telemetry.RefreshFailureOutcome.NO_STORED_REFRESH,
            osStatus = -25308, // errSecInteractionNotAllowed — device was locked
            storageOp = SecureStorageDiagnostics.Op.LOAD,
        )

        val event = analytics.first(Telemetry.Events.AUTH_REFRESH_FAILED)
        assertEquals("no_stored_refresh", event?.properties?.get("outcome"))
        assertEquals(-25308, event?.properties?.get("storage_os_status"))
        assertEquals("load", event?.properties?.get("storage_op"))
        assertNull(
            analytics.first(Telemetry.Events.FORCED_LOGOUT),
            "a null storage read must not sign the member out",
        )
    }

    /** The two storage-shaped outcomes answer different questions: nothing to
     *  send, versus the rotated token vanishing right after a successful write.
     *  Collapsing them would hide which one is happening. */
    @Test
    fun theTwoStorageOutcomesStaySeparate() {
        assertEquals("no_stored_refresh", Telemetry.RefreshFailureOutcome.NO_STORED_REFRESH)
        assertEquals("stored_refresh_missing", Telemetry.RefreshFailureOutcome.STORED_REFRESH_MISSING)
    }

    @Test
    fun forcedLogoutCarriesStorageContext() {
        val (telemetry, analytics, _) = fakeTelemetry()

        telemetry.forcedLogout(
            cause = "refresh_missing",
            osStatus = -25308, // errSecInteractionNotAllowed — device was locked
            storageOp = SecureStorageDiagnostics.Op.LOAD,
            storageKey = "refresh_token",
        )

        val event = analytics.first(Telemetry.Events.FORCED_LOGOUT)
        assertEquals("forced", event?.properties?.get("type"))
        assertEquals("refresh_missing", event?.properties?.get("cause"))
        assertEquals(-25308, event?.properties?.get("storage_os_status"))
        assertEquals("load", event?.properties?.get("storage_op"))
        assertEquals("refresh_token", event?.properties?.get("storage_key"))
    }

    @Test
    fun forcedLogoutWithoutStorageFailureOmitsStorageProps() {
        val (telemetry, analytics, _) = fakeTelemetry()

        telemetry.forcedLogout("refresh_error")

        // Null props are filtered out by `track`, so absence — not a null value —
        // is what "the store reported no failure" looks like downstream.
        val props = analytics.first(Telemetry.Events.FORCED_LOGOUT)?.properties.orEmpty()
        assertEquals("refresh_error", props["cause"])
        assertTrue("storage_os_status" !in props)
        assertTrue("storage_op" !in props)
    }

    @Test
    fun tokenStorageFailureCarriesOpKeyAndStatus() {
        val (telemetry, analytics, _) = fakeTelemetry()

        telemetry.tokenStorageFailure(SecureStorageDiagnostics.Op.SAVE, "refresh_token", -34018)

        val event = analytics.first(Telemetry.Events.TOKEN_STORAGE_FAILURE)
        assertEquals("save", event?.properties?.get("op"))
        assertEquals("refresh_token", event?.properties?.get("key"))
        assertEquals(-34018, event?.properties?.get("os_status"))
    }

    @Test
    fun authRefreshFailedCarriesOutcomeAndStatus() {
        val (telemetry, analytics, _) = fakeTelemetry()

        telemetry.authRefreshFailed(Telemetry.RefreshFailureOutcome.STORED_REFRESH_MISSING, 200)

        val event = analytics.first(Telemetry.Events.AUTH_REFRESH_FAILED)
        assertEquals("stored_refresh_missing", event?.properties?.get("outcome"))
        assertEquals(200, event?.properties?.get("status_code"))
    }

    @Test
    fun authRefreshFailedOmitsStatusWhenRequestNeverCompleted() {
        val (telemetry, analytics, _) = fakeTelemetry()

        telemetry.authRefreshFailed(Telemetry.RefreshFailureOutcome.TRANSIENT_EXCEPTION)

        val props = analytics.first(Telemetry.Events.AUTH_REFRESH_FAILED)?.properties.orEmpty()
        assertEquals("transient_exception", props["outcome"])
        assertTrue("status_code" !in props)
    }

    @Test
    fun diagnosticsRecordsFailurePerKeyAndNotifiesListener() {
        val seen = mutableListOf<SecureStorageDiagnostics.Failure>()
        SecureStorageDiagnostics.listener = { seen += it }

        SecureStorageDiagnostics.report(
            SecureStorageDiagnostics.Op.LOAD, TokenStorage.REFRESH_TOKEN_KEY, -25308,
        )

        assertEquals(1, seen.size)
        assertEquals(-25308, seen.single().status)
        assertEquals(
            SecureStorageDiagnostics.Failure("load", "refresh_token", -25308),
            SecureStorageDiagnostics.lastFailureFor(TokenStorage.REFRESH_TOKEN_KEY),
        )
    }

    @Test
    fun anUnrelatedKeysFailureIsNotAttributedToTheRefreshToken() {
        // The store also holds base_url_override / survey flags, which are absent
        // for most members. A miss there must never be reported as the reason a
        // token was null — that would be worse than reporting nothing.
        SecureStorageDiagnostics.report(
            SecureStorageDiagnostics.Op.LOAD, "base_url_override", -25300, notable = false,
        )

        assertNull(SecureStorageDiagnostics.lastFailureFor(TokenStorage.REFRESH_TOKEN_KEY))
        assertEquals(-25300, SecureStorageDiagnostics.lastFailureFor("base_url_override")?.status)
    }

    @Test
    fun routineNotFoundIsRecordedButNotEmittedAsAnEvent() {
        // notable=false: a signed-out member's absent token is expected and would
        // be pure event noise, but must still be available to explain a logout.
        val seen = mutableListOf<SecureStorageDiagnostics.Failure>()
        SecureStorageDiagnostics.listener = { seen += it }

        SecureStorageDiagnostics.report(
            SecureStorageDiagnostics.Op.LOAD, TokenStorage.REFRESH_TOKEN_KEY, -25300, notable = false,
        )

        assertTrue(seen.isEmpty())
        assertEquals(-25300, SecureStorageDiagnostics.lastFailureFor(TokenStorage.REFRESH_TOKEN_KEY)?.status)
    }

    @Test
    fun diagnosticsRecordsFailureEvenWithNoListenerAttached() {
        // Failures can happen before Telemetry exists (DI wires the listener when
        // Telemetry is constructed). They must still be available to forced_logout.
        SecureStorageDiagnostics.resetForTest()
        assertNull(SecureStorageDiagnostics.lastFailureFor(TokenStorage.ACCESS_TOKEN_KEY))

        SecureStorageDiagnostics.report(
            SecureStorageDiagnostics.Op.SAVE, TokenStorage.ACCESS_TOKEN_KEY, -25308,
        )

        assertEquals(-25308, SecureStorageDiagnostics.lastFailureFor(TokenStorage.ACCESS_TOKEN_KEY)?.status)
    }
}
