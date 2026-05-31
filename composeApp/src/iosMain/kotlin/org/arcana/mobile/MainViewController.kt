package org.arcana.mobile

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.coroutines.flow.MutableStateFlow
import org.arcana.mobile.di.appModule
import org.arcana.mobile.navigation.DeepLinkHandler
import org.koin.core.context.startKoin
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

fun MainViewController(): UIViewController {
    // Guard against a double startKoin if the VC is ever recreated.
    if (KoinPlatformTools.defaultContext().getOrNull() == null) {
        startKoin { modules(appModule) }
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
