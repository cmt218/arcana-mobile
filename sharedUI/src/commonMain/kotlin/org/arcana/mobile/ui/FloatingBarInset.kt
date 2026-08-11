package org.arcana.mobile.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Extra bottom content inset for scrollables on the three tab-root screens
 * (Home / Schedule / Profile), so content flows edge-to-edge UNDER the iOS
 * shell's floating Liquid Glass tab bar yet can still scroll its last item
 * clear of it.
 *
 * - iOS shell: provided by `shell/TabRoots.kt` as the bottom safe-drawing
 *   inset (the floating bar's region).
 * - Android (and any context without a floating bar): defaults to 0.dp —
 *   the opaque Compose ArcanaTabBar still reserves its own space via
 *   Scaffold, so no extra inset is wanted.
 */
val LocalFloatingBarInset = compositionLocalOf<Dp> { 0.dp }
