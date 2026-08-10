import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    // Initialize PostHog + Sentry once at launch (before Compose loads) so crash
    // capture is armed early; hand the instances to Kotlin via ContentView.
    private let telemetry = TelemetryBootstrap.start()

    var body: some Scene {
        WindowGroup {
            ContentView(
                analytics: telemetry.analytics,
                crashReporter: telemetry.crashReporter
            )
                // Custom-scheme links (arcana://welcome?token=...)
                .onOpenURL { url in
                    IosDeepLinkBridgeKt.onIosDeepLink(url: url.absoluteString)
                }
                // Universal Links (https://arcana.fit/welcome?token=...)
                .onContinueUserActivity(NSUserActivityTypeBrowsingWeb) { activity in
                    if let url = activity.webpageURL {
                        IosDeepLinkBridgeKt.onIosDeepLink(url: url.absoluteString)
                    }
                }
        }
    }
}
