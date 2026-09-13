package org.arcana.mobile.theme

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The mesh must never fold over itself: a flipped triangle paints over its
 * neighbours and shows up as a sawtooth seam in the field. Every triangle is
 * wound the same way at rest (negative signed area in screen space), so any
 * positive area is a fold. The thinnest-triangle floor guards against the
 * near-fold squeeze that reads as a hard ridge.
 */
class AtmosphereMeshFoldTest {
    private val seedsToTry = 40
    private val framesPerSeed = 40
    private val secondsPerFrame = 0.9f

    @Test fun `the drifting mesh never folds and never squeezes a triangle below a fifth of its rest area`() {
        val mesh = AtmosphereMesh(ATMOSPHERE_COLORS)
        val restArea = (1f / (MESH_SIDE - 1)) * (1f / (MESH_SIDE - 1)) / 2f
        var thinnest = Float.MAX_VALUE
        repeat(seedsToTry) { seedIndex ->
            val seeds = atmosphereSeeds(Random(seedIndex))
            repeat(framesPerSeed) { frame ->
                mesh.layout(seeds, frame * secondsPerFrame, 1f, 1f)
                val p = mesh.positions
                val idx = mesh.indices
                var i = 0
                while (i < idx.size) {
                    val a = idx[i].toInt() * 2
                    val b = idx[i + 1].toInt() * 2
                    val c = idx[i + 2].toInt() * 2
                    val area = (p[b] - p[a]) * (p[c + 1] - p[a + 1]) - (p[b + 1] - p[a + 1]) * (p[c] - p[a])
                    assertTrue(area < 0f, "folded triangle at seed $seedIndex frame $frame (area $area)")
                    if (-area < thinnest) thinnest = -area
                    i += 3
                }
            }
        }
        assertTrue(
            thinnest >= 0.2f * restArea,
            "thinnest triangle is ${thinnest / restArea} of its rest area; the amplitude squeezes the mesh too hard",
        )
    }
}
