import Foundation
import ComposeApp
import PostHog

/// iOS implementation of the Kotlin `Analytics` protocol (exported by the
/// ComposeApp framework), backed by the PostHog Swift SDK. Injected into Kotlin
/// via `MainViewController(analytics:crashReporter:)`.
class SwiftAnalytics: Analytics {
    /// Last-registered environment tag, re-applied after `reset()` clears it.
    private var environment: String? = nil

    /// Kotlin `Map<String, Any?>` boxes a `Boolean` value as `KotlinBoolean`
    /// when it crosses into Swift as `Any`. PostHog's Swift SDK doesn't
    /// recognize that box and silently drops it during JSON serialization, so
    /// boolean event properties (e.g. `authenticated`, `is_full`, booking flags)
    /// never reach PostHog. Coerce it to a native `Bool` here. Int/Long/String
    /// box to types PostHog already serializes, so they pass through untouched.
    private func bridge(_ p: [String: Any]) -> [String: Any] {
        p.mapValues { value in (value as? KotlinBoolean)?.boolValue ?? value }
    }

    private func nonEmpty(_ p: [String: Any]) -> [String: Any]? {
        let bridged = bridge(p)
        return bridged.isEmpty ? nil : bridged
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

    func setEnvironment(environment: String) {
        self.environment = environment
        PostHogSDK.shared.register(["environment": environment])
    }

    func reset() {
        PostHogSDK.shared.reset()
        // reset() clears registered super properties, so re-register them
        // immediately — otherwise every event after a logout loses its `platform`
        // (and `environment`) tag until the next cold start.
        PostHogSDK.shared.register(["platform": "ios"])
        if let environment = environment {
            PostHogSDK.shared.register(["environment": environment])
        }
    }
}
