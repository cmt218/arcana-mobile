// Scroll-perf A/B harness — NOT part of the app target. See README.md in this
// directory for the runbook. To use: copy into iosApp/iosApp/Perf/ (synchronized
// folder — Xcode picks it up automatically) and add to iOSApp.swift:
//     init() { FrameTimeRecorder.shared.start() }
// Must be identical in both builds under comparison. REMOVE both the file and the
// init call before archiving anything for release.
import Foundation
import QuartzCore

final class FrameTimeRecorder {
    static let shared = FrameTimeRecorder()

    private var displayLink: CADisplayLink?
    private var buffer: [String] = []
    private var fileURL: URL?
    private var wallClockAtStart: TimeInterval = 0
    private var mediaTimeAtStart: CFTimeInterval = 0
    private let flushQueue = DispatchQueue(label: "frame-recorder-flush", qos: .utility)

    func start() {
        guard displayLink == nil else { return }
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let launchEpoch = Int(Date().timeIntervalSince1970)
        let url = docs.appendingPathComponent("frame_log_\(launchEpoch).csv")
        FileManager.default.createFile(atPath: url.path, contents: "wall_ts,media_ts,target_ts,duration\n".data(using: .utf8))
        fileURL = url
        wallClockAtStart = Date().timeIntervalSince1970
        mediaTimeAtStart = CACurrentMediaTime()

        let link = CADisplayLink(target: self, selector: #selector(onFrame(_:)))
        // .common so the link keeps firing during scroll tracking — the whole point.
        link.add(to: .main, forMode: .common)
        displayLink = link
    }

    @objc private func onFrame(_ link: CADisplayLink) {
        let wall = wallClockAtStart + (link.timestamp - mediaTimeAtStart)
        buffer.append("\(wall),\(link.timestamp),\(link.targetTimestamp),\(link.duration)")
        if buffer.count >= 240 { flush() }
    }

    private func flush() {
        guard let url = fileURL, !buffer.isEmpty else { return }
        let lines = buffer.joined(separator: "\n") + "\n"
        buffer.removeAll(keepingCapacity: true)
        flushQueue.async {
            if let handle = try? FileHandle(forWritingTo: url) {
                handle.seekToEndOfFile()
                handle.write(lines.data(using: .utf8)!)
                try? handle.close()
            }
        }
    }
}
