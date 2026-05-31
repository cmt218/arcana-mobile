import UIKit
import SwiftUI
import ComposeApp

// NOTE on deep-link delivery: pendingDeepLink is read when the Compose view
// controller is first created, so this reliably delivers a link on COLD start
// (the link launches the app). A link arriving while the app is already running
// (warm) updates the @State binding, which routes here via SwiftUI's
// updateUIViewController — a no-op below — so warm-start routing does NOT yet
// reach Kotlin. That's a known follow-up: it would need a shared Kotlin deep-link
// holder that the Swift side pushes into on update. Cold-start custom-scheme
// `xcrun simctl openurl` is what we smoke locally.
struct ComposeView: UIViewControllerRepresentable {
    let pendingDeepLink: String?
    let onConsumed: () -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(
            pendingDeepLink: pendingDeepLink,
            onConsumed: onConsumed
        )
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    @Binding var pendingDeepLink: String?

    var body: some View {
        ComposeView(
            pendingDeepLink: pendingDeepLink,
            onConsumed: { pendingDeepLink = nil }
        )
        .ignoresSafeArea()
    }
}
