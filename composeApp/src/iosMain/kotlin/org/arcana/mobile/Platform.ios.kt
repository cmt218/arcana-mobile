package org.arcana.mobile

import platform.UIKit.UIDevice

class IOSPlatform: Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
}

actual fun getPlatform(): Platform = IOSPlatform()

// Simulator can reach the host Mac at localhost. Physical iPhones need a
// tunnel URL set via Developer Settings.
actual fun defaultBaseUrl(): String = "http://localhost:8000"

actual fun logWarning(tag: String, message: String) {
    println("W/$tag: $message")
}