package org.arcana.mobile

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

/**
 * Fallback base URL used when no override is persisted via Developer Settings
 * (see [org.arcana.mobile.networking.BaseUrlProvider]).
 *
 * Returns the PROD hostname (`https://api.arcana.fit`) on BOTH platforms — the
 * pre-launch localhost default has already been cut over, so a fresh install
 * (debug or release) talks to prod out of the box. To point a build at a server
 * on your Mac, set an override in the Developer Settings screen: `http://localhost:8000`
 * on the iOS simulator, `http://10.0.2.2:8000` on the Android emulator (its host
 * loopback alias), or a Cloudflare quick-tunnel URL on a physical device. Debug
 * builds permit cleartext to `localhost` / `10.0.2.2` for exactly that.
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
 * - Android → the installed package's `versionName` via PackageManager (set in `androidApp/build.gradle.kts`; library modules have no BuildConfig.VERSION_NAME).
 * - iOS → `CFBundleShortVersionString` from the app's Info.plist.
 */
expect fun appVersionName(): String