package org.arcana.mobile.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import org.arcana.mobile.theme.Lime
import org.arcana.mobile.theme.Stone
import org.arcana.mobile.theme.WordmarkGrid
import org.arcana.mobile.theme.wordmarkGrid

/** Gap between one row starting and the next, top to bottom. */
internal const val REFRESH_ROW_STEP_MS = 40

/** How long one dot takes to settle once its row starts. */
internal const val REFRESH_DOT_MS = 260

/** The share of a dot's settle spent lime, before it turns stone. */
internal const val REFRESH_LIME_SHARE = 0.35f

/** Rows in the wordmark grid; `SplashWordmarkTest` holds this to the grid itself. */
internal const val WORDMARK_ROWS = 15

/** When the last row has settled and the full wordmark is on screen. */
const val SPLASH_WORDMARK_SETTLED_MS: Int = (WORDMARK_ROWS - 1) * REFRESH_ROW_STEP_MS + REFRESH_DOT_MS

/**
 * The splash's wordmark, drawn the way a dot-matrix display redraws: row by row
 * from the top, each dot flashing lime then settling to stone, then breathing.
 * Settles on [WordmarkLogo]'s grid at 88% of the width.
 */
@Composable
fun SplashWordmark(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier) {
        val viewportW = constraints.maxWidth.toFloat()
        val viewportH = constraints.maxHeight.toFloat()
        if (viewportW <= 0f || viewportH <= 0f) return@BoxWithConstraints

        val dots = remember(viewportW, viewportH) { layoutDots(wordmarkGrid, viewportW, viewportH) }
        // One clock for the splash's life: keyed on the size, a resize (rotation, an
        // iPad window) would restart the redraw and blank the wordmark.
        var timeMs by remember { mutableLongStateOf(0L) }
        LaunchedEffect(Unit) {
            val startNs = withFrameNanos { it }
            while (true) {
                withFrameNanos { now -> timeMs = (now - startNs) / 1_000_000L }
            }
        }

        val limeBuckets = remember { Array(ALPHA_BUCKETS) { ArrayList<Offset>(wordmarkGrid.lit.size) } }
        val stoneBuckets = remember { Array(ALPHA_BUCKETS) { ArrayList<Offset>(wordmarkGrid.lit.size) } }
        val limeColors = remember { bucketColors(Lime) }
        val stoneColors = remember { bucketColors(Stone) }

        Canvas(modifier = Modifier.matchParentSize()) {
            val t = timeMs.toFloat()
            limeBuckets.forEach { it.clear() }
            stoneBuckets.forEach { it.clear() }
            for (i in dots.indices) {
                val dot = dots[i]
                bucket(limeBuckets, refreshLime(dot.row, t), dot.center)
                bucket(stoneBuckets, refreshStone(dot.row, t, i), dot.center)
            }
            drawBuckets(limeBuckets, limeColors, dots.cellSize)
            drawBuckets(stoneBuckets, stoneColors, dots.cellSize)
        }
    }
}

/** Lime alpha of a dot in [row] at [tMs]: only while it first lights. */
internal fun refreshLime(row: Int, tMs: Float): Float {
    val p = rowProgress(row, tMs)
    return if (p <= 0f || p >= REFRESH_LIME_SHARE) 0f else easeOut(p / REFRESH_LIME_SHARE)
}

/** Stone alpha of the dot at [index] in [row] at [tMs]: rises from 0.6 as the lime
 *  hands over, then breathes once settled. */
internal fun refreshStone(row: Int, tMs: Float, index: Int): Float {
    val p = rowProgress(row, tMs)
    return when {
        p < REFRESH_LIME_SHARE -> 0f
        p < 1f -> 0.6f + 0.4f * easeOut((p - REFRESH_LIME_SHARE) / (1f - REFRESH_LIME_SHARE))
        else -> breath(tMs - row * REFRESH_ROW_STEP_MS - REFRESH_DOT_MS, index)
    }
}

private fun rowProgress(row: Int, tMs: Float): Float = (tMs - row * REFRESH_ROW_STEP_MS) / REFRESH_DOT_MS

private fun easeOut(x: Float): Float {
    val inv = 1f - x.coerceIn(0f, 1f)
    return 1f - inv * inv * inv
}

/** A 2.8s breath, phase-offset per dot so the settled mark reads as alive. */
private fun breath(afterMs: Float, index: Int): Float =
    1f - 0.18f * (0.5f - 0.5f * cos(((afterMs + index * 37f) / 2800f) * 2f * PI.toFloat()))

private class Dot(val center: Offset, val row: Int)

private class Dots(val items: List<Dot>, val cellSize: Float) {
    val size get() = items.size
    val indices get() = items.indices
    operator fun get(i: Int) = items[i]
}

/** The lit dots only, on a grid spanning the whole viewport (evenly padded, origin top-left). */
private fun layoutDots(grid: WordmarkGrid, viewportW: Float, viewportH: Float): Dots {
    val cellSize = viewportW * WORDMARK_WIDTH_FRACTION / grid.cols
    var totalCols = max(grid.cols, ceil(viewportW / cellSize).toInt())
    var totalRows = max(grid.rows, ceil(viewportH / cellSize).toInt())
    if ((totalCols - grid.cols) % 2 != 0) totalCols++
    if ((totalRows - grid.rows) % 2 != 0) totalRows++
    val padC = (totalCols - grid.cols) / 2
    val padR = (totalRows - grid.rows) / 2
    val half = cellSize / 2f
    val items = grid.lit
        .sortedWith(compareBy({ it[1] }, { it[0] }))
        .map { (c, r) -> Dot(Offset((padC + c) * cellSize + half, (padR + r) * cellSize + half), r) }
    return Dots(items, cellSize)
}

private fun bucket(buckets: Array<ArrayList<Offset>>, alpha: Float, at: Offset) {
    if (alpha <= 0.01f) return
    buckets[(alpha * ALPHA_BUCKETS).toInt().coerceAtMost(ALPHA_BUCKETS - 1)].add(at)
}

private fun bucketColors(color: Color) = Array(ALPHA_BUCKETS) { b -> color.copy(alpha = (b + 0.5f) / ALPHA_BUCKETS) }

/** One draw call per non-empty alpha bucket: iOS's Skia-on-Metal does not batch
 *  per-point alpha, so drawing each dot on its own cost a draw call per dot. */
private fun DrawScope.drawBuckets(buckets: Array<ArrayList<Offset>>, colors: Array<Color>, cellSize: Float) {
    for (b in buckets.indices) {
        val points = buckets[b]
        if (points.isNotEmpty()) {
            drawPoints(points, PointMode.Points, colors[b], strokeWidth = cellSize, cap = StrokeCap.Round)
        }
    }
}

private const val ALPHA_BUCKETS = 16
private const val WORDMARK_WIDTH_FRACTION = 0.88f
