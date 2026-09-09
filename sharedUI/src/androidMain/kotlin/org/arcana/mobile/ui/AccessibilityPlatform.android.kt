package org.arcana.mobile.ui

import android.content.Context
import android.view.accessibility.AccessibilityManager
import org.arcana.mobile.SharedAndroidContext

actual fun accessibilityServicesActive(): Boolean {
    val ctx = SharedAndroidContext.appContext ?: return false
    val am = ctx.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    return am?.isTouchExplorationEnabled == true
}
