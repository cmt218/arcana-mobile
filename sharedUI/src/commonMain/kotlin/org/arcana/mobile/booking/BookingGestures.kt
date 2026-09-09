package org.arcana.mobile.booking

import androidx.compose.runtime.Composable
import org.arcana.mobile.ui.accessibilityServicesActive

/** The trial switch. Off = plain taps with the press response and haptics. */
object BookingGestures {
    const val enabled = true
}

@Composable
fun useBookingGestures(): Boolean = BookingGestures.enabled && !accessibilityServicesActive()
