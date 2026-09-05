package org.arcana.mobile.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.ui.unit.Dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MotionTest {
    @Test
    fun durations_are_ordered_quick_to_long() {
        assertTrue(Dur.Quick < Dur.Short && Dur.Short < Dur.Medium && Dur.Medium < Dur.Long)
        assertEquals(120, Dur.Quick)
        assertEquals(200, Dur.Short)
        assertEquals(340, Dur.Medium)
        assertEquals(480, Dur.Long)
    }

    @Test
    fun emphasized_easing_decelerates() {
        // The search reveal's curve: past halfway it is already most of the way there.
        assertTrue(Ease.Emphasized.transform(0.5f) > 0.8f)
    }

    @Test
    fun exit_easing_accelerates() {
        // The bound only Exit's control points satisfy; a linear curve (0.5) or the
        // Emphasized curve (~0.878) would both fail it.
        assertTrue(Ease.Exit.transform(0.5f) < 0.3f)
    }

    @Test
    fun easing_curves_are_pinned_to_their_spec_control_points() {
        assertEquals(CubicBezierEasing(0.2f, 0f, 0f, 1f), Ease.Emphasized)
        assertEquals(CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f), Ease.Exit)
    }

    @Test
    fun kick_is_the_only_bouncy_spring() {
        assertTrue(Springs.Kick.dampingRatio < 0.7f)
        assertTrue(Springs.Snappy.dampingRatio >= 0.8f)
        assertTrue(Springs.Settle.dampingRatio >= 0.8f)
    }

    @Test
    fun spring_presets_match_spec_values() {
        assertEquals(0.85f, Springs.Snappy.dampingRatio)
        assertEquals(Spring.StiffnessMedium, Springs.Snappy.stiffness)

        assertEquals(0.90f, Springs.Settle.dampingRatio)
        assertEquals(Spring.StiffnessMediumLow, Springs.Settle.stiffness)

        assertEquals(0.65f, Springs.Kick.dampingRatio)
        assertEquals(Spring.StiffnessMedium, Springs.Kick.stiffness)
    }

    @Test
    fun generic_factories_instantiate_at_a_non_float_type() {
        val snappy = Springs.snappy<Dp>()
        assertEquals(0.85f, snappy.dampingRatio)
        assertEquals(Spring.StiffnessMedium, snappy.stiffness)

        val settle = Springs.settle<Dp>()
        assertEquals(0.90f, settle.dampingRatio)
        assertEquals(Spring.StiffnessMediumLow, settle.stiffness)

        val kick = Springs.kick<Dp>()
        assertEquals(0.65f, kick.dampingRatio)
        assertEquals(Spring.StiffnessMedium, kick.stiffness)
    }
}
