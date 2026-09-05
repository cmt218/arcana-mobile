package org.arcana.mobile.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * travellingDotOffsetX is the one piece of real arithmetic behind the
 * travelling dot; everything else is Compose plumbing. Expected values below
 * are worked out by hand against a 1080px bar (a realistic device width) split
 * into three equal 360px slots, with a 12px dot (a 4dp dot at 3x density) —
 * never by re-deriving the function's own formula.
 */
class TabBarTest {
    private val barWidthPx = 1080
    private val dotWidthPx = 12

    @Test
    fun `position zero centers the dot in the first of three slots`() {
        // Slot 0 spans 0 to 360; its center is 180, minus half the dot's width.
        assertEquals(174, travellingDotOffsetX(0f, barWidthPx, 3, dotWidthPx))
    }

    @Test
    fun `position one centers the dot in the second of three slots`() {
        // Slot 1 spans 360 to 720; its center is 540, minus half the dot's width.
        assertEquals(534, travellingDotOffsetX(1f, barWidthPx, 3, dotWidthPx))
    }

    @Test
    fun `position two centers the dot in the third of three slots`() {
        // Slot 2 spans 720 to 1080; its center is 900, minus half the dot's width.
        assertEquals(894, travellingDotOffsetX(2f, barWidthPx, 3, dotWidthPx))
    }

    @Test
    fun `position one half centers the dot on the border between the first two slots`() {
        // Halfway from position 0 to 1 lands exactly on their shared edge at
        // 360, minus half the dot's width.
        assertEquals(354, travellingDotOffsetX(0.5f, barWidthPx, 3, dotWidthPx))
    }

    @Test
    fun `an odd dot width rounds to the nearest pixel rather than truncating`() {
        // 180 minus 5.5 is 174.5; nearest-pixel rounding gives 175, truncation would give 174.
        assertEquals(175, travellingDotOffsetX(0f, barWidthPx, 3, 11))
    }
}
