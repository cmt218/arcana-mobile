import Foundation
import ComposeApp
import PostHog
import Sentry

/// Client-safe analytics/observability keys. Read from Info.plist so they can be
/// injected per-build-configuration (xcconfig → Info.plist) without hardcoding.
/// Add these keys to `iosApp/Info.plist` (or an xcconfig):
///   POSTHOG_API_KEY  (e.g. phc_xxx)   POSTHOG_HOST  (https://us.i.posthog.com)
///   SENTRY_DSN       (https://...@oXXXX.ingest.sentry.io/XXXX)
/// A blank value disables the corresponding SDK (telemetry simply off).
enum TelemetryKeys {
    static func string(_ key: String) -> String {
        (Bundle.main.object(forInfoDictionaryKey: key) as? String)?
            .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
    }
    static var postHogApiKey: String { string("POSTHOG_API_KEY") }
    static var postHogHost: String {
        let h = string("POSTHOG_HOST"); return h.isEmpty ? "https://us.i.posthog.com" : h
    }
    static var sentryDsn: String { string("SENTRY_DSN") }
}

/// Initializes PostHog + Sentry as early as possible (so crash capture is armed
/// before Compose loads) and returns the `Analytics`/`CrashReporter` instances to
/// hand to Kotlin via `MainViewController(analytics:crashReporter:)`.
enum TelemetryBootstrap {
    static func start() -> (analytics: Analytics?, crashReporter: CrashReporter?) {
        var analytics: Analytics? = nil
        var crashReporter: CrashReporter? = nil

        #if DEBUG
        let isDebugBuild = true
        #else
        let isDebugBuild = false
        #endif
        // Shared with Android: only a release build talking to prod may report
        // analytics. Sentry is exempt and only gets labelled.
        let environment = TelemetryGate.shared.currentEnvironment()
        let mayReportAnalytics = TelemetryGate.shared.shouldReportAnalytics(
            isDebugBuild: isDebugBuild,
            environment: environment
        )

        let apiKey = mayReportAnalytics ? TelemetryKeys.postHogApiKey : ""
        #if DEBUG
        print("D/Telemetry: PostHog DISABLED (environment=\(environment)). Console echo is unaffected.")
        #endif
        if !apiKey.isEmpty {
            let config = PostHogConfig(apiKey: apiKey, host: TelemetryKeys.postHogHost)
            // Compose draws no UIKit views, so PostHog's autocapture can't see our
            // screens — Kotlin emits $screen manually. Lifecycle stays on.
            config.captureScreenViews = false
            config.captureApplicationLifecycleEvents = true
            // Session replay, fully masked for privacy.
            config.sessionReplay = true
            config.sessionReplayConfig.maskAllTextInputs = true
            config.sessionReplayConfig.maskAllImages = true
            PostHogSDK.shared.setup(config)
            // Super property on every event so the dashboard can break down /
            // filter by platform (iOS vs Android).
            PostHogSDK.shared.register(["platform": "ios"])
            analytics = SwiftAnalytics()
        }

        let dsn = TelemetryKeys.sentryDsn
        if !dsn.isEmpty {
            SentrySDK.start { options in
                options.dsn = dsn
                // Reports from every build on purpose, so alert rules scope on this.
                options.environment = TelemetryGate.shared.sentryEnvironment(
                    isDebugBuild: isDebugBuild,
                    environment: environment
                )
                options.tracesSampleRate = 0.2
            }
            crashReporter = SwiftCrashReporter()
        }

        return (analytics, crashReporter)
    }
}
