package org.arcana.mobile.analytics

/**
 * Platform-neutral product-analytics sink (PostHog under the hood).
 *
 * The shared code never talks to PostHog directly — it goes through [Telemetry],
 * which calls this interface. Android implements it over `posthog-android`; iOS
 * implements it in Swift over the PostHog Swift SDK and injects the instance
 * into Kotlin via `MainViewController` (mirrors the existing deep-link bridge).
 *
 * Property values must be primitives (String / Int / Long / Double / Boolean) so
 * they bridge cleanly across the Kotlin↔Swift boundary. Pass lists as
 * comma-joined strings.
 */
interface Analytics {
    /** Capture a custom product event. */
    fun capture(event: String, properties: Map<String, Any?> = emptyMap())

    /** Capture a screen view (PostHog `$screen`). */
    fun screen(name: String, properties: Map<String, Any?> = emptyMap())

    /** Associate the current device with a known member id, merging anonymous history. */
    fun identify(distinctId: String, properties: Map<String, Any?> = emptyMap())

    /** Set/update person properties on the currently identified member (`$set`). */
    fun setPersonProperties(properties: Map<String, Any?>)

    /** Detach from the current member (call on logout / forced logout). */
    fun reset()
}

/**
 * Platform-neutral crash + nonfatal sink (Sentry under the hood).
 *
 * Crashes/ANRs are captured automatically by the native SDKs once initialized at
 * the platform entry point. This interface covers the manual surface: nonfatals
 * from `catch` blocks, breadcrumbs, and user context.
 */
interface CrashReporter {
    /** Report a handled (nonfatal) exception with optional structured context. */
    fun captureException(error: Throwable, context: Map<String, Any?> = emptyMap())

    /** Drop a breadcrumb so a later crash report shows the lead-up. */
    fun addBreadcrumb(message: String, category: String = "app", data: Map<String, Any?> = emptyMap())

    /** Attach the signed-in member to subsequent reports. */
    fun setUser(id: String?, email: String?)

    /** Clear member context (call on logout / forced logout). */
    fun clearUser()
}
