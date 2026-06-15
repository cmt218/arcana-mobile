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

        let apiKey = TelemetryKeys.postHogApiKey
        #if DEBUG
        if apiKey.isEmpty {
            print("D/Telemetry: PostHog DISABLED — POSTHOG_API_KEY missing. Check that Secrets.xcconfig exists and Config.xcconfig is the project's config file.")
        } else {
            print("D/Telemetry: PostHog init — host=\(TelemetryKeys.postHogHost), key=\(apiKey.prefix(8))… (\(apiKey.count) chars)")
        }
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
            // Debug builds: flush each event immediately so QA sees events in
            // PostHog in real time. (Set config.debug = true here for verbose
            // SDK delivery logs when troubleshooting.)
            #if DEBUG
            config.flushAt = 1
            #endif
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
                options.tracesSampleRate = 0.2
            }
            crashReporter = SwiftCrashReporter()
        }

        return (analytics, crashReporter)
    }
}
