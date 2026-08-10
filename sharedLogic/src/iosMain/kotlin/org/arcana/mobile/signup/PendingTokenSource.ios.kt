package org.arcana.mobile.signup

/**
 * iOS intentionally performs NO first-launch token recovery. Reading the
 * clipboard would show iOS's "… would like to paste from …" banner, which
 * alarms users, and Apple offers no Install Referrer equivalent. Members who
 * tapped the welcome link before installing should re-tap it once the app is
 * installed (the Universal Link routes cleanly), or use the "Already paid?
 * enter your email" fallback (request-signup-link; screens 04/05, deferred).
 */
actual class PendingTokenSource actual constructor() {
    actual suspend fun consumePendingToken(): String? = null
}
