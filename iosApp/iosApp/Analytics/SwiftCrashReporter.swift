import Foundation
import ComposeApp
import Sentry

/// iOS implementation of the Kotlin `CrashReporter` protocol, backed by the
/// Sentry Cocoa SDK. Crashes/hangs/app-start are captured automatically by
/// `SentrySDK.start`; this covers nonfatals, breadcrumbs and user context.
class SwiftCrashReporter: CrashReporter {

    func captureException(error: KotlinThrowable, context: [String: Any]) {
        let ns = NSError(
            domain: "KotlinException",
            code: 0,
            userInfo: [NSLocalizedDescriptionKey: error.message ?? "Kotlin exception"]
        )
        SentrySDK.capture(error: ns) { scope in
            for (key, value) in context {
                scope.setExtra(value: value, key: key)
            }
        }
    }

    func addBreadcrumb(message: String, category: String, data: [String: Any]) {
        let crumb = Breadcrumb(level: .info, category: category)
        crumb.message = message
        if !data.isEmpty { crumb.data = data }
        SentrySDK.addBreadcrumb(crumb)
    }

    func setUser(id: String?, email: String?) {
        let user = User()
        user.userId = id
        user.email = email
        SentrySDK.setUser(user)
    }

    func clearUser() {
        SentrySDK.setUser(nil)
    }
}
