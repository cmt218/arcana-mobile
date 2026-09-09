package org.arcana.mobile.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SlideToConfirmMathTest {
    @Test
    fun arms_at_eighty_five_percent() {
        assertFalse(slideArmed(0.84f))
        assertTrue(slideArmed(0.85f))
        assertTrue(slideArmed(1f))
    }

    @Test
    fun label_fades_out_by_two_thirds_of_the_travel() {
        assertEquals(1f, slideLabelAlpha(0f))
        assertTrue(slideLabelAlpha(0.3f) in 0.4f..0.6f)
        assertEquals(0f, slideLabelAlpha(0.7f))
        assertEquals(0f, slideLabelAlpha(1f))
    }
}
