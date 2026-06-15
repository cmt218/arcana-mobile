import UIKit
import SwiftUI
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    let analytics: Analytics?
    let crashReporter: CrashReporter?

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(
            analytics: analytics,
            crashReporter: crashReporter
        )
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    let analytics: Analytics?
    let crashReporter: CrashReporter?

    var body: some View {
        ComposeView(analytics: analytics, crashReporter: crashReporter)
            .ignoresSafeArea()
    }
}
