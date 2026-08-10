package org.arcana.mobile

import android.content.pm.ApplicationInfo
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

actual fun logDebug(tag: String, message: String) {
    Log.d(tag, message)
}

// :sharedLogic is a library module, so BuildConfig.DEBUG/VERSION_NAME (app-module
// concepts) are unavailable here. Derive both from the application context:
// FLAG_DEBUGGABLE tracks the app's build type, and PackageManager owns the
// installed versionName. Both degrade gracefully in JVM unit tests where no
// context exists (debug=false, version="unknown") — tests never assert these.
actual val isDebugBuild: Boolean
    get() = SharedAndroidContext.appContext
        ?.let { (it.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 }
        ?: false

actual fun appVersionName(): String =
    SharedAndroidContext.appContext?.let { ctx ->
        runCatching {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
        }.getOrNull()
    } ?: "unknown"
