package org.arcana.mobile.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Extra bottom content inset for scrollables on the three tab-root screens
 * (Home / Schedule / Profile), so content flows edge-to-edge UNDER each
 * platform's floating tab bar yet can still scroll its last item clear of it.
 *
 * - iOS shell: provided by `shell/TabRoots.kt` as the bottom safe-drawing
 *   inset (the floating glass bar's region).
 * - Android: provided by `App.kt`'s `MainScaffold` as `ui/TabBar.kt`'s
 *   `floatingBarInset` (the bar's own drawn height plus the safe-drawing
 *   inset beneath it), since the Compose bar floats over content instead of
 *   reserving its own Scaffold slot.
 * - Any other context (no floating bar: `AuthFlowRoot`, Design system):
 *   defaults to 0.dp. Both shells provide their inset around the whole
 *   NavHost, so a pushed (non-tab) destination still sees a non-zero inset
 *   (on Android the bar's full height; on iOS the safe-area bottom, since
 *   the native bar is hidden there), even though no bar is drawn there.
 */
val LocalFloatingBarInset = compositionLocalOf<Dp> { 0.dp }
