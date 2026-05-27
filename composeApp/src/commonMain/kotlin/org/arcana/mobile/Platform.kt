package org.arcana.mobile

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

/**
 * Platform-specific fallback base URL used when no override is persisted
 * via Developer Settings (see [org.arcana.mobile.networking.BaseUrlProvider]).
 *
 * Pre-launch: defaults to a localhost-style URL so emulator / simulator dev
 * works without any setup. Physical devices need an override (paste the
 * Cloudflare quick-tunnel URL in the Developer Settings screen).
 *
 * Post-launch: this default will move to the prod API hostname so that fresh
 * installs work out of the box. See `arcana-mobile/CLAUDE.md` → "Temporary
 * debug treatment" for the cutover checklist.
 */
expect fun defaultBaseUrl(): String

expect fun logWarning(tag: String, message: String)