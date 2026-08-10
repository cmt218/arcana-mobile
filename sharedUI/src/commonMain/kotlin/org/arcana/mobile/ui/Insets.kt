package org.arcana.mobile.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Padding for screen-level content above the bottom tab bar. Honours the status
 * bar AND any display cutout (camera punch-out) on the horizontal edges, so
 * landscape orientations don't end up drawing under the cutout. The bottom
 * inset is left to [androidx.compose.material3.Scaffold] / the tab bar.
 */
@Composable
fun Modifier.safeContentPadding(): Modifier = this.windowInsetsPadding(
    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
)

/**
 * Bottom + horizontal safe area for the tab bar — handles the navigation bar
 * gesture inset and any horizontal display cutouts.
 */
@Composable
fun Modifier.safeBottomBarPadding(): Modifier = this.windowInsetsPadding(
    WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
)

/**
 * Horizontal-only safe area — useful inside a LazyColumn item whose parent
 * needs to be full-bleed but whose own content should clear display cutouts.
 */
@Composable
fun Modifier.safeHorizontalPadding(): Modifier = this.windowInsetsPadding(
    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
)
