package org.arcana.mobile.networking

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

/**
 * The timeouts each request actually carries to the engine, read off the request
 * the engine receives. Socket timeouts cannot be simulated here (MockEngine answers
 * at once); the stall itself is measured on device (docs/regression/inventory.md ERR-22).
 */
class TimeoutsTest {

    private val seen = mutableListOf<HttpTimeoutConfig?>()

    private val client = HttpClient(
        MockEngine { request ->
            seen += request.getCapabilityOrNull(HttpTimeoutCapability)
            respond("{}", HttpStatusCode.OK)
        },
    ) { installTimeouts() }

    private fun assertTimeouts(socket: Long, request: Long, config: HttpTimeoutConfig?) {
        assertEquals(Timeouts.CONNECT_MS, config?.connectTimeoutMillis)
        assertEquals(socket, config?.socketTimeoutMillis)
        assertEquals(request, config?.requestTimeoutMillis)
    }

    @Test fun `a read gives up after ten seconds of silence`() = runTest {
        client.get("https://example.test/api/v1/discover/studios/")
        assertTimeouts(10_000, 20_000, seen.single())
    }

    @Test fun `every write keeps the long timeouts a booking needs`() = runTest {
        client.post("https://example.test/api/v1/bookings/")
        client.delete("https://example.test/api/v1/bookings/1/")
        client.patch("https://example.test/api/v1/bookings/1/review/")
        assertEquals(3, seen.size)
        seen.forEach { assertTimeouts(30_000, 60_000, it) }
    }
}
