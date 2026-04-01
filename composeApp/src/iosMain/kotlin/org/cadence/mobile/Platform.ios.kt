package org.cadence.mobile

import platform.UIKit.UIDevice

class IOSPlatform: Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
}

actual fun getPlatform(): Platform = IOSPlatform()

actual fun getBaseUrl(): String = "http://localhost:8000"

actual fun logWarning(tag: String, message: String) {
    println("W/$tag: $message")
}