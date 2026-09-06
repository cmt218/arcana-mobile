package org.arcana.mobile.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

/*
 * A port of the prototype renderer the treatment was chosen on: Catmull-Rom positions and
 * B-spline colours (which cannot overshoot) over the 4×4 grid, 14 segments per patch.
 * Colours and indices never change, so only positions are rebuilt per frame.
 */
internal const val MESH_SEGMENTS_PER_PATCH = 14
internal const val MESH_SIDE = (ATMOSPHERE_GRID - 1) * MESH_SEGMENTS_PER_PATCH + 1
internal const val MESH_VERTICES = MESH_SIDE * MESH_SIDE

internal class AtmosphereMesh(colors: List<Color>) {
    /** Interleaved x,y per vertex, in pixels, row-major from the top-left. Valid after [layout]. */
    private val basis = SampleBasis()

    val positions = FloatArray(MESH_VERTICES * 2)
    val colors: IntArray = meshColors(colors)
    val indices: ShortArray = meshIndices()

    private val control = FloatArray(ATMOSPHERE_GRID * ATMOSPHERE_GRID * 2)
    private val rowBlend = FloatArray(ATMOSPHERE_GRID * MESH_SIDE * 2)

    fun layout(seeds: List<PointSeed>, timeSeconds: Float, width: Float, height: Float) {
        for (row in 0 until ATMOSPHERE_GRID) for (col in 0 until ATMOSPHERE_GRID) {
            val p = atmosphereControlPoint(row, col, timeSeconds, seeds[row * ATMOSPHERE_GRID + col])
            val i = (row * ATMOSPHERE_GRID + col) * 2
            control[i] = p.x * width
            control[i + 1] = p.y * height
        }
        // Horizontal pass: blend each control row at every sample column.
        for (row in 0 until ATMOSPHERE_GRID) for (s in 0 until MESH_SIDE) {
            var x = 0f
            var y = 0f
            for (k in 0 until 4) {
                val w = basis.positionWeight[s * 4 + k]
                val c = (row * ATMOSPHERE_GRID + basis.index[s * 4 + k]) * 2
                x += w * control[c]
                y += w * control[c + 1]
            }
            val o = (row * MESH_SIDE + s) * 2
            rowBlend[o] = x
            rowBlend[o + 1] = y
        }
        // Vertical pass: blend the four row results at every sample row.
        for (sy in 0 until MESH_SIDE) for (sx in 0 until MESH_SIDE) {
            var x = 0f
            var y = 0f
            for (k in 0 until 4) {
                val w = basis.positionWeight[sy * 4 + k]
                val r = (basis.index[sy * 4 + k] * MESH_SIDE + sx) * 2
                x += w * rowBlend[r]
                y += w * rowBlend[r + 1]
            }
            val o = (sy * MESH_SIDE + sx) * 2
            positions[o] = x
            positions[o + 1] = y
        }
    }

    private fun meshColors(grid: List<Color>): IntArray {
        require(grid.size == ATMOSPHERE_GRID * ATMOSPHERE_GRID)
        val channels = FloatArray(grid.size * 3)
        grid.forEachIndexed { i, c -> channels[i * 3] = c.red; channels[i * 3 + 1] = c.green; channels[i * 3 + 2] = c.blue }
        val rows = FloatArray(ATMOSPHERE_GRID * MESH_SIDE * 3)
        for (row in 0 until ATMOSPHERE_GRID) for (s in 0 until MESH_SIDE) for (ch in 0 until 3) {
            var v = 0f
            for (k in 0 until 4) v += basis.colorWeight[s * 4 + k] * channels[(row * ATMOSPHERE_GRID + basis.index[s * 4 + k]) * 3 + ch]
            rows[(row * MESH_SIDE + s) * 3 + ch] = v
        }
        val out = IntArray(MESH_VERTICES)
        for (sy in 0 until MESH_SIDE) for (sx in 0 until MESH_SIDE) {
            val rgb = IntArray(3)
            for (ch in 0 until 3) {
                var v = 0f
                for (k in 0 until 4) v += basis.colorWeight[sy * 4 + k] * rows[(basis.index[sy * 4 + k] * MESH_SIDE + sx) * 3 + ch]
                rgb[ch] = (v.coerceIn(0f, 1f) * 255f).roundToInt()
            }
            out[sy * MESH_SIDE + sx] = (0xFF shl 24) or (rgb[0] shl 16) or (rgb[1] shl 8) or rgb[2]
        }
        return out
    }
}

/** For each sample along one axis: the four control indices it blends and their weights. */
private class SampleBasis {
    val index = IntArray(MESH_SIDE * 4)
    val positionWeight = FloatArray(MESH_SIDE * 4)
    val colorWeight = FloatArray(MESH_SIDE * 4)

    init {
        val last = ATMOSPHERE_GRID - 1
        for (s in 0 until MESH_SIDE) {
            val patch = minOf(last - 1, s / MESH_SEGMENTS_PER_PATCH)
            val t = s.toFloat() / MESH_SEGMENTS_PER_PATCH - patch
            val t2 = t * t
            val t3 = t2 * t
            val u = 1f - t
            index[s * 4] = (patch - 1).coerceAtLeast(0)
            index[s * 4 + 1] = patch
            index[s * 4 + 2] = patch + 1
            index[s * 4 + 3] = (patch + 2).coerceAtMost(last)
            positionWeight[s * 4] = 0.5f * (-t3 + 2f * t2 - t)
            positionWeight[s * 4 + 1] = 0.5f * (3f * t3 - 5f * t2 + 2f)
            positionWeight[s * 4 + 2] = 0.5f * (-3f * t3 + 4f * t2 + t)
            positionWeight[s * 4 + 3] = 0.5f * (t3 - t2)
            colorWeight[s * 4] = u * u * u / 6f
            colorWeight[s * 4 + 1] = (3f * t3 - 6f * t2 + 4f) / 6f
            colorWeight[s * 4 + 2] = (-3f * t3 + 3f * t2 + 3f * t + 1f) / 6f
            colorWeight[s * 4 + 3] = t3 / 6f
        }
    }
}

private fun meshIndices(): ShortArray {
    val quads = MESH_SIDE - 1
    val out = ShortArray(quads * quads * 6)
    var n = 0
    for (j in 0 until quads) for (i in 0 until quads) {
        val a = j * MESH_SIDE + i
        val b = a + 1
        val c = a + MESH_SIDE
        val d = c + 1
        out[n++] = a.toShort(); out[n++] = c.toShort(); out[n++] = b.toShort()
        out[n++] = b.toShort(); out[n++] = c.toShort(); out[n++] = d.toShort()
    }
    return out
}
