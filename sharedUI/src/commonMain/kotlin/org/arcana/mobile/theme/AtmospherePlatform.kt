package org.arcana.mobile.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Canvas

/**
 * False under Reduce Motion, Low Power Mode, or animations turned off. Read on every
 * recomposition and not observed, so the caller must recompose to see a change; do not
 * wrap it in `remember` or the gate freezes for the composable's lifetime.
 */
@Composable
expect fun systemAllowsAmbientMotion(): Boolean

/** Draws Gouraud-shaded triangles through the platform canvas; the common API only takes boxed lists. */
internal expect class AtmosphereMeshPainter() {
    fun draw(canvas: Canvas, mesh: AtmosphereMesh)
}
