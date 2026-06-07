package org.arcana.mobile

import android.os.Build
import android.util.Log

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): Platform = AndroidPlatform()

// Production API. Override via Developer Settings to point at a local server
// (e.g. http://10.0.2.2:8000 — the emulator's loopback to the host) during dev.
actual fun defaultBaseUrl(): String = "https://api.arcana.fit"

actual fun logWarning(tag: String, message: String) {
    Log.w(tag, message)
}