package org.arcana.mobile.networking

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodedPath
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * An expired ACCESS token with a healthy REFRESH token must never reach the
 * member as an error.
 *
 * The read endpoints now throw [ApiHttpError] on any non-2xx, so the obvious
 * worry is that the 401 which merely *triggers* a token refresh gets classified
 * and rendered as an error state. It must not: Ktor's `Auth` plugin refreshes
 * and REPLAYS the request inside the client pipeline, so the caller only ever
 * sees the replayed response. This proves that end to end rather than trusting
 * the plugin's documented behaviour.
 */
class RefreshDuringReadTest {

    @Serializable
    private data class FakeDto(val id: Int)

    private val payload = """{"id":7}"""
    private val refreshBody = """{"access":"access-2","refresh":"refresh-2"}"""

    /**
     * Mirrors the production client in the parts that decide this behaviour:
     * `expectSuccess` left false, JSON negotiation on, and a bearer provider
     * whose `refreshTokens` returns a new pair. [log] records the Authorization
     * header of every request so the replay can be told apart from the original.
     */
    private fun clientWithExpiredAccessToken(log: MutableList<String>): HttpClient {
        var access = "access-1"
        return HttpClient(
            MockEngine { request ->
                log += request.headers[HttpHeaders.Authorization] ?: "<none>"
                val json = headersOf(HttpHeaders.ContentType, "application/json")
                when {
                    request.url.encodedPath.contains("token/refresh") -> {
                        access = "access-2"
                        respond(refreshBody, HttpStatusCode.OK, json)
                    }
                    // The stale token is rejected; the refreshed one is accepted.
                    request.headers[HttpHeaders.Authorization] == "Bearer access-1" ->
                        respond("""{"detail":"token expired"}""", HttpStatusCode.Unauthorized, json)
                    else -> respond(payload, HttpStatusCode.OK, json)
                }
            },
        ) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(Auth) {
                bearer {
                    loadTokens { BearerTokens(access, "refresh-1") }
                    refreshTokens { BearerTokens("access-2", "refresh-2") }
                    sendWithoutRequest { !it.url.encodedPath.contains("token") }
                }
            }
        }
    }

    @Test
    fun `a 401 that a good refresh token recovers never reaches the caller as an error`() = runTest {
        val log = mutableListOf<String>()
        val client = clientWithExpiredAccessToken(log)

        val dto: FakeDto = client.get("https://example.test/api/v1/memberships/me").bodyOrThrow()

        assertEquals(7, dto.id, "the replayed request's body must be what the caller receives")
        assertTrue(
            log.any { it == "Bearer access-1" },
            "the stale token should have been tried first, or this proves nothing: $log",
        )
        assertTrue(
            log.any { it == "Bearer access-2" },
            "the request must be replayed with the refreshed token: $log",
        )
    }

    @Test
    fun `the recovered 401 never reaches the classifier so no error state can be built from it`() = runTest {
        val log = mutableListOf<String>()
        val client = clientWithExpiredAccessToken(log)

        // bodyOrThrow throwing here is exactly the regression this guards: it
        // would surface as a full-screen SERVER error on a session that is fine.
        val response = client.get("https://example.test/api/v1/bookings/me/")
        assertEquals(
            HttpStatusCode.OK,
            response.status,
            "caller sees the replay, not the 401 that triggered the refresh",
        )
        assertEquals(ErrorType.SERVER, errorTypeForStatus(401), "401 still classifies as SERVER when it does reach us")
    }
}
