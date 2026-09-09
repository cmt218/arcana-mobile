package org.arcana.mobile.ui

import platform.UIKit.UIAccessibilityIsSwitchControlRunning
import platform.UIKit.UIAccessibilityIsVoiceOverRunning

actual fun accessibilityServicesActive(): Boolean =
    UIAccessibilityIsVoiceOverRunning() || UIAccessibilityIsSwitchControlRunning()
