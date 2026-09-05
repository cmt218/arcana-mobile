package org.arcana.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType
import platform.UIKit.UISelectionFeedbackGenerator

@Composable
actual fun rememberHaptics(): Haptics = remember { UiKitHaptics() }

private class UiKitHaptics : Haptics {
    private val selectionGen = UISelectionFeedbackGenerator().also { it.prepare() }
    private val light = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleLight).also { it.prepare() }
    private val soft = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleSoft).also { it.prepare() }
    private val rigid = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleRigid).also { it.prepare() }
    private val notification = UINotificationFeedbackGenerator().also { it.prepare() }

    override fun selection() = selectionGen.selectionChanged()
    override fun tick() = light.impactOccurredWithIntensity(0.5)
    override fun toggle(on: Boolean) = if (on) light.impactOccurredWithIntensity(0.8) else soft.impactOccurredWithIntensity(0.6)
    override fun threshold() = rigid.impactOccurredWithIntensity(1.0)
    override fun confirm() = notification.notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess)
    override fun reject() = notification.notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeWarning)
    override fun boundary() = soft.impactOccurredWithIntensity(0.5)
    override fun ramp() = light.impactOccurredWithIntensity(0.4)
}
