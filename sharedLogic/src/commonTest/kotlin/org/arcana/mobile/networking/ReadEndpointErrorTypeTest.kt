package org.arcana.mobile.networking

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * End-to-end proof for the read endpoints: a real Ktor response pipeline (status
 * + body, no network) must turn a 5xx into [ErrorType.SERVER].
 *
 * This is deliberately NOT a re-assertion of `toErrorType`'s `when`. Those tests
 * hand-construct the exception and so would stay green even if the endpoints
 * never produced one. Here the exception is produced the way production produces
 * it: by the same `client.get(...)` + body-read shape `ArcanaApiClient`'s read
 * endpoints use, against a client configured like the production one
 * (`expectSuccess` left at its `false` default, no HttpResponseValidator).
 */
class ReadEndpointErrorTypeTest {

    /** Stands in for any read DTO (`MembershipMeDto`, `MyBookingsDto`, ...). */
    @Serializable
    private data class FakeDto(val id: Int)

    /** Stands in for the FULLY-DEFAULTED read DTOs — `FavoritesDto`,
     *  `ScheduleOverviewDto`, `SchedulePageDto`, `MeProfileDto`. Every field has
     *  a default, which is what makes the bug below possible. */
    @Serializable
    private data class FakeDefaultedDto(val items: List<String> = emptyList())

    /** A client configured exactly like `ArcanaApiClient`'s in the ways that
     *  matter here: JSON negotiation on, `expectSuccess` untouched (false). */
    private fun clientReturning(status: HttpStatusCode, body: String): HttpClient =
        HttpClient(
            MockEngine {
                respond(
                    content = body,
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        ) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

    private val serverErrorBody = """{"detail":"Internal Server Error"}"""

    @Test
    fun `a 500 from a read endpoint classifies as SERVER`() = runTest {
        val client = clientReturning(HttpStatusCode.InternalServerError, serverErrorBody)

        val thrown = assertNotNull(
            runCatching { client.get("/api/v1/memberships/me").bodyOrThrow<FakeDto>() }.exceptionOrNull(),
            "a 500 must not be reported as a successful read",
        )

        assertEquals(ErrorType.SERVER, thrown.toErrorType())
    }

    @Test
    fun `a 503 from a read endpoint classifies as SERVER`() = runTest {
        val client = clientReturning(HttpStatusCode.ServiceUnavailable, serverErrorBody)

        val thrown = assertNotNull(
            runCatching { client.get("/api/v1/bookings/me/").bodyOrThrow<FakeDto>() }.exceptionOrNull(),
            "a 503 must not be reported as a successful read",
        )

        assertEquals(ErrorType.SERVER, thrown.toErrorType())
    }

    @Test
    fun `a 404 from a read endpoint classifies as SERVER`() = runTest {
        val client = clientReturning(HttpStatusCode.NotFound, """{"detail":"Not found."}""")

        val thrown = assertNotNull(
            runCatching { client.get("/api/v1/classes/1/").bodyOrThrow<FakeDto>() }.exceptionOrNull(),
            "a 404 must not be reported as a successful read",
        )

        assertEquals(ErrorType.SERVER, thrown.toErrorType())
    }

    @Test
    fun `the failure status survives on the exception`() = runTest {
        val client = clientReturning(HttpStatusCode.BadGateway, serverErrorBody)

        val thrown = runCatching {
            client.get("/api/v1/memberships/me").bodyOrThrow<FakeDto>()
        }.exceptionOrNull()

        assertIs<ApiHttpError>(thrown, "the status must reach the classifier")

        assertEquals(502, thrown.statusCode)
    }

    @Test
    fun `a 200 from a read endpoint still deserializes normally`() = runTest {
        val client = clientReturning(HttpStatusCode.OK, """{"id":7}""")

        val dto: FakeDto = client.get("/api/v1/memberships/me").bodyOrThrow()

        assertEquals(7, dto.id)
    }

    /**
     * The trap [bodyOrThrow] exists to close — locked in so nobody "simplifies"
     * a read endpoint back to a bare `.body()`.
     *
     * On a failure status the body is the server's error payload, so
     * deserializing it into the DTO throws a `JsonConvertException`. That
     * carries no status, so it classifies as CONNECTION: the member is told to
     * check their connection while the server is down. This asserts the broken
     * behavior on purpose — it is the regression guard, not the desired path.
     */
    @Test
    fun `a bare body call on a 500 loses the status and misclassifies`() = runTest {
        val client = clientReturning(HttpStatusCode.InternalServerError, serverErrorBody)

        val thrown = assertNotNull(
            runCatching { client.get("/api/v1/memberships/me").body<FakeDto>() }.exceptionOrNull(),
            "a 500 body read must fail somehow",
        )

        assertTrue(thrown !is ApiHttpError, "a bare .body() cannot carry the status")
        assertEquals(
            ErrorType.CONNECTION,
            thrown.toErrorType(),
            "this is the defect: use bodyOrThrow() on read endpoints",
        )
    }

    /**
     * The worse half of the same defect, on the fully-defaulted DTOs.
     *
     * `FavoritesDto`, `ScheduleOverviewDto`, `SchedulePageDto` and `MeProfileDto`
     * default every field, and the client parses with `ignoreUnknownKeys = true`.
     * So a 5xx error body does not merely throw the wrong exception: it
     * DESERIALIZES CLEANLY into an empty DTO and the read reports success. The
     * member sees an empty schedule / no favorites / a blank profile instead of
     * an error, and `FavoritesRepository` would cache that emptiness over their
     * real favorites.
     *
     * This test pins the raw-`.body()` hazard so the reason the endpoints must
     * use [bodyOrThrow] cannot be quietly undone.
     */
    @Test
    fun `a bare body call on a 500 silently yields an empty defaulted DTO`() = runTest {
        val client = clientReturning(HttpStatusCode.InternalServerError, serverErrorBody)

        val parsed: FakeDefaultedDto = client.get("/api/v1/users/me/favorites/").body()

        assertEquals(
            FakeDefaultedDto(),
            parsed,
            "a 500 parsed as a successful empty result — the false-empty-state bug",
        )
    }

    @Test
    fun `bodyOrThrow turns that silent empty result into a SERVER failure`() = runTest {
        val client = clientReturning(HttpStatusCode.InternalServerError, serverErrorBody)

        val thrown = assertNotNull(
            runCatching {
                client.get("/api/v1/users/me/favorites/").bodyOrThrow<FakeDefaultedDto>()
            }.exceptionOrNull(),
            "a 500 must never surface as an empty success",
        )

        assertEquals(ErrorType.SERVER, thrown.toErrorType())
    }
}
