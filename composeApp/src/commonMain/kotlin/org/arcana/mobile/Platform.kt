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

/** Debug-level log: Android → logcat `Log.d`, iOS → stdout (Xcode console).
 *  Used by [org.arcana.mobile.analytics.Telemetry] to echo every event when
 *  [isDebugBuild] is true, so analytics can be eyeballed live during QA. */
expect fun logDebug(tag: String, message: String)

/** True for debug builds (Android `BuildConfig.DEBUG`, iOS debug binary). Gates
 *  verbose dev-only logging so release builds stay quiet. */
expect val isDebugBuild: Boolean

/**
 * The app's user-facing version string, sourced from each platform's own build
 * config so it always reflects the actual installed build:
 * - Android → `BuildConfig.VERSION_NAME` (the `versionName` in `build.gradle.kts`).
 * - iOS → `CFBundleShortVersionString` from the app's Info.plist.
 */
expect fun appVersionName(): String