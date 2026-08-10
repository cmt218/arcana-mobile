package org.arcana.mobile.navigation

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Bridge for iOS deep links. SwiftUI delivers an opened URL (onOpenURL /
 * onContinueUserActivity) AFTER the Compose view controller is created, so the
 * URL can't be passed as a one-shot constructor param — it must be pushed into
 * an observable the running UI hierarchy collects. [onIosDeepLink] is called
 * from Swift; the iOS entry point (`MainViewController` in :sharedUI)
 * collects [pendingDeepLink] and recomposes App with the extracted token.
 * Works for both cold and warm starts.
 *
 * NOTE for Swift callers: this file's symbols surface as `IosDeepLinkBridgeKt`
 * (previously `MainViewControllerKt` when the bridge lived in that file).
 */
object IosDeepLinkBridge {
    val pendingDeepLink = MutableStateFlow<String?>(null)
}

/** Called from Swift (onOpenURL / onContinueUserActivity). */
fun onIosDeepLink(url: String) {
    IosDeepLinkBridge.pendingDeepLink.value = url
}
