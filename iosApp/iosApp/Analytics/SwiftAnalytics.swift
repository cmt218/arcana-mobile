import Foundation
import ComposeApp
import PostHog

/// iOS implementation of the Kotlin `Analytics` protocol (exported by the
/// ComposeApp framework), backed by the PostHog Swift SDK. Injected into Kotlin
/// via `MainViewController(analytics:crashReporter:)`.
class SwiftAnalytics: Analytics {
    private func nonEmpty(_ p: [String: Any]) -> [String: Any]? {
        p.isEmpty ? nil : p
    }

    func capture(event: String, properties: [String: Any]) {
        PostHogSDK.shared.capture(event, properties: nonEmpty(properties))
    }

    func screen(name: String, properties: [String: Any]) {
        PostHogSDK.shared.screen(name, properties: nonEmpty(properties))
    }

    func identify(distinctId: String, properties: [String: Any]) {
        PostHogSDK.shared.identify(distinctId, userProperties: nonEmpty(properties))
    }

    func setPersonProperties(properties: [String: Any]) {
        PostHogSDK.shared.capture("$set", userProperties: nonEmpty(properties))
    }

    func reset() {
        PostHogSDK.shared.reset()
    }
}
