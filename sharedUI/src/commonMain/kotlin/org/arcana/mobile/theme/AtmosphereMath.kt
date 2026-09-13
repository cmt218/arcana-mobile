package org.arcana.mobile.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/*
 * Spec: docs/superpowers/specs/2026-09-04-mobile-premium-polish-design.md §Atmosphere.
 * Colours are pre-mixed over Stone and device-tuned (the perimeter deeper than the
 * spec's original presence, to kill a white edge halo) — do not layer alpha on top.
 */
const val ATMOSPHERE_GRID = 4
// 0.08, not the prototype's 0.15: the interior control points sit a third of the
// width apart, and at 0.15 two of them can close to within 0.03 of each other, so
// the Catmull-Rom patches fold over and the later triangles paint sawtooth seams
// across the earlier ones (the "jagged edges" in the field). At 0.08 the mesh
// never folds and its thinnest triangle keeps ~28% of its rest area; locked by
// AtmosphereMeshFoldTest.
const val ATMOSPHERE_AMPLITUDE = 0.08f
private const val EDGE_AMPLITUDE_FACTOR = 0.6f
private const val BASE_PERIOD_X = 6.0f
private const val BASE_PERIOD_Y = 7.5f
private const val PERIOD_SCALE_MIN = 0.8f
private const val PERIOD_SCALE_MAX = 1.4f
private const val TWO_PI = (2 * PI).toFloat()

// Felicia's O "Deep centre" over Stone #F5F2ED: a deep-olive centre inside a
// LIGHT-olive perimeter, so the field reads as a gradient that breathes as the
// points drift — not a flat olive slab. The perimeter is light enough to stay
// airy but clearly tinted (never the near-white that read as a halo).
// Olive = MossLight+Lime 50%.
// Lighter overall than a solid olive, but the two centre points are far apart in
// value (a deep-olive accent beside a light one) so the drift stays legible as
// they swirl — the field breathes without ever going dark across the board.
private val LimeWhisper = Color(0xFFD8DBB6)   // corner: light olive (off Stone, no halo)
private val LimeTint = Color(0xFFCED4A4)      // edge:   light olive
private val OliveShade = Color(0xFF9AA662)    // centre: deep-olive accent (the darkest)
private val LimeDeepShade = Color(0xFFC2CA86) // centre: light olive — wide gap = visible drift

val ATMOSPHERE_COLORS: List<Color> = listOf(
    LimeWhisper, LimeTint, LimeTint, LimeWhisper,
    LimeTint, OliveShade, LimeDeepShade, LimeTint,
    LimeTint, LimeDeepShade, OliveShade, LimeTint,
    LimeWhisper, LimeTint, LimeTint, LimeWhisper,
)

// A whisper of olive at the very corners — enough to seat them, not enough to
// flatten the centre-to-edge gradient (0.20 did exactly that).
val ATMOSPHERE_VIGNETTE = Color(0xFFA6B277)   // Stone + Olive 65%
const val ATMOSPHERE_VIGNETTE_ALPHA = 0.10f

data class PointSeed(val periodX: Float, val periodY: Float, val phaseX: Float, val phaseY: Float)

fun atmosphereSeeds(random: Random): List<PointSeed> = List(ATMOSPHERE_GRID * ATMOSPHERE_GRID) {
    PointSeed(
        periodX = BASE_PERIOD_X * random.nextFloat(PERIOD_SCALE_MIN, PERIOD_SCALE_MAX),
        periodY = BASE_PERIOD_Y * random.nextFloat(PERIOD_SCALE_MIN, PERIOD_SCALE_MAX),
        phaseX = random.nextFloat() * TWO_PI,
        phaseY = random.nextFloat() * TWO_PI,
    )
}

private fun Random.nextFloat(from: Float, until: Float) = from + nextFloat() * (until - from)

/** Normalised (0..1) position of control point ([row], [col]) at [timeSeconds]. */
fun atmosphereControlPoint(row: Int, col: Int, timeSeconds: Float, seed: PointSeed): Offset {
    val last = ATMOSPHERE_GRID - 1
    val baseX = col / last.toFloat()
    val baseY = row / last.toFloat()
    val onVerticalEdge = col == 0 || col == last
    val onHorizontalEdge = row == 0 || row == last
    if (onVerticalEdge && onHorizontalEdge) return Offset(baseX, baseY)
    val dx = ATMOSPHERE_AMPLITUDE * sin(TWO_PI * timeSeconds / seed.periodX + seed.phaseX)
    val dy = ATMOSPHERE_AMPLITUDE * cos(TWO_PI * timeSeconds / seed.periodY + seed.phaseY)
    return when {
        onVerticalEdge -> Offset(baseX, baseY + dy * EDGE_AMPLITUDE_FACTOR)
        onHorizontalEdge -> Offset(baseX + dx * EDGE_AMPLITUDE_FACTOR, baseY)
        else -> Offset(baseX + dx, baseY + dy)
    }
}
