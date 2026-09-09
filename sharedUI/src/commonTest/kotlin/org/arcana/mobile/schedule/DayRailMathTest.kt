package org.arcana.mobile.schedule

import kotlin.test.Test
import kotlin.test.assertEquals

class DayRailMathTest {
    private val chip = 56f
    private val gap = 8f

    @Test
    fun pill_sits_on_the_chip_at_whole_positions() {
        assertEquals(0f, railPillOffsetPx(0f, chip, gap))
        assertEquals(64f, railPillOffsetPx(1f, chip, gap))
        assertEquals(192f, railPillOffsetPx(3f, chip, gap))
    }

    @Test
    fun pill_is_halfway_between_chips_at_a_half_page() {
        assertEquals(32f, railPillOffsetPx(0.5f, chip, gap))
    }

    @Test
    fun scroll_target_keeps_a_visible_chip_where_it_is() {
        // viewport 300 shows chips 0..3 fully; chip 2 needs no scroll.
        assertEquals(
            0f,
            railScrollTargetPx(2, chip, gap, viewportPx = 300f, currentScrollPx = 0f, marginPx = 24f, contentStartPx = 0f),
        )
    }

    @Test
    fun scroll_target_brings_a_hidden_chip_to_the_edge_with_a_margin() {
        // chip 6 starts at 384 and ends at 440; viewport 300 at scroll 0 ends at 300.
        // Target scroll = chipEnd - viewport + margin(24) = 164.
        assertEquals(
            164f,
            railScrollTargetPx(6, chip, gap, viewportPx = 300f, currentScrollPx = 0f, marginPx = 24f, contentStartPx = 0f),
        )
        // Scrolling back: chip 0 hidden to the left at scroll 164 → target = chipStart - margin, floored at 0.
        assertEquals(
            0f,
            railScrollTargetPx(0, chip, gap, viewportPx = 300f, currentScrollPx = 164f, marginPx = 24f, contentStartPx = 0f),
        )
    }

    @Test
    fun scroll_target_for_the_last_chip_in_a_full_window() {
        // Real density-3 geometry (iPhone 17 Pro Max / emulator-5554, both d=3): chip 168px,
        // gap 24px, stride 192px, 24dp rail margin/content-start = 72px, viewport 1320px
        // (440dp Pro Max width), WINDOW_DAYS = 15 so the last chip is index 14.
        // True span = [72 + 14*192, +168] = [2760, 2928]; content width = 3000, maxValue = 1680.
        // Target = trueEnd - viewport + margin = 2928 - 1320 + 72 = 1680, i.e. exactly maxValue.
        assertEquals(
            1680f,
            railScrollTargetPx(14, 168f, 24f, viewportPx = 1320f, currentScrollPx = 0f, marginPx = 72f, contentStartPx = 72f),
        )
    }

    @Test
    fun scroll_target_leaves_an_already_visible_middle_chip_untouched() {
        // Chip 7: start = 7*64 = 448, end = 448+56 = 504. Rail already scrolled to 400 with a
        // 300px viewport (visible window 400..700). end+margin = 528 <= 700 and start-margin = 424 >= 400,
        // so the chip already sits inside the visible window and neither edge condition fires.
        assertEquals(
            400f,
            railScrollTargetPx(7, chip, gap, viewportPx = 300f, currentScrollPx = 400f, marginPx = 24f, contentStartPx = 0f),
        )
    }

    @Test
    fun scroll_target_for_the_left_branch_has_a_true_margin_at_density_three() {
        // Same real geometry as the last-chip test, rail already scrolled all the way right
        // (currentScrollPx = 1680, its maxValue). Chip 5 true span = [72 + 5*192, +168] =
        // [1032, 1200], entirely left of the visible window [1680, 3000], so the right-edge
        // branch cannot fire; this takes the left branch. Target = trueStart - margin =
        // 1032 - 72 = 960, leaving exactly 72px (24dp) of margin ahead of the chip. Index 5,
        // not 0, so the maxOf(0f, ...) floor never engages and the margin arithmetic is
        // actually exercised.
        assertEquals(
            960f,
            railScrollTargetPx(5, 168f, 24f, viewportPx = 1320f, currentScrollPx = 1680f, marginPx = 72f, contentStartPx = 72f),
        )
    }
}
