package org.arcana.mobile.networking

import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.CancellationException

/** Reads the server's `{"error": "<reason_code>"}` off a non-2xx response, or
 *  null if the body isn't shaped that way (e.g. a proxy's HTML error page).
 *  [bookingFailureFor] has a fallback for "no reason code". */
internal suspend fun HttpResponse.parsedErrorCode(): String? =
    try {
        body<Map<String, String>>()["error"]
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

/**
 * The non-2xx decision for [ArcanaApiClient.createBooking]. Order matters:
 * 1. A named [parsedCode] always wins, regardless of status.
 * 2. No code + [status] >= 500 → [ApiHttpError] (classifies SERVER) — the
 *    server answered but had nothing structured to say.
 * 3. No code + 4xx → the generic `"booking_failed"` [BookingError].
 */
internal fun bookingFailureFor(status: Int, parsedCode: String?): Throwable {
    if (parsedCode != null) return BookingError(parsedCode)
    if (status >= 500) return ApiHttpError(status)
    return BookingError("booking_failed")
}
