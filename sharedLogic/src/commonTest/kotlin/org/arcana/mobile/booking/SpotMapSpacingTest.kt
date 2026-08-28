package org.arcana.mobile.booking

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Spacing guarantees for the two spot maps, checked against every real room we
 * serve ([SpotRoomFixtures]).
 *
 * The full-screen map fixes the dot size and grows the canvas around it; the
 * inline map fixes the canvas and shrinks the dot into it. Both place dots
 * through [spotCenters], which insets each dot by its own diameter — so the
 * usable span is `box - dot`, not `box`. Sizing that ignores the inset comes up
 * exactly one dot short and the circles collide.
 */
class SpotMapSpacingTest {

    private val density = 3f
    private val nodePx = 46f * density // NODE_SIZE on a 3x screen

    // ── full-screen map ──────────────────────────────────────────────────────

    @Test
    fun every_real_room_places_full_screen_circles_clear_of_each_other() {
        val offenders = mutableListOf<String>()
        SpotRoomFixtures.rooms.forEach { (name, coords) ->
            val layout = normalizeSpots(SpotRoomFixtures.parse(coords))
            val (w, h) = spotContentSize(layout, nodePx)
            val gap = closestSpotGap(layout, w, h, nodePx)
            if (gap < nodePx) offenders += "$name: gap ${gap / nodePx} of a dot"
        }
        assertTrue(offenders.isEmpty(), "overlapping rooms:\n" + offenders.joinToString("\n"))
    }

    @Test
    fun full_screen_spacing_is_uniform_across_rooms() {
        // Every room should land on the SAME closest-pair spacing — the sizing is
        // driven by that pair, so anything else means the inset math is off.
        SpotRoomFixtures.rooms.forEach { (name, coords) ->
            val layout = normalizeSpots(SpotRoomFixtures.parse(coords))
            val (w, h) = spotContentSize(layout, nodePx)
            assertEquals(
                nodePx * (1f + SPOT_GAP_FRACTION),
                closestSpotGap(layout, w, h, nodePx),
                nodePx * 0.01f,
                name,
            )
        }
    }

    @Test
    fun shallow_two_row_room_does_not_collapse_vertically() {
        // NRTHRN Strong: 2 rows, ~22 columns. The short axis is barely taller than
        // one dot, so the missing inset cost most of the row-to-row gap.
        val spots = buildList {
            for (x in 1..12) add(spot(x.toDouble(), 0.0))
            for (x in 1..10) add(spot(x.toDouble() + 0.5, 1.0))
        }
        val layout = normalizeSpots(spots)
        val (w, h) = spotContentSize(layout, nodePx)
        assertTrue(closestSpotGap(layout, w, h, nodePx) >= nodePx, "rows collided")
    }

    @Test
    fun content_box_keeps_the_rooms_proportions() {
        // Proportions live on the span of spot CENTRES (box - dot), which is what
        // the normalized coordinates map onto.
        val layout = normalizeSpots(SpotRoomFixtures.parse(SpotRoomFixtures.rooms[0].second))
        val (w, h) = spotContentSize(layout, nodePx)
        assertEquals(layout.bboxAspect, (w - nodePx) / (h - nodePx), 0.01f)
    }

    @Test
    fun content_box_is_capped_for_a_pathologically_dense_room() {
        // Two spots a hair apart would otherwise demand a canvas of millions of px.
        val layout = normalizeSpots(listOf(spot(0.0, 0.0), spot(0.001, 0.0), spot(100.0, 50.0)))
        val (w, h) = spotContentSize(layout, nodePx, maxSide = 12000f)
        assertTrue(w <= 12000f && h <= 12000f, "w=$w h=$h")
    }

    @Test
    fun a_single_spot_still_produces_a_usable_canvas() {
        val layout = normalizeSpots(listOf(spot(4.0, 4.0)))
        val (w, h) = spotContentSize(layout, nodePx)
        assertTrue(w >= nodePx && h >= nodePx, "w=$w h=$h")
    }

    // ── inline preview map ───────────────────────────────────────────────────

