package org.arcana.mobile.ui

/** True when a screen reader or switch access is driving the UI; gesture controls then render as taps. */
expect fun accessibilityServicesActive(): Boolean
