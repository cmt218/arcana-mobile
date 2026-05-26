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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Canvas as GraphicsCanvas
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
 * Renders into a single Compose Canvas using a pre-rasterized [ImageBitmap]
 * dot sprite — the same approach the HTML reference uses with drawImage +
 * globalAlpha. ~20 k cells/frame at 30 fps during the dance; falls to ~629
 * lit cells once settled because we skip cells with alpha ≤ 0.01.
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
        val sprite = remember(state.cellSize, lit, dotScale) {
            buildDotSprite(state.cellSize * dotScale, lit)
        }

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
            val cs = state.cellSize
            val half = cs / 2f
            val drawSize = sprite.width.toFloat()
            val drawOffset = -drawSize / 2f
            var i = 0
            var r = 0
            while (r < state.totalRows) {
                val cy = r * cs + half + drawOffset
                var c = 0
                while (c < state.totalCols) {
                    val delay = state.delays[i]
                    val localT = t - delay
                    val isLit = state.litFlags[i].toInt() == 1
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
                        drawImage(
                            image = sprite,
                            topLeft = Offset(c * cs + half + drawOffset, cy),
                            alpha = op,
                        )
                    }
                    c++
                    i++
                }
                r++
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

// ---------- Dot sprite ----------

private fun buildDotSprite(diameterPx: Float, color: Color): ImageBitmap {
    // 2-px padding around the circle so the AA edge isn't clipped at the sprite border.
    val pad = 2
    val size = ceil(diameterPx).toInt() + pad * 2
    val safeSize = max(4, size)
    val bitmap = ImageBitmap(safeSize, safeSize)
    val canvas = GraphicsCanvas(bitmap)
    val paint = Paint().apply {
        isAntiAlias = true
        this.color = color
        style = PaintingStyle.Fill
    }
    canvas.drawCircle(
        center = Offset(safeSize / 2f, safeSize / 2f),
        radius = diameterPx / 2f,
        paint = paint,
    )
    return bitmap
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
