package org.arcana.mobile.analytics

import io.ktor.http.Url

const val ENV_PROD = "prod"
const val ENV_LOCAL = "local"
const val ENV_TUNNEL = "tunnel"
const val ENV_OTHER = "other"

/**
 * Classify the API base URL into an analytics `environment` tag.
 *
 * Also the input to [TelemetryGate.shouldReportAnalytics]: dev traffic is not
 * sent at all, so as a PostHog super-property this is only a tripwire.
 *
 * - `prod`   → the production API (`api.arcana.fit`)
 * - `local`  → a server on the dev machine (`localhost` / `127.0.0.1` /
 *              `10.0.2.2`, the Android-emulator host-loopback alias)
 * - `tunnel` → a Cloudflare quick tunnel (`*.trycloudflare.com`)
 * - `other`  → anything else (unrecognized host, or an unparseable URL)
 */
fun classifyEnvironment(baseUrl: String): String {
    // Ktor's Url defaults a missing host to "localhost", so a schemeless/garbage
    // string would misclassify as `local`. Real base URLs always carry a scheme
    // (BaseUrlProvider enforces http/https), so guard on it first.
    if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) return ENV_OTHER
    val host = try {
        Url(baseUrl).host
    } catch (e: Exception) {
        return ENV_OTHER
    }
    return when {
        host == "api.arcana.fit" -> ENV_PROD
        host == "localhost" || host == "127.0.0.1" || host == "10.0.2.2" -> ENV_LOCAL
        host.endsWith("trycloudflare.com") -> ENV_TUNNEL
        else -> ENV_OTHER
    }
}
