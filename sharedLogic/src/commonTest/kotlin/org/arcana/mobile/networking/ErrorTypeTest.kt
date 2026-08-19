package org.arcana.mobile.networking

import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException

class ErrorTypeTest {

    @Test
    fun `status 0 is CONNECTION because the request never completed`() {
        assertEquals(ErrorType.CONNECTION, errorTypeForStatus(0))
    }

    @Test
    fun `5xx is SERVER`() {
        assertEquals(ErrorType.SERVER, errorTypeForStatus(500))
        assertEquals(ErrorType.SERVER, errorTypeForStatus(503))
    }

    @Test
    fun `unexpected non-auth 4xx is SERVER because the server did answer`() {
        assertEquals(ErrorType.SERVER, errorTypeForStatus(404))
        assertEquals(ErrorType.SERVER, errorTypeForStatus(409))
    }

    @Test
    fun `a plain exception is CONNECTION because it never reached the server`() {
        assertEquals(ErrorType.CONNECTION, Exception("boom").toErrorType())
        assertEquals(ErrorType.CONNECTION, IOException("socket closed").toErrorType())
    }

    // The reason Task 2 exists. HttpTimeout throws these, and neither is a
    // ResponseException, so both must land in CONNECTION: a timeout received
    // no HTTP response, so blaming the server would repeat the original bug.
    @Test
    fun `Ktor timeout exceptions are CONNECTION rather than SERVER`() {
        assertEquals(
            ErrorType.CONNECTION,
            HttpRequestTimeoutException("https://api.arcana.fit", 30_000L).toErrorType(),
        )
        assertEquals(
            ErrorType.CONNECTION,
            SocketTimeoutException("timed out").toErrorType(),
        )
    }

    // Guard: the UI category and the `api_request` telemetry outcome are
    // defined against the same buckets and must never drift apart.
    //
    // The expectations below are LITERAL on purpose. Recomputing them from
    // `apiRequestOutcome` (as this test first did) moves both sides of the
    // assertion together, so a change to that function's buckets would slip
    // through green. Written out, a bucket-boundary change fails here.
    @Test
    fun `errorTypeForStatus maps each representative status to a fixed category`() {
        val expectations = listOf(
            0 to ErrorType.CONNECTION,
            302 to ErrorType.SERVER,
            401 to ErrorType.SERVER,
            403 to ErrorType.SERVER,
            404 to ErrorType.SERVER,
            409 to ErrorType.SERVER,
            418 to ErrorType.SERVER,
            500 to ErrorType.SERVER,
            502 to ErrorType.SERVER,
            503 to ErrorType.SERVER,
        )
        expectations.forEach { (status, expected) ->
            assertEquals(expected, errorTypeForStatus(status), "status $status")
        }
    }

    // ---- The defect this file exists to close --------------------------
    // `ResponseException` is never thrown by this app: the client runs with
    // `expectSuccess = false` and installs no HttpResponseValidator. Every
    // status-carrying failure therefore arrives as one of the app's OWN typed
    // exceptions, and each must reach `errorTypeForStatus` rather than falling
    // through to the CONNECTION default.

    @Test
    fun `ApiHttpError 5xx is SERVER`() {
        assertEquals(ErrorType.SERVER, ApiHttpError(500).toErrorType())
        assertEquals(ErrorType.SERVER, ApiHttpError(503).toErrorType())
    }

    @Test
    fun `ApiHttpError 404 is SERVER because the server did answer`() {
        assertEquals(ErrorType.SERVER, ApiHttpError(404).toErrorType())
    }

    // ---- 401/403: deliberate SERVER, not an accident of apiRequestOutcome's
    // client_error bucket. See the ErrorType KDoc for the token-refresh
    // mechanism that lets a 401 reach this classifier at all, and why SERVER
    // (not CONNECTION) is the product-ratified answer for both statuses.

    @Test
    fun `ApiHttpError 401 is SERVER`() {
        assertEquals(ErrorType.SERVER, ApiHttpError(401).toErrorType())
    }

    @Test
    fun `ApiHttpError 403 is SERVER`() {
        assertEquals(ErrorType.SERVER, ApiHttpError(403).toErrorType())
    }

    @Test
    fun `LoginError carrying a 5xx status is SERVER`() {
        assertEquals(ErrorType.SERVER, LoginError(500).toErrorType())
        assertEquals(ErrorType.SERVER, LoginError(503).toErrorType())
    }

    // The trap: login() throws LoginError(status) for ANY non-200, so a wrong
    // password is LoginError(401) — not a dead session, just bad credentials.
    // No production caller routes this through toErrorType() today:
    // AuthViewModel catches LoginError itself and renders its own
    // credential-error copy. This test exists because later tasks are adding
    // more toErrorType() call sites, and if login's error path is ever
    // migrated to go through one, a wrong password would silently become
    // "server error" copy instead of "check your email and password." Pinning
    // SERVER here (the same answer errorTypeForStatus already gives any 401)
    // makes that trap visible instead of an unpleasant surprise.
    @Test
    fun `LoginError 401 is SERVER even though it usually just means a wrong password`() {
        assertEquals(ErrorType.SERVER, LoginError(401).toErrorType())
    }

    @Test
    fun `PasswordResetRequestError carrying a 5xx status is SERVER`() {
        assertEquals(ErrorType.SERVER, PasswordResetRequestError(502).toErrorType())
    }

    // The other half of the contract: adding status-carrying types must not
    // start blaming the server for failures that never reached it.
    @Test
    fun `a serialization failure never proves the server answered so it stays CONNECTION`() {
        assertEquals(
            ErrorType.CONNECTION,
            SerializationException("Unexpected JSON token").toErrorType(),
        )
    }

    // ---- telemetryReasonFor: same classification, keeps the status ------

    @Test
    fun `telemetryReasonFor keeps the status for an ApiHttpError`() {
        assertEquals("server_500", ApiHttpError(500).telemetryReasonFor())
    }

    @Test
    fun `telemetryReasonFor keeps the status for a LoginError`() {
        assertEquals("server_503", LoginError(503).telemetryReasonFor())
    }

    @Test
    fun `telemetryReasonFor falls back to network for a plain exception`() {
        assertEquals("network", Exception("boom").telemetryReasonFor())
    }
}
