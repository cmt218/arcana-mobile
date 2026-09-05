package org.arcana.mobile.ui

import androidx.compose.ui.graphics.luminance
import org.arcana.mobile.theme.Clay
import org.arcana.mobile.theme.Moss
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PressableTest {
    @Test
    fun pressed_shade_is_darker_than_the_fill() {
        assertTrue(Moss.pressedShade().luminance() < Moss.luminance())
        assertTrue(Clay.pressedShade().luminance() < Clay.luminance())
    }

    @Test
    fun pressed_shade_keeps_the_hue_family() {
        // Moss stays green: green channel still dominates red and blue.
        val shade = Moss.pressedShade()
        assertTrue(shade.green > shade.red && shade.green > shade.blue)
    }

    @Test
    fun pressed_shade_is_pinned_to_a_twelve_percent_step_toward_ink() {
        // lerp(Moss, Ink, 0.12) by hand, then rounded to the nearest 1/255 step:
        // Color's sRGB storage is 8 bits per channel, so the stored result is
        // quantized even though the interpolation itself is continuous.
        val shade = Moss.pressedShade()
        assertEquals(38f / 255f, shade.red, absoluteTolerance = 0.0005f)
        assertEquals(55f / 255f, shade.green, absoluteTolerance = 0.0005f)
        assertEquals(21f / 255f, shade.blue, absoluteTolerance = 0.0005f)
    }
}
