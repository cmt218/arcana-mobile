package org.arcana.mobile.networking

import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException

/** A real HTTP response with a failure status. [bodySnippet] is the first 200
 *  chars of the body, for logs. */
class ApiHttpError(val statusCode: Int, val bodySnippet: String? = null) : Exception(
    if (bodySnippet.isNullOrBlank()) "HTTP $statusCode" else "HTTP $statusCode: $bodySnippet",
)

/**
 * Read a 2xx body, or throw [ApiHttpError] carrying the status. Use instead of a
 * bare `.body()` wherever an endpoint doesn't inspect `response.status` itself:
 * with `expectSuccess = false` a bare `.body()` on a 5xx throws a status-less
 * deserialization error, which then classifies as CONNECTION instead of SERVER.
 *
 * Not `expectSuccess = true` — refresh, login, booking, concierge and signup all
 * need to see a non-2xx without throwing.
 */
suspend inline fun <reified T> HttpResponse.bodyOrThrow(): T {
    if (status.value !in 200..299) {
        val snippet = try {
            bodyAsText().take(200)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null // never let snippet capture mask the real failure
        }
        throw ApiHttpError(status.value, snippet)
    }
    return body()
}
