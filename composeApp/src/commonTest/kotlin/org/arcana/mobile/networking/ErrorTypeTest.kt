package org.arcana.mobile.networking

import org.arcana.mobile.analytics.apiRequestOutcome
import kotlin.test.Test
import kotlin.test.assertEquals

class ErrorTypeTest {
    @Test fun `status 0 means the request never reached the server`() {
        // The exact bug that told a Member "server error" while the server was
        // healthy: no response was ever received.
        assertEquals(ErrorType.CONNECTION, errorTypeForStatus(0))
    }

    @Test fun `5xx is a server failure`() {
        assertEquals(ErrorType.SERVER, errorTypeForStatus(500))
        assertEquals(ErrorType.SERVER, errorTypeForStatus(502))
        assertEquals(ErrorType.SERVER, errorTypeForStatus(503))
    }

    @Test fun `unexpected non-auth 4xx is a server failure`() {
        // A Member can't act on these, and they are not a connection problem,
        // so they read as "on our end" rather than "check your connection".
        assertEquals(ErrorType.SERVER, errorTypeForStatus(400))
        assertEquals(ErrorType.SERVER, errorTypeForStatus(404))
        assertEquals(ErrorType.SERVER, errorTypeForStatus(422))
    }

    @Test fun `a throwable with no response is a connection failure`() {
        assertEquals(ErrorType.CONNECTION, RuntimeException("connection reset").toErrorType())
        assertEquals(ErrorType.CONNECTION, IllegalStateException("timeout").toErrorType())
    }

    @Test fun `never disagrees with the api_request telemetry buckets`() {
        // Guard against drift: if apiRequestOutcome's bucketing changes, the UI
        // classification must move with it or this fails.
        listOf(0, 200, 204, 301, 400, 401, 404, 422, 500, 502, 503).forEach { status ->
            val expected =
                if (apiRequestOutcome(status) == "network_error") ErrorType.CONNECTION else ErrorType.SERVER
            assertEquals(expected, errorTypeForStatus(status), "status $status")
        }
    }
}
