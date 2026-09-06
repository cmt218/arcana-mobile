package org.arcana.mobile.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.skiaCanvas
import org.jetbrains.skia.BlendMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.VertexMode
import platform.Foundation.NSProcessInfo
import platform.Foundation.lowPowerModeEnabled
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

@Composable
actual fun systemAllowsAmbientMotion(): Boolean =
    !UIAccessibilityIsReduceMotionEnabled() && !NSProcessInfo.processInfo.lowPowerModeEnabled

internal actual class AtmosphereMeshPainter actual constructor() {
    private val paint = Paint()

    // DST keeps the vertex colours: with no shader Skia treats the paint colour as the source, and
    // MODULATE against the default black paint renders the whole mesh black.
    actual fun draw(canvas: Canvas, mesh: AtmosphereMesh) {
        canvas.skiaCanvas.drawVertices(
            VertexMode.TRIANGLES, mesh.positions, mesh.colors, null, mesh.indices, BlendMode.DST, paint,
        )
    }
}