    @Test
    fun every_real_room_keeps_inline_preview_circles_clear() {
        val boxW = 361f // sheet content width on a 393dp phone
        val offenders = mutableListOf<String>()
        SpotRoomFixtures.rooms.forEach { (name, coords) ->
            val layout = normalizeSpots(SpotRoomFixtures.parse(coords))
            val boxH = (boxW / layout.bboxAspect).coerceIn(96f, 300f)
            val even = minOf(boxW / layout.cols, boxH / layout.rows).coerceIn(14f, 40f)
            val dot = maxSpotDot(layout, boxW, boxH, drawnInset = 2f, clear = 2f, minDot = 14f, maxDot = even)
            val drawn = dot - 4f
            val gap = closestSpotGap(layout, boxW, boxH, dot)
            if (dot > 14f && gap < drawn) offenders += "$name: circles overlap by ${drawn - gap}dp"
        }
        assertTrue(offenders.isEmpty(), "overlapping previews:\n" + offenders.joinToString("\n"))
    }

    @Test
    fun inline_preview_dot_never_grows_past_the_requested_cap() {
        // Shrink-only: a room that already fits must be left exactly as it was.
        val layout = normalizeSpots(SpotRoomFixtures.parse(SpotRoomFixtures.rooms[0].second))
        val dot = maxSpotDot(layout, 361f, 120f, drawnInset = 2f, clear = 2f, minDot = 14f, maxDot = 14f)
        assertEquals(14f, dot, 1e-3f)
    }

    @Test
    fun inline_preview_dot_respects_the_minimum_even_when_nothing_fits() {
        // A room too dense for any legible dot keeps the 14dp floor rather than
        // vanishing — the preview is a glance, the full-screen map is the detail.
        val spots = (0..200).map { spot(it * 0.01, 0.0) }
        val layout = normalizeSpots(spots)
        val dot = maxSpotDot(layout, 361f, 96f, drawnInset = 2f, clear = 2f, minDot = 14f, maxDot = 40f)
        assertEquals(14f, dot, 1e-3f)
    }

    // ── label fitting ────────────────────────────────────────────────────────

    @Test
    fun a_label_that_already_fits_keeps_the_design_size() {
        assertEquals(12, fitLabelSize(naturalPx = 80f, availablePx = 126f, atSize = 12, minSize = 7))
        // Exactly filling the box still counts as fitting.
        assertEquals(12, fitLabelSize(naturalPx = 126f, availablePx = 126f, atSize = 12, minSize = 7))
    }

    @Test
    fun an_oversized_label_shrinks_to_something_that_fits() {
        // The Pack's "10,BENCH": ~178px of text in a 126px circle at 12sp.
        val fitted = fitLabelSize(naturalPx = 178f, availablePx = 126f, atSize = 12, minSize = 7)
        assertTrue(fitted < 12, "did not shrink: $fitted")
        assertTrue(178f * fitted / 12f <= 126f, "still overflows at ${fitted}sp")
    }

    @Test
    fun shrinking_stops_at_the_floor() {
        assertEquals(7, fitLabelSize(naturalPx = 4000f, availablePx = 126f, atSize = 12, minSize = 7))
    }

    @Test
    fun a_floor_above_the_design_size_never_grows_the_label() {
        // The inline map draws at 10sp; a 12sp floor must not push past it.
        assertEquals(10, fitLabelSize(naturalPx = 4000f, availablePx = 126f, atSize = 10, minSize = 12))
    }

    @Test
    fun degenerate_measurements_fall_back_to_the_design_size() {
        assertEquals(12, fitLabelSize(naturalPx = 0f, availablePx = 126f, atSize = 12, minSize = 7))
        assertEquals(12, fitLabelSize(naturalPx = 100f, availablePx = 0f, atSize = 12, minSize = 7))
    }

    // ── centres ──────────────────────────────────────────────────────────────

    @Test
    fun centres_are_inset_by_half_a_dot_at_the_frame_edges() {
        val layout = normalizeSpots(listOf(spot(0.0, 0.0), spot(10.0, 10.0)), marginFraction = 0f)
        val placed = spotCenters(layout, boxW = 200f, boxH = 100f, dot = 20f)
        assertEquals(10f, placed[0].cx, 1e-3f)
        assertEquals(10f, placed[0].cy, 1e-3f)
        assertEquals(190f, placed[1].cx, 1e-3f)
        assertEquals(90f, placed[1].cy, 1e-3f)
    }

    private var nextId = 1
    private fun spot(x: Double, y: Double) =
        org.arcana.mobile.data.SpotDto(id = nextId++, label = "S$nextId", positionX = x, positionY = y)
}
