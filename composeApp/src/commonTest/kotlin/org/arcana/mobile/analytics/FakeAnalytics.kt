package org.arcana.mobile.analytics

/** Records every call so tests can assert the taxonomy fires correctly. */
class FakeAnalytics : Analytics {
    data class Event(val name: String, val properties: Map<String, Any?>)

    val events = mutableListOf<Event>()
    val screens = mutableListOf<String>()
    var identifiedId: String? = null
    var resetCount = 0
    val personProperties = mutableListOf<Map<String, Any?>>()

    override fun capture(event: String, properties: Map<String, Any?>) {
        events += Event(event, properties)
    }
    override fun screen(name: String, properties: Map<String, Any?>) { screens += name }
    override fun identify(distinctId: String, properties: Map<String, Any?>) { identifiedId = distinctId }
    override fun setPersonProperties(properties: Map<String, Any?>) { personProperties += properties }
    override fun reset() { resetCount++ }

    fun names(): List<String> = events.map { it.name }
    fun first(name: String): Event? = events.firstOrNull { it.name == name }
    fun all(name: String): List<Event> = events.filter { it.name == name }
}

class FakeCrashReporter : CrashReporter {
    val captured = mutableListOf<Throwable>()
    override fun captureException(error: Throwable, context: Map<String, Any?>) { captured += error }
    override fun addBreadcrumb(message: String, category: String, data: Map<String, Any?>) {}
    override fun setUser(id: String?, email: String?) {}
    override fun clearUser() {}
}

/** Convenience: a Telemetry wired to fresh fakes, returned alongside them. */
fun fakeTelemetry(): Triple<Telemetry, FakeAnalytics, FakeCrashReporter> {
    val analytics = FakeAnalytics()
    val crash = FakeCrashReporter()
    return Triple(Telemetry(analytics, crash), analytics, crash)
}
