package org.arcana.mobile.analytics

/**
 * No-op fallbacks. Used in tests and as a safety net if a platform fails to
 * supply a real implementation — the app must never crash because telemetry is
 * unavailable.
 */
object NoopAnalytics : Analytics {
    override fun capture(event: String, properties: Map<String, Any?>) {}
    override fun screen(name: String, properties: Map<String, Any?>) {}
    override fun identify(distinctId: String, properties: Map<String, Any?>) {}
    override fun setPersonProperties(properties: Map<String, Any?>) {}
    override fun setEnvironment(environment: String) {}
    override fun reset() {}
}

object NoopCrashReporter : CrashReporter {
    override fun captureException(error: Throwable, context: Map<String, Any?>) {}
    override fun addBreadcrumb(message: String, category: String, data: Map<String, Any?>) {}
    override fun setUser(id: String?, email: String?) {}
    override fun clearUser() {}
}
