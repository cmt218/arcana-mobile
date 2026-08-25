package org.arcana.mobile.ui

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The nudges are measured constants, not derived ones, so nothing but a test
 * stops a refactor from quietly changing what ships. These pin the values
 * against the on-device measurements they came from.
 */
class OpticalCenteringTest {

    /** 3x density: 1sp = 3px, which is how the screenshots are measured. */
    private fun px(sp: Float) = sp * 3f

    @Test
    fun `cap nudge cancels the measured 4px rise of a 13sp link label`() {
        // TextLink measured 4.0px high against its arrow on a 3x screenshot,
        // at both call sites. The nudge must land the residual inside 0.5pt.
        val residualPx = 4.0f - px(capNudgeSp(13f))
        assertTrue(
            abs(residualPx) / 3f < 0.5f,
            "13sp cap nudge leaves ${residualPx / 3f}pt, over the 0.5pt bar",
        )
    }

    @Test
    fun `cap nudge scales with type size`() {
        assertEquals(capNudgeSp(26f), capNudgeSp(13f) * 2f, 0.0001f)
        assertEquals(0f, capNudgeSp(0f))
    }

    /** The whole point of the vertical-only variant. A start-aligned label
     *  never splits the trailing letter-space, so nudging it right moves it out
     *  of its gutter and corrects nothing. */
    @Test
    fun `the horizontal nudge is not zero and so must stay off a start-aligned label`() {
        val sideways = trailingSpaceNudgeSp(13f, 0.14f)
        assertTrue(px(sideways) > 3f, "expected a visible shift, got ${px(sideways)}px")
    }

    @Test
    fun `the horizontal nudge grows with tracking`() {
        assertTrue(trailingSpaceNudgeSp(13f, 0.14f) > trailingSpaceNudgeSp(13f, 0.10f))
    }
}
