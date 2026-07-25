package org.arcana.mobile.networking

import io.ktor.client.plugins.ResponseException
import org.arcana.mobile.analytics.apiRequestOutcome

/**
 * The member-facing category of a failed request. Two categories, because a
 * Member can only act on two things: their connection, or waiting for us.
 *
 * - [CONNECTION] — the request never got a valid answer from the server
 *   (offline, flaky, timeout, DNS). Copy talks about *their* connection and
 *   must never say "server error": the server may well be perfectly healthy.
 * - [SERVER] — the server answered badly (5xx, or an unexpected non-auth 4xx).
 *   Copy owns the fault.
 *
 * 401/403 never reach here in practice: the token-refresh interceptor handles
 * them upstream (see `refreshOutcomeForStatus`).
 */
enum class ErrorType { CONNECTION, SERVER }

/**
 * Pure status → category mapping, defined in terms of [apiRequestOutcome] so
 * the UI and the `api_request` telemetry event can never disagree about what
 * counts as a network failure. `0` means the request never completed.
 */
fun errorTypeForStatus(statusCode: Int): ErrorType =
    if (apiRequestOutcome(statusCode) == "network_error") ErrorType.CONNECTION else ErrorType.SERVER

/**
 * Classify any caught failure. A [ResponseException] means we received an HTTP
 * status, so the server answered: SERVER. Anything else (IO, timeout, transport
 * drop) never reached the server: CONNECTION.
 *
 * This is the single place that decision is made. Screens must not re-derive it
 * from exception messages.
 */
fun Throwable.toErrorType(): ErrorType = when (this) {
    is ResponseException -> errorTypeForStatus(response.status.value)
    else -> ErrorType.CONNECTION
}
