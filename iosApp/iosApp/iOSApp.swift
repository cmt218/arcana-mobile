import SwiftUI

@main
struct iOSApp: App {
    @State private var pendingDeepLink: String? = nil

    var body: some Scene {
        WindowGroup {
            ContentView(pendingDeepLink: $pendingDeepLink)
                // Custom-scheme links (arcana://welcome?token=...).
                .onOpenURL { url in
                    pendingDeepLink = url.absoluteString
                }
                // Universal Links (https://arcana.fit/welcome?token=...).
                .onContinueUserActivity(NSUserActivityTypeBrowsingWeb) { activity in
                    if let url = activity.webpageURL {
                        pendingDeepLink = url.absoluteString
                    }
                }
        }
    }
}
