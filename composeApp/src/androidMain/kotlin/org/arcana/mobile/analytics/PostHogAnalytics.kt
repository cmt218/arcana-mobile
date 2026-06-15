package org.arcana.mobile.analytics

import com.posthog.PostHog

/** Android [Analytics] backed by the PostHog Android SDK. */
class PostHogAnalytics : Analytics {

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

    override fun reset() {
        PostHog.reset()
    }
}
