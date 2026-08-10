package org.arcana.mobile.analytics

import com.posthog.PostHog

/** Android [Analytics] backed by the PostHog Android SDK. */
class PostHogAnalytics : Analytics {

    /** Last-registered environment tag, re-applied after [reset] clears it. */
    @Volatile private var environment: String? = null

    private fun clean(p: Map<String, Any?>): Map<String, Any>? =
        p.mapNotNull { (k, v) -> v?.let { k to it } }.toMap().ifEmpty { null }

    override fun capture(event: String, properties: Map<String, Any?>) {
        PostHog.capture(event = event, properties = clean(properties))
    }

    override fun screen(name: String, properties: Map<String, Any?>) {
        PostHog.screen(screenTitle = name, properties = clean(properties))
    }

    override fun identify(distinctId: String, properties: Map<String, Any?>) {
        PostHog.identify(distinctId = distinctId, userProperties = clean(properties))
    }

    override fun setPersonProperties(properties: Map<String, Any?>) {
        PostHog.capture(event = "\$set", userProperties = clean(properties))
    }

    override fun setEnvironment(environment: String) {
        this.environment = environment
        PostHog.register("environment", environment)
    }

    override fun reset() {
        PostHog.reset()
        // reset() clears registered super properties, so re-register them
        // immediately — otherwise every event after a logout loses its `platform`
        // (and `environment`) tag until the next cold start.
        PostHog.register("platform", "android")
        environment?.let { PostHog.register("environment", it) }
    }
}
