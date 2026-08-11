import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    // ShellModel's init runs TelemetryBootstrap (PostHog + Sentry, before any
    // Kotlin executes so crash capture is armed early) and then boots Koin via
    // IosShellBridge. See ArcanaShell.swift for the Liquid Glass shell itself.
    @StateObject private var shell = ShellModel()

    var body: some Scene {
        WindowGroup {
            ArcanaShellView()
                .environmentObject(shell)
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
