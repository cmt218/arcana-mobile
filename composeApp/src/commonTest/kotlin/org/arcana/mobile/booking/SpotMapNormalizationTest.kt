package org.arcana.mobile.booking

import org.arcana.mobile.data.SpotDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for the pure spot-map helpers: [normalizeSpots] (bbox normalization +
 * aspect derivation, shared by Mariana Tek absolute coords and Arketa fractional
 * coords) and [shouldUseMap] (the map-vs-chips dispatch predicate).
 */
class SpotMapNormalizationTest {

    private var nextId = 1
    private fun spot(x: Double?, y: Double?, status: String = "available") =
        SpotDto(id = nextId++, label = "S${nextId}", positionX = x, positionY = y, status = status)

    // ── normalizeSpots ───────────────────────────────────────────────────────

    @Test
    fun barrys_wide_shallow_room_has_wide_aspect() {
        // x 1..23, y {0,3,5} → aspect ≈ 22/5 = 4.4, rendered wide not square.
        val spots = buildList {
            for (x in 1..23) add(spot(x.toDouble(), 0.0))   // treadmill back row
            for (x in 1..23) add(spot(x.toDouble(), 3.0))   // floor
            for (x in 1..23) add(spot(x.toDouble(), 5.0))   // floor
        }
        val layout = normalizeSpots(spots)
        assertEquals(69, layout.spots.size)
        assertEquals(3, layout.rows)
        assertEquals(23, layout.cols)
        assertTrue(layout.bboxAspect in 4.3f..4.5f, "aspect was ${layout.bboxAspect}")
    }

    @Test
    fun coordinates_stay_within_unit_space_with_margin() {
        val spots = listOf(spot(1.0, 0.0), spot(23.0, 5.0), spot(12.0, 3.0))
        val layout = normalizeSpots(spots)
        layout.spots.forEach {
            assertTrue(it.nx in 0f..1f, "nx=${it.nx}")
            assertTrue(it.ny in 0f..1f, "ny=${it.ny}")
        }
        // With marginFraction 0.05, the extremes land at 0.05 and 0.95.
        val minSpot = layout.spots.first { it.spot.positionX == 1.0 }
        val maxSpot = layout.spots.first { it.spot.positionX == 23.0 }
        assertEquals(0.05f, minSpot.nx, 1e-4f)
        assertEquals(0.95f, maxSpot.nx, 1e-4f)
    }

    @Test
    fun arketa_fractional_coords_normalize_sensibly() {
        // Already 0–1; spans preserved, aspect from actual spans (0.8/0.4 = 2.0).
        val spots = listOf(spot(0.1, 0.2), spot(0.9, 0.6), spot(0.5, 0.4))
        val layout = normalizeSpots(spots)
        assertTrue(layout.bboxAspect in 1.9f..2.1f, "aspect was ${layout.bboxAspect}")
        layout.spots.forEach { assertTrue(it.nx in 0f..1f && it.ny in 0f..1f) }
    }

    @Test
    fun single_row_centers_the_flat_axis_and_uses_wide_strip_aspect() {
        val spots = listOf(spot(1.0, 4.0), spot(5.0, 4.0), spot(9.0, 4.0))
        val layout = normalizeSpots(spots)
        assertEquals(1, layout.rows)
        layout.spots.forEach { assertEquals(0.5f, it.ny, 1e-4f) }
        assertEquals(6f, layout.bboxAspect) // WIDE_STRIP_ASPECT
    }

    @Test
    fun single_column_centers_the_flat_axis() {
        val spots = listOf(spot(2.0, 0.0), spot(2.0, 3.0), spot(2.0, 6.0))
        val layout = normalizeSpots(spots)
        assertEquals(1, layout.cols)
        layout.spots.forEach { assertEquals(0.5f, it.nx, 1e-4f) }
        assertTrue(layout.bboxAspect < 1f, "tall band expected, was ${layout.bboxAspect}")
    }

    @Test
    fun spots_without_coordinates_are_dropped() {
        val spots = listOf(spot(1.0, 0.0), spot(null, null), spot(3.0, 0.0))
        val layout = normalizeSpots(spots)
        assertEquals(2, layout.spots.size)
    }

    @Test
    fun all_null_coordinates_yields_empty_layout() {
        val layout = normalizeSpots(listOf(spot(null, null), spot(null, null)))
        assertTrue(layout.spots.isEmpty())
    }

    // ── shouldUseMap ─────────────────────────────────────────────────────────

    @Test
    fun grid_mode_with_full_coords_uses_map() {
        val spots = List(10) { spot(it.toDouble(), 0.0) }
        assertTrue(shouldUseMap(spots, "grid"))
    }

    @Test
    fun list_mode_never_uses_map() {
        val spots = List(10) { spot(it.toDouble(), 0.0) }
        assertFalse(shouldUseMap(spots, "list"))
    }

    @Test
    fun none_mode_never_uses_map() {
        val spots = List(10) { spot(it.toDouble(), 0.0) }
        assertFalse(shouldUseMap(spots, "none"))
    }

    @Test
    fun grid_mode_with_mostly_null_coords_falls_back() {
        // 2 of 10 have coords (20%) → below the 80% threshold.
        val spots = List(10) { i -> if (i < 2) spot(i.toDouble(), 0.0) else spot(null, null) }
        assertFalse(shouldUseMap(spots, "grid"))
    }

    @Test
    fun grid_mode_with_few_spots_still_uses_map() {
        // Mariana Tek (the only grid platform) sends the FULL room, so the map
        // shows regardless of how few spots are open — no minimum-count gate.
        val spots = listOf(spot(1.0, 0.0), spot(2.0, 0.0))
        assertTrue(shouldUseMap(spots, "grid"))
    }

    @Test
    fun grid_mode_with_no_spots_falls_back() {
        assertFalse(shouldUseMap(emptyList(), "grid"))
    }

    @Test
    fun grid_mode_at_eighty_percent_coverage_uses_map() {
        // 8 of 10 (exactly 80%) → boundary passes.
        val spots = List(10) { i -> if (i < 8) spot(i.toDouble(), 0.0) else spot(null, null) }
        assertTrue(shouldUseMap(spots, "grid"))
    }
}
