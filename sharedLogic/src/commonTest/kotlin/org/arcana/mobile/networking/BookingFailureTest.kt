package org.arcana.mobile.networking

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

/**
 * ERR-11 regression guard for `ArcanaApiClient.createBooking`'s non-2xx path
 * (`BookingFailure.kt`'s `bookingFailureFor` + `parsedErrorCode`).
 *
 * Why this file exists at all: the original ERR-11 fix shipped with
 * `BookingViewModelTest` green because every one of its cases threw a
 * hand-constructed exception (`BookingError(...)`, `ApiHttpError(500)`, a bare
 * `Exception`) directly out of a fake `BookingApi`. That proves
 * `BookingViewModel` reacts correctly to a given exception; it proves NOTHING
 * about whether `createBooking` actually produces the right exception from a
 * real HTTP response. It didn't: a real 5xx with no JSON `{"error": ...}`
 * body fell all the way to `BookingError("booking_failed")` — the same code a
 * bare 4xx produces — instead of the `ApiHttpError` that classifies SERVER.
 * Device QA caught it; nothing in the suite would have.
 *
 * `ArcanaApiClient` builds its own `HttpClient` internally with no
 * engine-injection seam, so a `MockEngine` cannot be routed through
 * `createBooking` itself (contrast `ReadEndpointErrorTypeTest`, which can,
 * because the read endpoints' whole non-2xx behavior is the free-standing
 * `bodyOrThrow()` extension). Instead, `createBooking`'s non-2xx block was
 * extracted verbatim into two standalone pieces in `BookingFailure.kt` and
 * `createBooking` now just calls them — so testing them directly here IS
 * testing `createBooking`'s real behavior, not a stand-in for it. Two tiers:
 *
 * - The status/reason-code DECISION (`bookingFailureFor`) is genuinely pure,
 *   so it's tested exhaustively over the status table below.
 * - The BODY PARSING (`parsedErrorCode`) touches a real Ktor response,
 *   so it's driven through an actual `MockEngine`-backed `HttpClient`
 *   exactly like `ReadEndpointErrorTypeTest` drives the read endpoints — an
 *   HTML Django error page and a no-`error`-key JSON body are genuinely
 *   different parse paths, not the same case told twice.
 */
class BookingFailureTest {

    // ── layer 1: the pure decision table ─────────────────────────────────────

    @Test
    fun `a parsed reason code wins over a 5xx status`() {
        val thrown = bookingFailureFor(500, "session_full")

        assertIs<BookingError>(thrown, "a named reason must win even when the status is also a server error")
        assertEquals("session_full", thrown.code)
    }

    @Test
    fun `a parsed reason code wins over a 4xx status`() {
        val thrown = bookingFailureFor(409, "session_full")

        assertIs<BookingError>(thrown)
        assertEquals("session_full", thrown.code)
    }

    @Test
    fun `a 5xx with no reason code classifies as SERVER instead of the generic fallback`() {
        val thrown = bookingFailureFor(500, null)

        assertIs<ApiHttpError>(thrown, "this is the exact defect: it used to fall back to BookingError booking_failed")
        assertEquals(500, thrown.statusCode)
        assertEquals(ErrorType.SERVER, thrown.toErrorType())
    }

    @Test
    fun `every 5xx status with no reason code classifies as SERVER`() {
        for (status in listOf(500, 502, 503, 504, 599)) {
            val thrown = bookingFailureFor(status, null)
            assertIs<ApiHttpError>(thrown, "status $status")
            assertEquals(ErrorType.SERVER, thrown.toErrorType(), "status $status")
        }
    }

    @Test
    fun `every 4xx status with no reason code falls back to the generic booking_failed code`() {
        for (status in listOf(400, 404, 409, 422, 429, 499)) {
            val thrown = bookingFailureFor(status, null)
            assertIs<BookingError>(thrown, "status $status")
            assertEquals("booking_failed", thrown.code, "status $status")
        }
    }

    @Test
    fun `the 499 to 500 boundary is where booking_failed flips to SERVER`() {
        val justBelow = bookingFailureFor(499, null)
        val at = bookingFailureFor(500, null)

        assertIs<BookingError>(justBelow, "499 must still read as an ordinary client-side failure")
        assertEquals("booking_failed", justBelow.code)
        assertIs<ApiHttpError>(at, "500 is the first status that must classify SERVER")
    }

    // ── layer 2: real HTTP responses through a MockEngine ────────────────────

    /** Configured like `ArcanaApiClient`'s client in the ways that matter:
     *  JSON negotiation installed, `expectSuccess` left at its `false` default
     *  (MockEngine never applies Ktor's success validator anyway, but this
     *  keeps the setup honest about what production does). */
    private fun clientReturning(status: HttpStatusCode, body: String, contentType: String): HttpClient =
        HttpClient(
            MockEngine {
                respond(
                    content = body,
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, contentType),
                )
            },
        ) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

    /** Reproduces `createBooking`'s exact non-2xx line —
     *  `bookingFailureFor(response.status.value, response.parsedErrorCode())`
     *  — against a real response, so these tests exercise the production
     *  decision end-to-end (parsing included), not a hand-thrown stand-in. */
    private suspend fun bookingFailureFromResponse(response: HttpResponse): Throwable =
        bookingFailureFor(response.status.value, response.parsedErrorCode())

    @Test
    fun `a 500 whose body is an HTML error page classifies as SERVER`() = runTest {
        // The realistic case: Django's own debug/error page, or an infra layer
        // in front of it (Cloud Run) — never the API's {"error": ...} shape.
        val djangoErrorPage = "<html><head><title>Server Error (500)</title></head>" +
            "<body><h1>Server Error (500)</h1></body></html>"
        val client = clientReturning(HttpStatusCode.InternalServerError, djangoErrorPage, "text/html")

        val thrown = bookingFailureFromResponse(client.post("/api/v1/bookings/"))

        assertIs<ApiHttpError>(thrown, "an HTML 500 body must not be mistaken for a typed reason code")
        assertEquals(ErrorType.SERVER, thrown.toErrorType())
    }

    @Test
    fun `a 500 whose body is valid JSON with no error key classifies as SERVER`() = runTest {
        val client = clientReturning(
            HttpStatusCode.InternalServerError,
            """{"detail":"Internal Server Error"}""",
            "application/json",
        )

        val thrown = bookingFailureFromResponse(client.post("/api/v1/bookings/"))

        assertIs<ApiHttpError>(thrown, "valid JSON with no error key must not fall back to booking_failed")
        assertEquals(ErrorType.SERVER, thrown.toErrorType())
    }

    @Test
    fun `a 409 carrying a typed reason code surfaces that reason instead of a category`() = runTest {
        val client = clientReturning(HttpStatusCode.Conflict, """{"error":"session_full"}""", "application/json")

        val thrown = bookingFailureFromResponse(client.post("/api/v1/bookings/"))

        assertIs<BookingError>(thrown, "a named reason must always win over a transport category")
        assertEquals("session_full", thrown.code)
    }

    @Test
    fun `a 4xx with no parseable reason still falls back to booking_failed`() = runTest {
        val client = clientReturning(HttpStatusCode.BadRequest, """{"detail":"Bad request"}""", "application/json")

        val thrown = bookingFailureFromResponse(client.post("/api/v1/bookings/"))

        assertIs<BookingError>(thrown)
        assertEquals("booking_failed", thrown.code)
    }
}
