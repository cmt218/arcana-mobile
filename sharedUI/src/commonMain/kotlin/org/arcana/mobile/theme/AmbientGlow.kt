package org.arcana.mobile.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Burnt Nectar radial glow behind hero content — mirrors the ambient `radial-gradient`
 * used at the top of the marketing site. Keeps surfaces kinetic without forcing
 * a loud accent block.
 */
@Composable
fun AmbientNectarGlow(
    modifier: Modifier = Modifier,
    intensity: Float = 0.18f,
    centerYFraction: Float = 0.18f,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                val cx = size.width / 2f
                val cy = size.height * centerYFraction
                val radius = maxOf(size.width, size.height) * 0.65f
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(BurntNectar.copy(alpha = intensity), Color.Transparent),
                        center = Offset(cx, cy),
                        radius = radius,
                    )
                )
            }
    )
}
