package org.arcana.mobile.theme

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext

actual fun meshGradientSupported(): Boolean = Build.VERSION.SDK_INT >= 29

@Composable
actual fun systemAllowsAmbientMotion(): Boolean {
    val context = LocalContext.current
    val powerSave = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true
    val animatorScale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    return !powerSave && animatorScale > 0f
}

internal actual class AtmosphereMeshPainter actual constructor() {
    private val paint = android.graphics.Paint()

    actual fun draw(canvas: Canvas, mesh: AtmosphereMesh) = canvas.nativeCanvas.drawVertices(
        android.graphics.Canvas.VertexMode.TRIANGLES,
        mesh.positions.size, mesh.positions, 0,
        null, 0,
        mesh.colors, 0,
        mesh.indices, 0, mesh.indices.size,
        paint,
    )
}
