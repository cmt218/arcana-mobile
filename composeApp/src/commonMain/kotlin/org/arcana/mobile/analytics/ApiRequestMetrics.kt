package org.arcana.mobile.analytics

/**
 * Pure, testable helpers for the `api_request` transport-timing event. Kept
 * separate from the Ktor plugin so the mapping logic is unit-tested without an
 * HttpClient.
 */

/**
 * Map (method, path) to a stable, low-cardinality endpoint name for the
 * `api_request` event. Path ids are collapsed so `/classes/8412/` and
 * `/classes/99/` both become `class_detail`; the raw path is never sent to
 * PostHog (bounded cardinality — important for cost).
 *
 * **Maintenance:** when you add an endpoint to `ArcanaApiClient`, add a `when`
 * case here. Unmapped routes fall back to `other` — that's safe (no breakage,
 * still bounded) but the call won't get its own line on the Mobile Performance
 * dashboard; a rising `other` bucket there is the tell that a mapping is
 * missing. `other` (not an auto-derived name) is intentional: future slug/uuid
 * path params would otherwise blow up cardinality. Every case below is locked
 * by `ApiRequestMetricsTest` — a rename fails the build.
 */
fun normalizeEndpoint(method: String, encodedPath: String): String {
    // Strip the version prefix and any leading/trailing slashes, then collapse
    // pure-integer segments to `{id}` so ids don't explode cardinality.
    val path = encodedPath
        .substringAfter("/api/v1/", encodedPath)
        .trim('/')
    val shape = path.split('/')
        .joinToString("/") { seg -> if (seg.toIntOrNull() != null) "{id}" else seg }
    val m = method.uppercase()
    return when (m to shape) {
        "GET" to "classes" -> "schedule_window"
        "GET" to "classes/overview" -> "schedule_overview"
        "GET" to "classes/sessions" -> "schedule_page"
        "GET" to "classes/{id}" -> "class_detail"
        "GET" to "memberships/me" -> "membership_me"
        "GET" to "bookings/me" -> "my_bookings"
        "POST" to "bookings" -> "booking_create"
        "GET" to "bookings/{id}" -> "booking_detail"
        "DELETE" to "bookings/{id}" -> "booking_cancel"
        "POST" to "auth/token" -> "login"
        "POST" to "auth/token/refresh" -> "token_refresh"
        "POST" to "auth/complete-signup" -> "complete_signup"
        "POST" to "auth/request-password-reset" -> "password_reset"
        "POST" to "beta/signup-survey" -> "signup_survey"
        "GET" to "users/me" -> "profile"
        "PATCH" to "users/me" -> "profile_update"
        "GET" to "users/me/favorites" -> "favorites"
        "PUT" to "users/me/favorites" -> "favorites_update"
        "POST" to "concierge-requests" -> "concierge_create"
        else -> "other"
    }
}

/** Bucket an HTTP status into an outcome class. `0` = the request never
 *  completed (network/IO/timeout — the plugin passes 0 on exception). */
fun apiRequestOutcome(statusCode: Int): String = when {
    statusCode == 0 -> "network_error"
    statusCode in 200..399 -> "success"
    statusCode in 400..499 -> "client_error"
    else -> "server_error"
}

/** Network time = client round-trip minus server processing, clamped ≥ 0.
 *  Null when the server header was absent (can't attribute the split). */
fun deriveNetworkMs(totalMs: Long, serverMs: Long?): Long? =
    if (serverMs == null) null else (totalMs - serverMs).coerceAtLeast(0L)
