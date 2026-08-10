package org.arcana.mobile.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.arcana.mobile.theme.Stone

/** Per-cell dance→settle duration in ms — design-handoff canonical value. */
const val DANCE_DURATION_MS: Int = 2400

/** Max per-cell delay in ms — staggers the dance start across the grid. */
const val DANCE_SETTLE_STAGGER_MS: Int = 1300


/**
 * The dot-matrix Arcana wordmark, with the "dance → settle → breath pulse"
 * animation from the brand design handoff.
 *
 * Algorithm and tokens are a 1:1 port of the canonical reference (DancingWordmark.jsx
 * + README in design_handoff_splash_screen): cells start at a dim baseline, each
 * runs a 2.4 s flicker→settle ramp staggered up to 1.3 s after the start of the
 * animation, and lit cells (those that fall on the wordmark per
 * `wordmark-grid.json`) keep a faint 2.8 s breath pulse afterwards.
 *
 * Renders with **bucketed `drawPoints`**: each cell's per-frame opacity is
 * quantized into one of [ALPHA_BUCKETS] alpha bins, and each non-empty bin is
 * drawn with a single `drawPoints(PointMode.Points, cap = Round)` call — round-
 * capped points of `strokeWidth = cellSize * dotScale` are visually identical
 * to the AA circle sprite the previous implementation used. This collapses
 * ~20 k draw calls per frame (the wordmark's pitch × full-viewport dot rain
 * fills the screen) into ~16, which is essential on iOS where Compose's
 * Skia→Metal backend doesn't batch per-call alpha paints the way Android's
 * HWUI does. After settle, only ~629 lit cells survive the `op > 0.01` cull.
 */
