package org.arcana.mobile

import android.content.Context

/**
 * Application context for :sharedLogic's Android actuals (SecureStorage,
 * PendingTokenSource, Platform). Set ONCE from `ArcanaApplication.onCreate()`
 * (in :sharedUI androidMain) before Koin starts — :sharedLogic cannot reference
 * the Application class directly without inverting the module dependency.
 *
 * Static app-context holders are the standard Android pattern for
 * process-lifetime services (PostHog, Sentry, and androidx-startup all do the
 * same internally) and are exempt from StaticFieldLeak lint: the Application
 * object lives as long as the process, so nothing leaks. The setter coerces to
 * `applicationContext` so a shorter-lived Context (Activity/Service) can never
 * be pinned here even by a careless future caller.
 *
 * `appContext` stays nullable for JVM unit tests, where no Android app is
 * running: consumers that can degrade gracefully (e.g. [isDebugBuild]) read it
 * directly instead of crashing on an uninitialized holder.
 */
object SharedAndroidContext {
    @Volatile
    private var _appContext: Context? = null

    var appContext: Context?
        get() = _appContext
        set(value) {
            _appContext = value?.applicationContext
        }

    fun require(): Context =
        _appContext ?: error(
            "SharedAndroidContext.appContext not set — ArcanaApplication.onCreate() must run first"
        )
}
