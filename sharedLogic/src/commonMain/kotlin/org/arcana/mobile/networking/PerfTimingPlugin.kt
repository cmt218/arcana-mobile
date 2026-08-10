package org.arcana.mobile.networking

import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.contentLength
import io.ktor.http.encodedPath
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlin.time.TimeSource
import org.arcana.mobile.analytics.Telemetry
import org.arcana.mobile.analytics.apiRequestOutcome
import org.arcana.mobile.analytics.deriveNetworkMs
import org.arcana.mobile.analytics.normalizeEndpoint

class PerfTimingConfig {
    /** Telemetry sink. Left as Noop until installed by ArcanaApiClient. */
    var telemetry: Telemetry = Telemetry.Noop

    /** Fraction of requests to record (0.0–1.0). 1.0 = every request. The knob
     *  lets us dial volume down later without touching call sites. */
    var sampleRate: Double = 1.0
}

/**
 * Times every request on the client it's installed on and emits one
 * `api_request` event: total round-trip (send → response available), the
 * server's self-reported processing time (`X-Arcana-Server-Ms`), and the
 * derived network time. Best-effort — a failure to record must never affect
 * the request. A network/IO exception is recorded as `network_error` (status 0)
 * and re-thrown so the caller's own error handling is unchanged.
 */
val PerfTimingPlugin = createClientPlugin("PerfTiming", ::PerfTimingConfig) {
    val telemetry = pluginConfig.telemetry
    val sampleRate = pluginConfig.sampleRate

    on(Send) { request ->
        val sampled = sampleRate >= 1.0 || Random.nextDouble() < sampleRate
        if (!sampled) return@on proceed(request)

        val method = request.method.value
        val path = request.url.encodedPath
        val mark = TimeSource.Monotonic.markNow()
        val call = try {
            proceed(request)
        } catch (e: CancellationException) {
            // A cancelled request (rapid nav, a superseded filter refetch, VM
            // teardown) is NOT a network failure — rethrow without recording so
            // it doesn't pollute the api_request error rate.
            throw e
        } catch (e: Throwable) {
            recordSafely(telemetry) {
                telemetry.apiRequest(
                    endpoint = normalizeEndpoint(method, path),
                    method = method,
                    statusCode = 0,
                    outcome = apiRequestOutcome(0),
                    totalMs = mark.elapsedNow().inWholeMilliseconds,
                    serverMs = null,
                    networkMs = null,
                    responseBytes = null,
                )
            }
            throw e
        }
        val totalMs = mark.elapsedNow().inWholeMilliseconds
        recordSafely(telemetry) {
            val status = call.response.status.value
            val serverMs = call.response.headers["X-Arcana-Server-Ms"]?.toLongOrNull()
            telemetry.apiRequest(
                endpoint = normalizeEndpoint(method, path),
                method = method,
                statusCode = status,
                outcome = apiRequestOutcome(status),
                totalMs = totalMs,
                serverMs = serverMs,
                networkMs = deriveNetworkMs(totalMs, serverMs),
                responseBytes = call.response.contentLength(),
            )
        }
        call
    }
}

/** Swallow any analytics-path failure — instrumentation must never break a call. */
private inline fun recordSafely(telemetry: Telemetry, block: () -> Unit) {
    try {
        block()
    } catch (e: Throwable) {
        telemetry.recordError(e, mapOf("where" to "PerfTimingPlugin"))
    }
}
