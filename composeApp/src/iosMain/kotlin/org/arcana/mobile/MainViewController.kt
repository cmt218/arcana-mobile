package org.arcana.mobile

import androidx.compose.ui.window.ComposeUIViewController
import org.arcana.mobile.di.appModule
import org.arcana.mobile.navigation.DeepLinkHandler
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatformTools
import platform.UIKit.UIViewController

/**
 * iOS entry point. Receives the cold-start deep link (if any) from the Swift side,
 * extracts the welcome token, and hands it to the shared [App].
 *
 * @param pendingDeepLink the absolute URL the app was opened with (custom-scheme or
 *   Universal Link), or null on a normal launch.
 * @param onConsumed called once [App] has consumed the welcome token, so Swift can
 *   clear its pending-link binding.
 */
fun MainViewController(
    pendingDeepLink: String? = null,
    onConsumed: () -> Unit = {},
): UIViewController {
    // Guard against a double startKoin: if this view controller is ever recreated,
    // calling startKoin a second time throws KoinApplicationAlreadyStartedException.
    // GlobalContext is JVM-only in Koin; the multiplatform-safe accessor for the
    // started-Koin check in iosMain is KoinPlatformTools.defaultContext().
    if (KoinPlatformTools.defaultContext().getOrNull() == null) {
        startKoin { modules(appModule) }
    }
    return ComposeUIViewController {
        val token = pendingDeepLink?.let { DeepLinkHandler.extractWelcomeToken(it) }
        App(initialWelcomeToken = token, onWelcomeTokenConsumed = onConsumed)
    }
}
