import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                // Custom-scheme links (arcana://welcome?token=...)
                .onOpenURL { url in
                    MainViewControllerKt.onIosDeepLink(url: url.absoluteString)
                }
                // Universal Links (https://arcana.fit/welcome?token=...)
                .onContinueUserActivity(NSUserActivityTypeBrowsingWeb) { activity in
                    if let url = activity.webpageURL {
                        MainViewControllerKt.onIosDeepLink(url: url.absoluteString)
                    }
                }
        }
    }
}
