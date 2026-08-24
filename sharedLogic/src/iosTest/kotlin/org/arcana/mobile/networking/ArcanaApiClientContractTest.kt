@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.arcana.mobile.networking

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.HttpHeaders
import io.ktor.http.ContentType
import kotlinx.coroutines.test.runTest
import org.arcana.mobile.analytics.Telemetry
import org.arcana.mobile.auth.SecureStorage
import org.arcana.mobile.auth.TokenStorage
import kotlin.test.*

/**
 * Drives the REAL `ArcanaApiClient` — auth plugin, timeouts, `bodyOrThrow` and
 * all — against `MockEngine`.
 *
 * The contract worth pinning: with `expectSuccess = false`, an endpoint written
 * with a bare `.body()` instead of `bodyOrThrow()` turns a 5xx into a status-less
 * deserialization error, which then classifies as CONNECTION and tells the member
 * to check their connection while the server is down. Nothing failed if someone
 * reverted a call site to `.body()` until this test existed.
 *
 * **iosTest, not commonTest.** The client needs a `TokenStorage` and a
 * `BaseUrlProvider`, both of which take the concrete `SecureStorage`; on Android
 * that wants a real `Context` for EncryptedSharedPreferences, which a JVM unit
 * test has no way to supply. iOS's Keychain works under the simulator. The code
 * under test is commonMain, so one target proves the contract. Moving this to
 * commonTest needs `SecureStorage` to become fakeable — a change to the app's
 * highest-blast-radius code, worth its own card rather than a drive-by.
 */
class ArcanaApiClientContractTest {

    private fun clientFor(status: HttpStatusCode, body: String = """{"detail":"boom"}""") =
        ArcanaApiClient(
            tokenStorage = TokenStorage(SecureStorage()),
            baseUrlProvider = BaseUrlProvider(SecureStorage(), Telemetry.Noop, "http://localhost"),
            telemetry = Telemetry.Noop,
            engine = MockEngine {
                respond(
                    content = body,
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            },
        )

    @Test fun `a 500 throws ApiHttpError carrying the status`() = runTest {
        val e = assertFailsWith<ApiHttpError> { clientFor(HttpStatusCode.InternalServerError).fetchClassDetail(482) }
        assertEquals(500, e.statusCode)
        assertEquals(ErrorType.SERVER, (e as Throwable).toErrorType())
    }

    @Test fun `a 404 throws ApiHttpError rather than a parse failure`() = runTest {
        val e = assertFailsWith<ApiHttpError> { clientFor(HttpStatusCode.NotFound).fetchClassDetail(482) }
        assertEquals(404, e.statusCode)
    }

    /** The snippet is what makes a 5xx diagnosable in logs. */
    @Test fun `the error carries a body snippet`() = runTest {
        val e = assertFailsWith<ApiHttpError> {
            clientFor(HttpStatusCode.BadGateway, body = "upstream timed out").fetchClassDetail(482)
        }
        assertTrue(e.bodySnippet?.contains("upstream") == true, "snippet was ${e.bodySnippet}")
    }

    @Test fun `a 2xx deserializes normally`() = runTest {
        val json = """
            {"id":482,"start_at":"2026-06-11T09:00:00-04:00","end_at":"2026-06-11T09:50:00-04:00",
             "duration_minutes":50,"status":"scheduled","platform_capacity":20,"platform_booked":14,
             "arcana_spots_offered":20,"arcana_spots_available":6,
             "template":{"id":311,"name":"Foundation 50","modality":"pilates","hero_image_url":"","spot_selection_mode":"none"},
             "instructors":[],
             "location":{"id":41,"name":"SolidCore Williamsburg","timezone":"America/New_York",
                         "studio":{"id":3,"slug":"solidcore","name":"SolidCore","logo_url":"","primary_color":"#1A1A1A"}}}
        """.trimIndent()
        val session = clientFor(HttpStatusCode.OK, json).fetchClassDetail(482)
        assertEquals(482, session.id)
        assertEquals(6, session.arcanaSpotsAvailable)
    }
}
