package org.arcana.mobile.analytics

import io.ktor.http.Url

/**
 * Classify the API base URL into an analytics `environment` tag, registered as a
 * PostHog super-property so the performance dashboard can filter to prod-only and
 * exclude our own dev traffic (local emulator/simulator + Cloudflare tunnels).
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
    if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) return "other"
    val host = try {
        Url(baseUrl).host
    } catch (e: Exception) {
        return "other"
    }
    return when {
        host == "api.arcana.fit" -> "prod"
        host == "localhost" || host == "127.0.0.1" || host == "10.0.2.2" -> "local"
        host.endsWith("trycloudflare.com") -> "tunnel"
        else -> "other"
    }
}
