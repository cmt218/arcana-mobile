package org.arcana.mobile.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AtmosphereMathTest {
    private val seeds = atmosphereSeeds(Random(7))

    @Test
    fun sixteen_colours_in_the_spec_order() {
        assertEquals(16, ATMOSPHERE_COLORS.size)
        assertEquals(
            listOf(
                Color(0xFFEEEDDC), Color(0xFFEBEBD4), Color(0xFFEBEBD4), Color(0xFFEEEDDC),
                Color(0xFFEBEBD4), Color(0xFFC5CCA6), Color(0xFFD3D5AB), Color(0xFFEBEBD4),
                Color(0xFFEBEBD4), Color(0xFFD3D5AB), Color(0xFFC5CCA6), Color(0xFFEBEBD4),
                Color(0xFFEEEDDC), Color(0xFFEBEBD4), Color(0xFFEBEBD4), Color(0xFFEEEDDC),
            ),
            ATMOSPHERE_COLORS,
        )
    }

    @Test
    fun vignette_matches_spec_color_and_alpha() {
        assertEquals(Color(0xFFC6CA91), ATMOSPHERE_VIGNETTE)
        assertEquals(0.06f, ATMOSPHERE_VIGNETTE_ALPHA)
    }

    @Test
    fun fallback_mid_color_matches_the_mesh_tint() {
        assertEquals(Color(0xFFEBEBD4), ATMOSPHERE_FALLBACK_MID)
    }

    @Test
    fun corners_never_move() {
        for (t in listOf(0f, 1.7f, 33f)) {
            assertEquals(Offset(0f, 0f), atmosphereControlPoint(0, 0, t, seeds[0]))
            assertEquals(Offset(1f, 1f), atmosphereControlPoint(3, 3, t, seeds[15]))
        }
    }

    @Test
    fun edge_points_slide_only_along_their_edge() {
        for (t in listOf(0f, 2.3f, 9f)) {
            val top = atmosphereControlPoint(0, 1, t, seeds[1])
            assertEquals(0f, top.y)
            val left = atmosphereControlPoint(2, 0, t, seeds[8])
            assertEquals(0f, left.x)
        }
    }

    @Test
    fun interior_points_stay_within_amplitude_of_their_base() {
        for (t in 0 until 200) {
            val p = atmosphereControlPoint(1, 2, t / 10f, seeds[6])
            assertTrue(kotlin.math.abs(p.x - 2f / 3f) <= ATMOSPHERE_AMPLITUDE + 1e-5f)
            assertTrue(kotlin.math.abs(p.y - 1f / 3f) <= ATMOSPHERE_AMPLITUDE + 1e-5f)
        }
    }

    @Test
    fun interior_point_excursion_reaches_the_full_amplitude() {
        var maxDx = 0f
        var maxDy = 0f
        for (i in 0 until 1250) {
            val p = atmosphereControlPoint(1, 2, i * 0.02f, seeds[6])
            maxDx = maxOf(maxDx, kotlin.math.abs(p.x - 2f / 3f))
            maxDy = maxOf(maxDy, kotlin.math.abs(p.y - 1f / 3f))
        }
        assertTrue(kotlin.math.abs(maxDx - ATMOSPHERE_AMPLITUDE) < 0.005f)
        assertTrue(kotlin.math.abs(maxDy - ATMOSPHERE_AMPLITUDE) < 0.005f)
    }

    @Test
    fun edge_point_excursion_is_sixty_percent_of_interior_amplitude() {
        var maxDx = 0f
        for (i in 0 until 1250) {
            val p = atmosphereControlPoint(0, 1, i * 0.02f, seeds[1])
            maxDx = maxOf(maxDx, kotlin.math.abs(p.x - 1f / 3f))
        }
        assertTrue(kotlin.math.abs(maxDx - ATMOSPHERE_AMPLITUDE * 0.6f) < 0.005f)
    }

    @Test
    fun seeds_scale_the_base_periods_within_the_spec_band() {
        for (s in seeds) {
            assertTrue(s.periodX in 6.0f * 0.8f..6.0f * 1.4f)
            assertTrue(s.periodY in 7.5f * 0.8f..7.5f * 1.4f)
        }
    }
}
