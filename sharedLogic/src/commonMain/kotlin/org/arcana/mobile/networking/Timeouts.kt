package org.arcana.mobile.networking

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.timeout
import io.ktor.http.HttpMethod

/** Reads give up after 10s of silence, so a dead signal says so fast (on iOS this also bounds the
 *  connect: Darwin ignores connectTimeoutMillis). Writes keep 30s: a booking takes up to ~8s
 *  server-side, and a timed-out write leaves the member unsure whether it went through. */
internal object Timeouts {
    const val CONNECT_MS = 10_000L
    const val READ_SOCKET_MS = 10_000L
    const val READ_REQUEST_MS = 20_000L
    const val WRITE_SOCKET_MS = 30_000L
    const val WRITE_REQUEST_MS = 60_000L
}

internal fun HttpClientConfig<*>.installTimeouts() {
    install(HttpTimeout) {
        connectTimeoutMillis = Timeouts.CONNECT_MS
        socketTimeoutMillis = Timeouts.WRITE_SOCKET_MS
        requestTimeoutMillis = Timeouts.WRITE_REQUEST_MS
    }
    install(ReadTimeouts)
}

private val ReadTimeouts = createClientPlugin("ReadTimeouts") {
    onRequest { request, _ ->
        if (request.method == HttpMethod.Get) {
            request.timeout {
                socketTimeoutMillis = Timeouts.READ_SOCKET_MS
                requestTimeoutMillis = Timeouts.READ_REQUEST_MS
            }
        }
    }
}
