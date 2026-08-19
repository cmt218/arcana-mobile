package org.arcana.mobile.networking

import io.ktor.client.plugins.ResponseException
import org.arcana.mobile.analytics.apiRequestOutcome

/**
 * The member-facing category of a failed request.
 *
 * - [CONNECTION] — no valid answer from the server (offline, flaky, timeout, DNS).
 *   Copy must never say "server error": the server may be perfectly healthy.
 * - [SERVER] — the server answered badly. Copy owns the fault.
 */
enum class ErrorType { CONNECTION, SERVER }

/** Routes through [apiRequestOutcome] so the UI and `api_request` telemetry
 *  agree on what counts as a network failure. 401/403 are SERVER: the request
 *  did get an answer. Call only with a known-failure status. */
fun errorTypeForStatus(statusCode: Int): ErrorType = when {
    statusCode == 401 || statusCode == 403 -> ErrorType.SERVER
    apiRequestOutcome(statusCode) == "network_error" -> ErrorType.CONNECTION
    else -> ErrorType.SERVER
}

/** The single place this decision is made; screens must not re-derive it from
 *  exception messages. [ResponseException] is unreachable today
 *  (`expectSuccess = false`) and kept only in case that changes.
 *  `BookingError`/`ConciergeError` carry a reason code, not a status. */
fun Throwable.toErrorType(): ErrorType = when (this) {
    is ApiHttpError -> errorTypeForStatus(statusCode)
    is LoginError -> errorTypeForStatus(statusCode)
    is PasswordResetRequestError -> errorTypeForStatus(statusCode)
    is ResponseException -> errorTypeForStatus(response.status.value)
    else -> ErrorType.CONNECTION
}

/** Telemetry reason, keeping the HTTP status when there is one. Recognizes the
 *  same types as [toErrorType] so the two can't disagree. */
fun Throwable.telemetryReasonFor(): String = when (this) {
    is ApiHttpError -> "server_$statusCode"
    is LoginError -> "server_$statusCode"
    is PasswordResetRequestError -> "server_$statusCode"
    is ResponseException -> "server_${response.status.value}"
    else -> if (toErrorType() == ErrorType.SERVER) "server" else "network"
}
