package org.arcana.mobile

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.coroutines.flow.MutableStateFlow
import org.arcana.mobile.analytics.Analytics
import org.arcana.mobile.analytics.CrashReporter
import org.arcana.mobile.analytics.NoopAnalytics
import org.arcana.mobile.analytics.NoopCrashReporter
import org.arcana.mobile.di.appModule
import org.arcana.mobile.navigation.DeepLinkHandler
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.koin.mp.KoinPlatformTools
import platform.UIKit.UIViewController

/**
 * Bridge for iOS deep links. SwiftUI delivers an opened URL (onOpenURL /
 * onContinueUserActivity) AFTER the Compose view controller is created, so the
 * URL can't be passed as a one-shot constructor param — it must be pushed into
 * an observable the running Compose hierarchy collects. [onIosDeepLink] is called
 * from Swift; [MainViewController]'s composable collects [pendingDeepLink] and
 * recomposes [App] with the extracted token. Works for both cold and warm starts.
 */
object IosDeepLinkBridge {
    val pendingDeepLink = MutableStateFlow<String?>(null)
}

/** Called from Swift (onOpenURL / onContinueUserActivity). */
fun onIosDeepLink(url: String) {
    IosDeepLinkBridge.pendingDeepLink.value = url
}

/**
 * @param analytics Swift-provided PostHog-backed [Analytics] (see
 *   `iosApp/.../SwiftAnalytics.swift`). Null → no-op (telemetry disabled).
 * @param crashReporter Swift-provided Sentry-backed [CrashReporter]. Null → no-op.
 *
 * Swift constructs these (after `PostHogSDK.shared.setup` / `SentrySDK.start` in
 * `iOSApp.swift`) and passes them in, mirroring how deep links are bridged. They
 * are registered into Koin so shared code resolves the same instances Android does.
 */
fun MainViewController(
    analytics: Analytics? = null,
    crashReporter: CrashReporter? = null,
): UIViewController {
    // Guard against a double startKoin if the VC is ever recreated.
    if (KoinPlatformTools.defaultContext().getOrNull() == null) {
        val a = analytics ?: NoopAnalytics
        val c = crashReporter ?: NoopCrashReporter
        startKoin {
            modules(
                appModule,
                module {
                    single<Analytics> { a }
                    single<CrashReporter> { c }
                },
            )
        }
    }
    return ComposeUIViewController {
        val pending by IosDeepLinkBridge.pendingDeepLink.collectAsState()
        val token = pending?.let { DeepLinkHandler.extractWelcomeToken(it) }
        App(
            initialWelcomeToken = token,
            onWelcomeTokenConsumed = { IosDeepLinkBridge.pendingDeepLink.value = null },
        )
    }
}
