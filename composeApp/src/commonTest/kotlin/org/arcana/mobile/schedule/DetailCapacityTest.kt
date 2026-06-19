package org.arcana.mobile.schedule

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for the pure `computeDetailCapacity` helper that drives the
 * ClassDetail availability block.
 *
 * Mirrors the philosophy of CapacityTierTest: for studios that publish
 * capacity we keep the existing Open / Scarce / Full progression. For
 * studios that don't (e.g. ID Hot Yoga), Scarce is meaningless — we
 * can't tell "filling fast" from "just opened" — and we collapse to
 * Open / Full only.
 */
class DetailCapacityTest {
    // ── Default (publishes_capacity = true) — existing three-state behavior ──

    @Test
    fun zero_available_is_full() {
        assertEquals(
            DetailCapacity.Full,
            computeDetailCapacity(available = 0, publishesCapacity = true),
        )
    }

    @Test
    fun one_or_two_available_is_scarce() {
        assertEquals(
            DetailCapacity.Scarce,
            computeDetailCapacity(available = 1, publishesCapacity = true),
        )
        assertEquals(
            DetailCapacity.Scarce,
            computeDetailCapacity(available = 2, publishesCapacity = true),
        )
    }

    @Test
    fun three_or_more_available_is_open() {
        assertEquals(
            DetailCapacity.Open,
            computeDetailCapacity(available = 3, publishesCapacity = true),
        )
    }

    @Test
    fun negative_available_is_full() {
        assertEquals(
            DetailCapacity.Full,
            computeDetailCapacity(available = -1, publishesCapacity = true),
        )
    }

    // ── Hidden capacity — binary Open / Full only ────────────────────────────

    @Test
    fun hidden_with_one_spot_is_open_not_scarce() {
        // A "1 spot left" hidden-capacity class collapses to Open. We have
        // no way to know whether 1 means "just one left of 20" or "just one
        // booked of 20 with 19 still open"; pretending it's Scarce would
        // mislead. Open is the safe truthful default.
        assertEquals(
            DetailCapacity.Open,
            computeDetailCapacity(available = 1, publishesCapacity = false),
        )
    }

    @Test
    fun hidden_with_many_spots_is_open() {
        assertEquals(
            DetailCapacity.Open,
            computeDetailCapacity(available = 33, publishesCapacity = false),
        )
    }

    @Test
    fun hidden_with_zero_spots_is_full() {
        // openings = 0 from upstream means genuinely not bookable. Binary
        // Full collapses cleanly into the full CTA.
        assertEquals(
            DetailCapacity.Full,
            computeDetailCapacity(available = 0, publishesCapacity = false),
        )
    }

    // ── NotOpen (Mariana Tek booking window not open yet) ────────────────────

    @Test
    fun not_open_takes_precedence_over_full() {
        // Server reports 0 spots for a not-open class; NotOpen must win so the
        // detail block reads "booking opens …" instead of FULL.
        assertEquals(
            DetailCapacity.NotOpen,
            computeDetailCapacity(available = 0, publishesCapacity = true, notOpen = true),
        )
    }

    @Test
    fun not_open_takes_precedence_over_open() {
        assertEquals(
            DetailCapacity.NotOpen,
            computeDetailCapacity(available = 8, publishesCapacity = true, notOpen = true),
        )
    }

    @Test
    fun open_class_is_unaffected_by_notOpen_false() {
        assertEquals(
            DetailCapacity.Open,
            computeDetailCapacity(available = 8, publishesCapacity = true, notOpen = false),
        )
    }
}
