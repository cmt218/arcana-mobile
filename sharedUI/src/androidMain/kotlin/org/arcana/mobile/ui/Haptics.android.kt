package org.arcana.mobile.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

@Composable
actual fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { ViewHaptics(view) }
}

private class ViewHaptics(private val view: View) : Haptics {
    private fun perform(constant: Int) { view.performHapticFeedback(constant) }
    private val api = Build.VERSION.SDK_INT

    override fun selection() = perform(HapticFeedbackConstants.CLOCK_TICK)
    override fun tick() = perform(
        if (api >= 34) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK else HapticFeedbackConstants.CLOCK_TICK
    )
    override fun toggle(on: Boolean) = perform(
        when {
            api >= 34 && on -> HapticFeedbackConstants.TOGGLE_ON
            api >= 34 -> HapticFeedbackConstants.TOGGLE_OFF
            else -> HapticFeedbackConstants.KEYBOARD_TAP
        }
    )
    override fun threshold() = perform(
        if (api >= 34) HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE else HapticFeedbackConstants.LONG_PRESS
    )
    override fun confirm() = perform(
        if (api >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.KEYBOARD_TAP
    )
    override fun reject() = perform(
        if (api >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
    )
    override fun boundary() = perform(
        if (api >= 30) HapticFeedbackConstants.GESTURE_END else HapticFeedbackConstants.CLOCK_TICK
    )
    override fun ramp() = tick()
}
