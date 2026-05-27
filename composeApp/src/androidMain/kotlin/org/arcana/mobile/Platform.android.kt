package org.arcana.mobile

import android.os.Build
import android.util.Log

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): Platform = AndroidPlatform()

// 10.0.2.2 is the emulator's loopback to the host machine. Physical Android
// devices need a tunnel URL set via Developer Settings.
actual fun defaultBaseUrl(): String = "http://10.0.2.2:8000"

actual fun logWarning(tag: String, message: String) {
    Log.w(tag, message)
}