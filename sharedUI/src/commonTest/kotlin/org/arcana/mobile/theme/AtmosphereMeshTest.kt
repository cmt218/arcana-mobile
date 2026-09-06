package org.arcana.mobile.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AtmosphereMeshTest {
    private val width = 1179f
    private val height = 2556f

    // Seeds only matter through atmosphereControlPoint; at t = 0 with zero phases every drift
    // term is sin(0) = 0 or cos(0) * amplitude, so use phases that zero both.
    private fun stillSeeds() = List(ATMOSPHERE_GRID * ATMOSPHERE_GRID) {
        PointSeed(periodX = 6f, periodY = 7.5f, phaseX = 0f, phaseY = (kotlin.math.PI / 2).toFloat())
    }

    @Test
    fun `index buffer covers every quad with two in-range triangles`() {
        val mesh = AtmosphereMesh(ATMOSPHERE_COLORS)
        val quads = MESH_SIDE - 1
        assertEquals(quads * quads * 6, mesh.indices.size)
        assertTrue(mesh.indices.all { it >= 0 && it < MESH_VERTICES })
        // Every vertex is referenced at least once.
        val used = BooleanArray(MESH_VERTICES)
        mesh.indices.forEach { used[it.toInt()] = true }
        assertTrue(used.all { it })
    }

    @Test
    fun `an undrifted grid lays out as a uniform lattice pinned to the corners`() {
        val mesh = AtmosphereMesh(ATMOSPHERE_COLORS)
        mesh.layout(stillSeeds(), 0f, width, height)
        fun x(sx: Int, sy: Int) = mesh.positions[(sy * MESH_SIDE + sx) * 2]
        fun y(sx: Int, sy: Int) = mesh.positions[(sy * MESH_SIDE + sx) * 2 + 1]
        val last = MESH_SIDE - 1
        assertEquals(0f, x(0, 0), 0.01f); assertEquals(0f, y(0, 0), 0.01f)
        assertEquals(width, x(last, 0), 0.01f); assertEquals(0f, y(last, 0), 0.01f)
        assertEquals(0f, x(0, last), 0.01f); assertEquals(height, y(0, last), 0.01f)
        assertEquals(width, x(last, last), 0.01f); assertEquals(height, y(last, last), 0.01f)
        // The middle patch blends four real control points, so Catmull-Rom reproduces the linear
        // grid there exactly; the edge patches clamp a phantom point and compress slightly, as the
        // prototype's did. Every row and column must still advance monotonically.
        val seg = MESH_SEGMENTS_PER_PATCH
        for (sy in 0 until MESH_SIDE) for (sx in seg..2 * seg) assertEquals(width * sx / last, x(sx, sy), 0.05f)
        for (sx in 0 until MESH_SIDE) for (sy in seg..2 * seg) assertEquals(height * sy / last, y(sx, sy), 0.05f)
        for (sy in 0 until MESH_SIDE) for (sx in 1 until MESH_SIDE) assertTrue(x(sx, sy) > x(sx - 1, sy))
        for (sx in 0 until MESH_SIDE) for (sy in 1 until MESH_SIDE) assertTrue(y(sx, sy) > y(sx, sy - 1))
    }

    @Test
    fun `drifted positions stay inside the surface and the corners never move`() {
        val mesh = AtmosphereMesh(ATMOSPHERE_COLORS)
        val seeds = atmosphereSeeds(Random(7))
        for (t in listOf(0.7f, 3.3f, 11f, 40.5f)) {
            mesh.layout(seeds, t, width, height)
            val last = MESH_SIDE - 1
            val corners = listOf(0 to 0, last to 0, 0 to last, last to last)
            for ((sx, sy) in corners) {
                val i = (sy * MESH_SIDE + sx) * 2
                assertEquals(if (sx == 0) 0f else width, mesh.positions[i], 0.01f)
                assertEquals(if (sy == 0) 0f else height, mesh.positions[i + 1], 0.01f)
            }
            // Catmull-Rom can overshoot a little between control points; the edge points slide
            // along their own edge, so the outline stays put and interior samples stay on-screen
            // with a small tolerance for that overshoot.
            for (v in 0 until MESH_VERTICES) {
                assertTrue(mesh.positions[v * 2] in -0.05f * width..1.05f * width)
                assertTrue(mesh.positions[v * 2 + 1] in -0.05f * height..1.05f * height)
            }
        }
    }

    @Test
    fun `a flat colour grid renders as exactly that colour everywhere`() {
        val flat = Color(0xFFEBEBD4)
        val mesh = AtmosphereMesh(List(ATMOSPHERE_GRID * ATMOSPHERE_GRID) { flat })
        assertTrue(mesh.colors.all { it == 0xFFEBEBD4.toInt() })
    }

    @Test
    fun `spline colours never overshoot the control colours`() {
        val mesh = AtmosphereMesh(ATMOSPHERE_COLORS)
        fun ch(c: Int, shift: Int) = (c shr shift) and 0xFF
        for (shift in listOf(16, 8, 0)) {
            val bounds = ATMOSPHERE_COLORS.map { (it.toArgb() shr shift) and 0xFF }
            val lo = bounds.min()
            val hi = bounds.max()
            assertTrue(mesh.colors.all { ch(it, shift) in lo..hi })
        }
        assertTrue(mesh.colors.all { (it ushr 24) == 0xFF })
    }

    @Test
    fun `the centre of the surface carries the deep tints`() {
        val mesh = AtmosphereMesh(ATMOSPHERE_COLORS)
        val mid = MESH_SIDE / 2
        val centre = mesh.colors[mid * MESH_SIDE + mid]
        val corner = mesh.colors[0]
        // The two shade tints sit in the middle of the grid, so the centre is darker than a corner.
        assertTrue((centre and 0xFF) < (corner and 0xFF))
        assertTrue(((centre shr 8) and 0xFF) < ((corner shr 8) and 0xFF))
    }
}
