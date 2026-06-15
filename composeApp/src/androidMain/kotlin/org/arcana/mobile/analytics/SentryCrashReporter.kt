package org.arcana.mobile.analytics

import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.protocol.User

/** Android [CrashReporter] backed by the Sentry Android SDK. Uncaught crashes,
 * ANRs and app-start are captured automatically once `SentryAndroid.init` runs;
 * this covers the manual surface (nonfatals, breadcrumbs, user context). */
class SentryCrashReporter : CrashReporter {

    override fun captureException(error: Throwable, context: Map<String, Any?>) {
        Sentry.captureException(error) { scope ->
            context.forEach { (k, v) -> scope.setExtra(k, v?.toString() ?: "") }
        }
    }

    override fun addBreadcrumb(message: String, category: String, data: Map<String, Any?>) {
        val crumb = Breadcrumb().apply {
            this.message = message
            this.category = category
        }
        data.forEach { (k, v) -> crumb.setData(k, v ?: "") }
        Sentry.addBreadcrumb(crumb)
    }

    override fun setUser(id: String?, email: String?) {
        Sentry.setUser(User().apply {
            this.id = id
            this.email = email
        })
    }

    override fun clearUser() {
        Sentry.setUser(null)
    }
}
