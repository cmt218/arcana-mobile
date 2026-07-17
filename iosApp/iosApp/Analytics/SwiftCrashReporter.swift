import Foundation
import ComposeApp
import Sentry

/// iOS implementation of the Kotlin `CrashReporter` protocol, backed by the
/// Sentry Cocoa SDK. Crashes/hangs/app-start are captured automatically by
/// `SentrySDK.start`; this covers nonfatals, breadcrumbs and user context.
class SwiftCrashReporter: CrashReporter {

    func captureException(error: KotlinThrowable, context: [String: Any]) {
        // Sentry groups NSError by domain + code, and titles the issue from them.
        // Both used to be hardcoded ("KotlinException" / 0), so EVERY Kotlin
        // nonfatal in the app collapsed into a single "KotlinException: Code: 0"
        // issue — a forced logout and a failed booking filed as the same problem,
        // with the real message buried in userInfo where the issue list never
        // shows it. Deriving the domain from the Kotlin exception type restores
        // one issue per exception class.
        let typeName = String(describing: type(of: error))
        let ns = NSError(
            domain: typeName,
            code: 0,
            userInfo: [NSLocalizedDescriptionKey: error.message ?? typeName]
        )
        SentrySDK.capture(error: ns) { scope in
            for (key, value) in context {
                scope.setExtra(value: value, key: key)
            }
            // `cause` is promoted to a tag, not just an extra: tags are indexed,
            // so forced logouts stay filterable/searchable by why the session
            // ended. Extras are only visible once you open a single event.
            if let cause = context["cause"] as? String {
                scope.setTag(value: cause, key: "cause")
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
