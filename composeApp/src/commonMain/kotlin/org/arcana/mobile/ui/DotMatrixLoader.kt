package org.arcana.mobile.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.arcana.mobile.theme.Lime
import org.arcana.mobile.theme.Mist
import org.arcana.mobile.theme.Moss

/** Phase offset between adjacent (col + row) diagonals — the choreography
 *  knob for how steep the traveling wave leans across the grid. */
internal const val DOT_PHASE_STEP = 0.07f

/**
 * Phase offset between adjacent dots along the (col + row) diagonal — produces
 * the traveling-wave choreography of the dot-matrix loader. Pure function so
 * the wave math is unit-testable without Compose.
 *
 * Returns intensity in [0, 1]: a triangle pulse that ramps 0→1 over the first
 * half of the (wrapped) phase and 1→0 over the second half.
 */
internal fun dotIntensity(progress: Float, col: Int, row: Int): Float {
    val phase = (progress + (col + row) * DOT_PHASE_STEP) % 1f
    return if (phase < 0.5f) phase * 2f else (1f - phase) * 2f
}

/**
 * Branded loading indicator: a grid of dots pulsing in a diagonal traveling
 * wave — the dot is the brand's repeating gesture (see ui/Dots.kt, the splash
 * wordmark). Each dot lerps Mist → Lime → Moss with intensity.
 *
 * Use the default size for full-surface loading states and [DotMatrixLoaderCompact]
 * for inline/footer loading (e.g. pagination load-more).
 */
@Composable
fun DotMatrixLoader(
    modifier: Modifier = Modifier,
    columns: Int = 9,
    rows: Int = 3,
    dotSize: Dp = 6.dp,
    pitch: Dp = 12.dp,
) {
    val transition = rememberInfiniteTransition(label = "dotMatrix")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "dotMatrixProgress",
    )
    Canvas(
        modifier = modifier.size(
            width = pitch * (columns - 1) + dotSize,
            height = pitch * (rows - 1) + dotSize,
        ),
    ) {
        val radius = dotSize.toPx() / 2f
        val step = pitch.toPx()
        for (row in 0 until rows) {
            for (col in 0 until columns) {
                val intensity = dotIntensity(progress, col, row)
                val color = if (intensity < 0.5f) {
                    lerp(Mist, Lime, intensity * 2f)
                } else {
                    lerp(Lime, Moss, (intensity - 0.5f) * 2f)
                }
                drawCircle(
                    color = color,
                    radius = radius,
                    center = Offset(col * step + radius, row * step + radius),
                )
            }
        }
    }
}

/** Single-row variant for inline loading (pagination footers, filter refresh). */
@Composable
fun DotMatrixLoaderCompact(modifier: Modifier = Modifier) {
    DotMatrixLoader(modifier = modifier, columns = 5, rows = 1, dotSize = 5.dp, pitch = 11.dp)
}
