package org.arcana.mobile.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DotMatrixLoaderTest {

    @Test
    fun intensityStaysInUnitRange() {
        for (p in 0..20) {
            val progress = p / 20f
            for (col in 0..8) for (row in 0..2) {
                val i = dotIntensity(progress, col, row)
                assertTrue(i in 0f..1f, "intensity $i out of range at p=$progress col=$col row=$row")
            }
        }
    }

    @Test
    fun intensityPeaksMidPhase() {
        // For dot (0,0) the phase equals progress directly: triangle wave peaking at 0.5.
        assertEquals(0f, dotIntensity(0f, 0, 0), absoluteTolerance = 0.001f)
        assertEquals(1f, dotIntensity(0.5f, 0, 0), absoluteTolerance = 0.001f)
        assertEquals(0f, dotIntensity(0.999f, 0, 0), absoluteTolerance = 0.01f)
    }

    @Test
    fun dotsOnDifferentDiagonalsAreOffsetInPhase() {
        // The traveling-wave effect: (col+row) shifts the phase, so dots on
        // different diagonals must not pulse in unison. (Dots sharing a
        // col+row diagonal pulse together by design.)
        val a = dotIntensity(0.25f, 0, 0)
        val b = dotIntensity(0.25f, 2, 1)
        assertTrue(a != b, "dots on different diagonals should differ in intensity")
    }

    @Test
    fun phaseWrapsAroundCleanly() {
        // A dot whose offset pushes the phase past 1.0 wraps instead of clamping.
        // Expectation derived from DOT_PHASE_STEP so a choreography tweak
        // doesn't falsely fail the wrap test.
        val progress = 0.95f
        val phase = (progress + (8 + 2) * DOT_PHASE_STEP) % 1f // 1.65 % 1 = 0.65 → second half
        val expected = (1f - phase) * 2f
        assertEquals(expected, dotIntensity(progress, 8, 2), absoluteTolerance = 0.001f)
    }
}
