package org.arcana.mobile

import android.app.Application
import org.arcana.mobile.analytics.androidTelemetryModule
import org.arcana.mobile.di.appModule
import org.koin.core.context.startKoin

class ArcanaApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Initialize PostHog + Sentry before Koin so crash capture is armed as
        // early as possible; the returned module supplies Analytics/CrashReporter.
        val telemetryModule = androidTelemetryModule(this)
        startKoin {
            modules(appModule, telemetryModule)
        }
    }

    companion object {
        lateinit var instance: ArcanaApplication
            private set
    }
}
