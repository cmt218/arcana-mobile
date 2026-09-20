import Foundation

/// SentryCrash can die part-way through writing a report, and sentry-cocoa then
/// crash-loops every launch reading `report["user"]` with no type check. Losing one
/// already corrupt report is the cheaper loss, so anything unreadable is deleted.
enum SentryReportPreflight {

    /// Top-level keys SentryCrashReportConverter dereferences as dictionaries. A
    /// scalar in any of them throws before app code runs.
    private static let dictionaryKeys = [
        "user", "crash", "report", "system", "process", "debug", "sentry_sdk_scope",
    ]

    private static let maxReportBytes = 5 * 1024 * 1024

    @discardableResult
    static func purgeUnusableReports(in directory: URL) -> [String] {
        let fileManager = FileManager.default
        guard let names = try? fileManager.contentsOfDirectory(atPath: directory.path) else {
            return []
        }
        var removed: [String] = []
        for name in names.sorted() where name.hasSuffix(".json") {
            let url = directory.appendingPathComponent(name)
            if isUsable(url) { continue }
            if (try? fileManager.removeItem(at: url)) != nil {
                removed.append(name)
            }
        }
        return removed
    }

    private static func isUsable(_ url: URL) -> Bool {
        let attributes = try? FileManager.default.attributesOfItem(atPath: url.path)
        guard let size = attributes?[.size] as? NSNumber,
              size.intValue <= maxReportBytes,
              let data = try? Data(contentsOf: url),
              let parsed = try? JSONSerialization.jsonObject(with: data),
              let report = parsed as? [String: Any]
        else {
            return false
        }
        for key in dictionaryKeys where report[key] != nil {
            if !(report[key] is [String: Any]) { return false }
        }
        if let images = report["binary_images"], !(images is [Any]) { return false }
        return true
    }

    /// `<Caches>/SentryCrash/<CFBundleName>/Reports`, found by scanning rather than
    /// composing so a bundle-name change cannot silently reinstate the crash loop.
    static func reportDirectories(inCaches caches: URL) -> [URL] {
        let root = caches.appendingPathComponent("SentryCrash")
        guard let children = try? FileManager.default.contentsOfDirectory(
            at: root,
            includingPropertiesForKeys: nil
        ) else {
            return []
        }
        return children
            .map { $0.appendingPathComponent("Reports") }
            .filter(isDirectory)
    }

    private static func isDirectory(_ url: URL) -> Bool {
        var directory: ObjCBool = false
        let exists = FileManager.default.fileExists(atPath: url.path, isDirectory: &directory)
        return exists && directory.boolValue
    }

    static func run() {
        let caches = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)
        for directory in caches.flatMap({ reportDirectories(inCaches: $0) }) {
            purgeUnusableReports(in: directory)
        }
    }
}
