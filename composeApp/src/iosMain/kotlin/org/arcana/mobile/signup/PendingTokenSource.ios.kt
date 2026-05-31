package org.arcana.mobile.signup

import org.arcana.mobile.navigation.DeepLinkHandler
import platform.UIKit.UIPasteboard

actual class PendingTokenSource actual constructor() {
    actual suspend fun consumePendingToken(): String? {
        val pasteboard = UIPasteboard.generalPasteboard
        // Note: reading the pasteboard shows the iOS 14+ "pasted from…" banner on cold
        // start when the clipboard is non-empty. Accepted cost of the clipboard fallback;
        // iOS 16+ UIPasteboard.detectPatterns could suppress it later (out of scope).
        val content = pasteboard.string ?: return null
        val token = DeepLinkHandler.extractClipboardToken(content)
        if (token != null) {
            pasteboard.setString("")  // clear so a re-launch doesn't re-trigger
        }
        return token
    }
}
