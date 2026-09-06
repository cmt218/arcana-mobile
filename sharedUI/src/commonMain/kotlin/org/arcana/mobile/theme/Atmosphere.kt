package org.arcana.mobile.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.delay
import kotlin.random.Random

// The frame after a 29 ms wait is the second vsync at 60 Hz and the fourth at 120 Hz, so the
// surface updates 30 times a second on either display without waking on every vsync between.
private const val ADVANCE_INTERVAL_MS = 29L

/**
 * The living surface every Stone screen sits on. Fills its parent — place it as
 * the first child of a `Box` so screen content draws over it. Draws the spec's
 * 4×4 mesh in its own layer, advancing at most 30 times a second, and drifts only
 * while resumed and while the system allows ambient motion. Deliberately no
 * `preferredFrameRate`: that vote reaches the whole scene's display link and would
 * cap scrolling too.
 */
@Composable
fun Atmosphere(modifier: Modifier = Modifier) {
    val seeds = remember { atmosphereSeeds(Random.Default) }
    val motionAllowed = systemAllowsAmbientMotion()
    var resumed by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        resumed = true
        onPauseOrDispose { resumed = false }
    }

    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(motionAllowed, resumed) {
        // Disallowed motion resets to base positions; a pause just stops advancing,
        // so withFrameNanos never schedules for either.
        if (!motionAllowed) { time = 0f; return@LaunchedEffect }
        if (!resumed) return@LaunchedEffect
        var lastNanos = withFrameNanos { it }
        while (true) {
            delay(ADVANCE_INTERVAL_MS)
            withFrameNanos { now ->
                time += (now - lastNanos) / 1_000_000_000f
                lastNanos = now
            }
        }
    }

    val mesh = remember { AtmosphereMesh(ATMOSPHERE_COLORS) }
    val painter = remember { AtmosphereMeshPainter() }
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer()
            .background(Stone)
            .drawWithCache {
                val vignette = vignetteBrush(size)
                onDrawBehind {
                    if (vignette == null) return@onDrawBehind
                    mesh.layout(seeds, time, size.width, size.height)
                    drawIntoCanvas { painter.draw(it, mesh) }
                    drawRect(vignette)
                }
            },
    )
}

// smoothstep(0.30, 0.95, d) sampled at quarter steps, d in widths from the centre, as the
// prototype's shader defined it; Clamp keeps the corners, beyond 0.95 widths, at full strength.
private const val VIGNETTE_OUTER = 0.95f
private val VIGNETTE_STOPS = listOf(0.30f to 0f, 0.4625f to 0.15625f, 0.625f to 0.5f, 0.7875f to 0.84375f, 0.95f to 1f)

private fun vignetteBrush(size: Size): Brush? {
    if (size.width <= 0f || size.height <= 0f) return null
    val stops = VIGNETTE_STOPS.map { (d, strength) ->
        d / VIGNETTE_OUTER to ATMOSPHERE_VIGNETTE.copy(alpha = strength * ATMOSPHERE_VIGNETTE_ALPHA)
    }
    return Brush.radialGradient(
        *stops.toTypedArray(),
        center = size.center,
        radius = VIGNETTE_OUTER * size.width,
        tileMode = TileMode.Clamp,
    )
}
