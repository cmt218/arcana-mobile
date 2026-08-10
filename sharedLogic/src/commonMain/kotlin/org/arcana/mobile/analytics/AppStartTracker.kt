package org.arcana.mobile.analytics

import kotlin.time.TimeSource

/**
 * Measures cold start: from the platform entry point ([markStart], called in
 * ArcanaApplication.onCreate / MainViewController) to the first rendered content
 * ([onFirstContent], called from App.kt). Fires `app_start_completed` exactly
 * once per process. Not true process/dyld start — starts at the earliest point
 * we control — so it under-counts pre-main time (documented; don't read as OS
 * TTID).
 */
object AppStartTracker {
    private var startMark: TimeSource.Monotonic.ValueTimeMark? = null
    private var fired = false

    /** Record t0 as early as possible at the platform entry point. Idempotent. */
    fun markStart() {
        if (startMark == null) startMark = TimeSource.Monotonic.markNow()
    }

    /** Fire once, when the first screen's content renders. No-op if already
     *  fired or if [markStart] never ran. */
    fun onFirstContent(telemetry: Telemetry, authenticated: Boolean) {
        if (fired) return
        val mark = startMark ?: return
        fired = true
        telemetry.appStartCompleted(
            durationMs = mark.elapsedNow().inWholeMilliseconds,
            startType = "cold",
            authenticated = authenticated,
            splashMs = null,
        )
    }

    fun resetForTest() {
        startMark = null
        fired = false
    }
}
