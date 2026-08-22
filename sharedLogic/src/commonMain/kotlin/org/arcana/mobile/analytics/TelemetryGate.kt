package org.arcana.mobile.analytics

import org.arcana.mobile.auth.SecureStorage
import org.arcana.mobile.defaultBaseUrl
import org.arcana.mobile.networking.BaseUrlProvider

/**
 * Whether this build may report product analytics, and how to label crash reports.
 *
 * Everything in PostHog is production: dev traffic is never sent, rather than
 * sent and filtered out on the dashboard. Sentry is exempt by design and reports
 * from every build, so dev and regression crashes still reach it.
 */
object TelemetryGate {

    fun shouldReportAnalytics(isDebugBuild: Boolean, environment: String): Boolean =
        !isDebugBuild && environment == ENV_PROD

    /** Sentry's native `environment` option. Alert rules scope to `prod`. */
    fun sentryEnvironment(isDebugBuild: Boolean, environment: String): String =
        if (isDebugBuild) "$environment-debug" else environment

    /**
     * Telemetry starts before Koin on both platforms, so this reads the
     * Developer Settings override straight from storage. An unreadable store
     * falls back to the bundled default rather than failing startup.
     */
    fun currentEnvironment(): String = classifyEnvironment(
        runCatching { BaseUrlProvider.storedUrl(SecureStorage(), defaultBaseUrl()) }
            .getOrElse { defaultBaseUrl() },
    )
}
