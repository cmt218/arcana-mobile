package org.arcana.mobile.schedule

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for the pure `computeCapacityTier` helper that drives the Schedule
 * row's capacity overline and the Detail availability block.
 *
 * The function returns a four-tier label for studios that publish capacity
 * (the default), and a binary label (Available / Full) for studios that
 * don't (e.g. ID Hot Yoga — their Mindbody site hides capacity from the
 * consumer marketplace API, so we can't infer a fill % and must collapse).
 */
class CapacityTierTest {
    // ── Default (publishes_capacity = true) — existing four-tier behavior ────

    @Test
    fun zero_available_is_full() {
        assertEquals(
            CapacityTier.Full,
            computeCapacityTier(available = 0, offered = 20, publishesCapacity = true),
        )
    }

    @Test
    fun one_available_is_almost_full() {
        assertEquals(
            CapacityTier.AlmostFull,
            computeCapacityTier(available = 1, offered = 20, publishesCapacity = true),
        )
    }

    @Test
    fun two_available_is_almost_full() {
        assertEquals(
            CapacityTier.AlmostFull,
            computeCapacityTier(available = 2, offered = 20, publishesCapacity = true),
        )
    }

    @Test
    fun forty_percent_or_less_available_is_filling_up() {
        // 8 / 20 = 0.40 → FillingUp (boundary)
        assertEquals(
            CapacityTier.FillingUp,
            computeCapacityTier(available = 8, offered = 20, publishesCapacity = true),
        )
    }

    @Test
    fun above_forty_percent_available_is_available() {
        // 9 / 20 = 0.45 → Available
        assertEquals(
            CapacityTier.Available,
            computeCapacityTier(available = 9, offered = 20, publishesCapacity = true),
        )
    }

    @Test
    fun negative_available_is_full() {
        // Defensive guard for over-booked sessions returned by upstream.
        assertEquals(
            CapacityTier.Full,
            computeCapacityTier(available = -1, offered = 20, publishesCapacity = true),
        )
    }

    // ── Hidden (publishes_capacity = false) — binary Available / Full ───────

    @Test
    fun hidden_capacity_with_available_spots_is_available() {
        // ID Hot Yoga case: cap inference gives offered = openings.
        // Any positive available → AVAILABLE pill, no scarce shading.
        assertEquals(
            CapacityTier.Available,
            computeCapacityTier(available = 33, offered = 33, publishesCapacity = false),
        )
    }

    @Test
    fun hidden_capacity_with_one_spot_does_not_become_almost_full() {
        // A genuine 1-spot-left class for a hidden-capacity studio still
        // renders as plain AVAILABLE — we can't trust the "1 spot" signal
        // because we don't know how many are really booked.
        assertEquals(
            CapacityTier.Available,
            computeCapacityTier(available = 1, offered = 33, publishesCapacity = false),
        )
    }

    @Test
    fun hidden_capacity_with_zero_available_is_full() {
        // When openings = 0 the class is genuinely not bookable; the binary
        // FULL pill is correct and the row's CTA collapses to the "+"
        // affordance.
        assertEquals(
            CapacityTier.Full,
            computeCapacityTier(available = 0, offered = 33, publishesCapacity = false),
        )
    }

    // ── NotOpen (Mariana Tek booking window not open yet) ────────────────────

    @Test
    fun not_open_takes_precedence_over_full() {
        // A not-open class arrives with available = 0 (the server zeroes spots
        // until the window opens). NotOpen must win over Full so it reads
        // "NOT OPEN", not the misleading "FULL".
        assertEquals(
            CapacityTier.NotOpen,
            computeCapacityTier(available = 0, offered = 20, publishesCapacity = true, notOpen = true),
        )
    }

    @Test
    fun not_open_takes_precedence_over_available() {
        assertEquals(
            CapacityTier.NotOpen,
            computeCapacityTier(available = 12, offered = 20, publishesCapacity = true, notOpen = true),
        )
    }

    @Test
    fun not_open_applies_to_hidden_capacity_too() {
        assertEquals(
            CapacityTier.NotOpen,
            computeCapacityTier(available = 0, offered = 33, publishesCapacity = false, notOpen = true),
        )
    }

    @Test
    fun open_class_is_unaffected_by_notOpen_false() {
        // Regression guard: the default notOpen = false keeps existing behavior.
        assertEquals(
            CapacityTier.Available,
            computeCapacityTier(available = 12, offered = 20, publishesCapacity = true, notOpen = false),
        )
    }
}
