package org.arcana.mobile.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/*
 * Values are the spec of record: docs/superpowers/specs/2026-09-04-mobile-premium-polish-design.md
 * §Atmosphere. The colours already carry the chosen "Quiet" presence — do not layer alpha on top.
 */
const val ATMOSPHERE_GRID = 4
const val ATMOSPHERE_AMPLITUDE = 0.15f
private const val EDGE_AMPLITUDE_FACTOR = 0.6f
private const val BASE_PERIOD_X = 6.0f
private const val BASE_PERIOD_Y = 7.5f
private const val PERIOD_SCALE_MIN = 0.8f
private const val PERIOD_SCALE_MAX = 1.4f
private const val TWO_PI = (2 * PI).toFloat()

private val LimeWhisper = Color(0xFFEEEDDC)   // Stone + Lime 10.8%
private val LimeTint = Color(0xFFEBEBD4)      // Stone + Lime 15.6%
private val OliveShade = Color(0xFFC5CCA6)    // Stone + Olive(MossLight+Lime 50%) 39%
private val LimeDeepShade = Color(0xFFD3D5AB) // Stone + LimeDeep 36%

val ATMOSPHERE_COLORS: List<Color> = listOf(
    LimeWhisper, LimeTint, LimeTint, LimeWhisper,
    LimeTint, OliveShade, LimeDeepShade, LimeTint,
    LimeTint, LimeDeepShade, OliveShade, LimeTint,
    LimeWhisper, LimeTint, LimeTint, LimeWhisper,
)

val ATMOSPHERE_VIGNETTE = Color(0xFFC6CA91)   // Stone + LimeDeep 50%
const val ATMOSPHERE_VIGNETTE_ALPHA = 0.06f

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
