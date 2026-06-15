package org.arcana.mobile.analytics

import android.content.Context
import com.posthog.PostHog
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig
import io.sentry.android.core.SentryAndroid
import org.arcana.mobile.BuildConfig
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Initializes PostHog + Sentry and returns the Koin module that binds the shared
 * [Analytics] / [CrashReporter] interfaces to the live instances. Call from
 * [org.arcana.mobile.ArcanaApplication.onCreate] BEFORE `startKoin`, then pass
 * the returned module alongside `appModule`.
 *
 * When a key/DSN is blank (fresh clone / local dev without secrets configured)
 * the corresponding SDK is skipped and a no-op binding is used, so the app runs
 * unchanged with telemetry simply disabled.
 */
fun androidTelemetryModule(context: Context): Module {
    val posthogEnabled = BuildConfig.POSTHOG_API_KEY.isNotBlank()
    val sentryEnabled = BuildConfig.SENTRY_DSN.isNotBlank()

    if (posthogEnabled) {
        val config = PostHogAndroidConfig(
            apiKey = BuildConfig.POSTHOG_API_KEY,
            host = BuildConfig.POSTHOG_HOST,
        ).apply {
            // Compose has no View tree, so PostHog's autocapture can't see our
            // screens — we emit $screen manually from MainScaffold. App-lifecycle
            // + deep-link autocapture stay on (cheap, useful).
            captureScreenViews = false
            captureDeepLinks = true
            captureApplicationLifecycleEvents = true
            // Session replay, fully masked for privacy (no text/inputs/images).
            sessionReplay = true
            sessionReplayConfig.maskAllTextInputs = true
            sessionReplayConfig.maskAllImages = true
            // Debug builds: send each event immediately (default batches ~20
            // events / ~30s) so QA sees events in PostHog in real time. Release
            // keeps the efficient default batching.
            if (BuildConfig.DEBUG) flushAt = 1
        }
        PostHogAndroid.setup(context, config)
        // Super property on every event so the dashboard can break down / filter
        // by platform (iOS vs Android).
        PostHog.register("platform", "android")
    }

    if (sentryEnabled) {
        SentryAndroid.init(context) { options ->
            options.dsn = BuildConfig.SENTRY_DSN
            // Low-rate performance tracing: app-start spans + manual network spans.
            options.tracesSampleRate = 0.2
        }
    }

    return module {
        single<Analytics> { if (posthogEnabled) PostHogAnalytics() else NoopAnalytics }
        single<CrashReporter> { if (sentryEnabled) SentryCrashReporter() else NoopCrashReporter }
    }
}