@Composable
fun DancingWordmark(
    modifier: Modifier = Modifier,
    lit: Color = Stone,
    unlitAlpha: Float = 0.18f,
    unlitSettleAlpha: Float = 0f,
    dotScale: Float = 1.0f,
    durationMs: Int = DANCE_DURATION_MS,
    settleStaggerMs: Int = DANCE_SETTLE_STAGGER_MS,
    pulse: Boolean = true,
    /**
     * Width of the wordmark itself as a fraction of the available width. The
     * surrounding "dot rain" fills the rest of the viewport at the same cell
     * pitch so the wordmark stays in scale relative to the surface.
     */
    wordmarkWidthFraction: Float = 0.88f,
) {
    // Grid data is embedded in WordmarkGridData.kt and parsed lazily once
    // per process — synchronous and zero-I/O, so the splash dance starts on
    // the first frame instead of after a coroutine-scheduled file read.
    val g = wordmarkGrid

    BoxWithConstraints(modifier = modifier) {
        val viewportW = constraints.maxWidth.toFloat()
        val viewportH = constraints.maxHeight.toFloat()
        if (viewportW <= 0f || viewportH <= 0f) return@BoxWithConstraints

        val state = remember(g, viewportW, viewportH, wordmarkWidthFraction, settleStaggerMs) {
            buildDanceState(g, viewportW, viewportH, wordmarkWidthFraction, settleStaggerMs)
        }
        val buffers = remember(state) { buildRenderBuffers(state) }
        // Pre-allocated per-bucket Color instances — Color.copy(alpha = ...)
        // would allocate every frame for every non-empty bucket otherwise.
        val bucketColors = remember(lit) {
            Array(ALPHA_BUCKETS) { b -> lit.copy(alpha = (b + 0.5f) / ALPHA_BUCKETS) }
        }
        val strokeWidth = state.cellSize * dotScale

        var timeMs by remember(state) { mutableLongStateOf(0L) }
        val animating = remember(state) { mutableStateOf(true) }

        LaunchedEffect(state) {
            val startNs = withFrameNanos { it }
            while (animating.value) {
                withFrameNanos { now ->
                    val t = (now - startNs) / 1_000_000L
                    timeMs = t
                    val maxLocalT = t - 0L                          // delays are >= 0
                    val danceFinishedFor = maxLocalT - durationMs   // most-delayed cell finishes at settleStagger + duration
                    animating.value = pulse || danceFinishedFor < settleStaggerMs
                }
            }
        }

        Canvas(modifier = Modifier.matchParentSize()) {
            val t = timeMs.toFloat()
            val n = state.totalCells
            val delays = state.delays
            val litFlags = state.litFlags
            val centers = buffers.centers
            val buckets = buffers.buckets
            val maxBucket = ALPHA_BUCKETS - 1

            // Reset bucket lists (size = 0; underlying arrays are reused).
            var b = 0
            while (b < ALPHA_BUCKETS) {
                buckets[b].clear()
                b++
            }

            // Bin each above-threshold cell into its alpha bucket.
            var i = 0
            while (i < n) {
                val localT = t - delays[i]
                val isLit = litFlags[i].toInt() == 1
                val op = computeOpacity(
                    localT = localT,
                    isLit = isLit,
                    cellIndex = i,
                    durationMs = durationMs,
                    unlitAlpha = unlitAlpha,
                    unlitSettleAlpha = unlitSettleAlpha,
                    pulse = pulse,
                )
                if (op > 0.01f) {
                    var idx = (op * ALPHA_BUCKETS).toInt()
                    if (idx > maxBucket) idx = maxBucket
                    buckets[idx].add(centers[i])
                }
                i++
            }

            // One draw call per non-empty bucket. ~16 calls/frame max.
            b = 0
            while (b < ALPHA_BUCKETS) {
                val list = buckets[b]
                if (list.isNotEmpty()) {
                    drawPoints(
                        points = list,
                        pointMode = PointMode.Points,
                        color = bucketColors[b],
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                }
                b++
            }
        }
    }
}

// ---------- Animation math (port of README §"Dance + settle math") ----------

private fun computeOpacity(
    localT: Float,
    isLit: Boolean,
    cellIndex: Int,
    durationMs: Int,
    unlitAlpha: Float,
    unlitSettleAlpha: Float,
    pulse: Boolean,
): Float {
    if (localT <= 0f) return unlitAlpha
    val d = durationMs.toFloat()
    if (localT >= d) {
        if (!isLit) return unlitSettleAlpha
        if (!pulse) return 1f
        // Breath pulse — 2.8 s period, phase-offset per cell so lit dots
        // breathe slightly out of sync (the "living ember" quality).
        val breathT = (localT - d + cellIndex * 37f) / 2800f
        return (1f - 0.18f * (0.5f - 0.5f * cos(breathT * 2f * PI.toFloat()))).coerceIn(0f, 1f)
    }
    val p = localT / d
    val invP = 1f - p
    val ease = 1f - invP * invP * invP                              // ease-out cubic
    val flicker = 0.5f + 0.5f * sin(p * PI.toFloat() * 8f + cellIndex * 0.7f)
    val wild = unlitAlpha + (1f - unlitAlpha) * flicker
    val settle = if (isLit) 1f else unlitSettleAlpha
    return (wild * (1f - ease) + settle * ease).coerceIn(0f, 1f)
}

// ---------- Grid construction ----------

private class DanceState(
    val totalCols: Int,
    val totalRows: Int,
    val cellSize: Float,        // pixels
    val litFlags: ByteArray,
    val delays: FloatArray,
) {
    val totalCells: Int get() = totalCols * totalRows
}

private fun buildDanceState(
    grid: WordmarkGrid,
    viewportW: Float,
    viewportH: Float,
    wordmarkWidthFraction: Float,
    settleStaggerMs: Int,
): DanceState {
    val wordmarkW = viewportW * wordmarkWidthFraction
    val cellSize = wordmarkW / grid.cols

    var totalCols = max(grid.cols, ceil(viewportW / cellSize).toInt())
    var totalRows = max(grid.rows, ceil(viewportH / cellSize).toInt())
    // Force even pad-diff so the wordmark sits perfectly centered.
    if ((totalCols - grid.cols) % 2 != 0) totalCols++
    if ((totalRows - grid.rows) % 2 != 0) totalRows++
    val padC = (totalCols - grid.cols) / 2
    val padR = (totalRows - grid.rows) / 2

    val litSet = HashSet<Int>(grid.lit.size).apply {
        for (pair in grid.lit) add(pair[0] * 10_000 + pair[1])
    }

    val n = totalCols * totalRows
    val litFlags = ByteArray(n)
    var idx = 0
    var r = 0
    while (r < totalRows) {
        val rLocal = r - padR
        val rIn = rLocal in 0 until grid.rows
        var c = 0
        while (c < totalCols) {
            val cLocal = c - padC
            if (rIn && cLocal in 0 until grid.cols) {
                if ((cLocal * 10_000 + rLocal) in litSet) litFlags[idx] = 1
            }
            c++
            idx++
        }
        r++
    }

    val delays = FloatArray(n)
    var i = 0
    while (i < n) {
        delays[i] = pseudoRandom(i * 17.31) * settleStaggerMs
        i++
    }
    return DanceState(totalCols, totalRows, cellSize, litFlags, delays)
}

/** Same deterministic PRNG the JS reference uses so the dance is identical
 * on every cold launch. */
private fun pseudoRandom(seed: Double): Float {
    val v = sin(seed * 9301.7 + 49297.3) * 10000.0
    return (v - floor(v)).toFloat()
}

// ---------- Render buffers ----------

/** Number of alpha quantization bins. 16 is dense enough that the banding is
 * imperceptible (each bin is ~6% of the 0..1 alpha range, below the human
 * just-noticeable difference for a small dim element on a dark background). */
private const val ALPHA_BUCKETS: Int = 16

private class RenderBuffers(
    /** Pre-allocated cell-center offsets, indexed by `r * totalCols + c`. */
    val centers: Array<Offset>,
    /** Per-frame scratch lists, one per alpha bucket. Cleared and refilled
     * each frame; backing arrays are reused. */
    val buckets: Array<ArrayList<Offset>>,
)

private fun buildRenderBuffers(state: DanceState): RenderBuffers {
    val n = state.totalCells
    val cs = state.cellSize
    val half = cs / 2f
    val cols = state.totalCols
    val centers = Array(n) { i ->
        val r = i / cols
        val c = i - r * cols
        Offset(c * cs + half, r * cs + half)
    }
    // Sized so even-ish distribution doesn't trigger a resize; the dance
    // phase concentrates cells in a few mid-alpha buckets, so initial capacity
    // is generous.
    val bucketCapacity = max(64, n / 4)
    val buckets = Array(ALPHA_BUCKETS) { ArrayList<Offset>(bucketCapacity) }
    return RenderBuffers(centers, buckets)
}

// ---------- JSON loading ----------

@Serializable
private data class WordmarkGrid(
    val cols: Int,
    val rows: Int,
    val lit: List<List<Int>>,
)

private val gridJson = Json { ignoreUnknownKeys = true }

/** Lazily parsed once per process from the embedded JSON constant in
 * [WORDMARK_GRID_JSON]. Synchronous; no I/O. */
private val wordmarkGrid: WordmarkGrid by lazy(LazyThreadSafetyMode.PUBLICATION) {
    gridJson.decodeFromString(WordmarkGrid.serializer(), WORDMARK_GRID_JSON)
}
