package org.arcana.mobile

import platform.Foundation.NSBundle
import platform.UIKit.UIDevice

class IOSPlatform: Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
}

actual fun getPlatform(): Platform = IOSPlatform()

// Production API. Override via Developer Settings to point at a local server
// (e.g. http://localhost:8000 — the host Mac, reachable from the simulator) during dev.
actual fun defaultBaseUrl(): String = "https://api.arcana.fit"

actual fun logWarning(tag: String, message: String) {
    println("W/$tag: $message")
}

actual fun appVersionName(): String =
    NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: ""