package org.arcana.mobile

import android.app.Application
import org.arcana.mobile.analytics.androidTelemetryModule
import org.arcana.mobile.di.appModule
import org.koin.core.context.startKoin

class ArcanaApplication : Application() {

    override fun onCreate() {
        // Mark the process-start reference as early as we control it, for the
        // cold-start → Home measurement (see AppStartTracker).
        org.arcana.mobile.analytics.AppStartTracker.markStart()
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
