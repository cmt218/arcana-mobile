package org.arcana.mobile.networking

/** Raised when the server rejects a concierge submission. `code` is the
 * server's `{error: code}` reason, or a generic fallback. Mirrors BookingError. */
class ConciergeError(val code: String) : Exception(code)

interface ConciergeApi {
    /** Submit a free-form concierge message. Returns the new request id on success. */
    suspend fun createConciergeRequest(message: String): Int
}
