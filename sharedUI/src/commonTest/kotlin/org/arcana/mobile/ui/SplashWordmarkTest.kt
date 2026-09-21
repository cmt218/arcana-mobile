package org.arcana.mobile.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.arcana.mobile.theme.wordmarkGrid

/**
 * The splash's row-by-row redraw. Whatever the lead-in, it must end on the
 * settled wordmark: every dot full stone (breathing between 0.82 and 1), no lime.
 */
class SplashWordmarkTest {

    @Test fun `the timeline is sized to the wordmark grid it draws`() {
        assertEquals(wordmarkGrid.rows, WORDMARK_ROWS)
        assertEquals((wordmarkGrid.rows - 1) * REFRESH_ROW_STEP_MS + REFRESH_DOT_MS, SPLASH_WORDMARK_SETTLED_MS)
    }

    @Test fun `every dot has settled to stone with no lime by the settled time`() {
        val t = SPLASH_WORDMARK_SETTLED_MS.toFloat()
        wordmarkGrid.lit.forEachIndexed { index, (_, row) ->
            assertEquals(0f, refreshLime(row, t), "row $row still lime")
            val stone = refreshStone(row, t, index)
            assertTrue(stone in 0.82f..1f, "row $row at $stone")
        }
    }

    @Test fun `a row flashes lime before it turns stone`() {
        val early = REFRESH_DOT_MS * 0.15f
        assertTrue(refreshLime(0, early) > 0f)
        assertEquals(0f, refreshStone(0, early, 0))

        val late = REFRESH_DOT_MS * 0.8f
        assertEquals(0f, refreshLime(0, late))
        assertTrue(refreshStone(0, late, 0) > 0.6f)
    }

    @Test fun `rows light top to bottom and nothing shows before its turn`() {
        val lastRow = WORDMARK_ROWS - 1
        val beforeLastRow = lastRow * REFRESH_ROW_STEP_MS - 1f
        assertEquals(0f, refreshLime(lastRow, beforeLastRow))
        assertEquals(0f, refreshStone(lastRow, beforeLastRow, 0))
        assertTrue(refreshStone(0, beforeLastRow, 0) > 0f, "the top row is already lit")
    }
}
