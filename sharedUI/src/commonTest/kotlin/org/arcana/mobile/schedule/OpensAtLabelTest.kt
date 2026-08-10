package org.arcana.mobile.schedule

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * Tests for the "OPENS …" copy formatters. Both render the studio booking
 * window in **Eastern Time** (`America/New_York`) regardless of the device
 * zone — the windows are ET wall-clock rules and we want the copy to match the
 * studio's own site. Fixed `Instant`s keep these deterministic.
 */
class OpensAtLabelTest {
    // 2026-06-22T15:00:00Z = 11:00 AM EDT (UTC-4 in June), a Monday.
    private val mondayMorning = Instant.parse("2026-06-22T15:00:00Z")
    // 2026-06-22T23:30:00Z = 7:30 PM EDT.
    private val mondayEvening = Instant.parse("2026-06-22T23:30:00Z")
    // 2026-01-05T15:00:00Z = 10:00 AM EST (UTC-5 in winter), a Monday.
    private val winterMorning = Instant.parse("2026-01-05T15:00:00Z")

    @Test fun cta_label_morning_edt() {
        assertEquals("OPENS MON 11:00 AM ET", opensAtCtaLabel(mondayMorning))
    }

    @Test fun cta_label_evening_pm() {
        assertEquals("OPENS MON 7:30 PM ET", opensAtCtaLabel(mondayEvening))
    }

    @Test fun cta_label_respects_winter_est_offset() {
        // Same wall-clock UTC as the EDT case but one hour earlier in ET — proves
        // we localize to ET with DST, not a fixed offset.
        assertEquals("OPENS MON 10:00 AM ET", opensAtCtaLabel(winterMorning))
    }

    @Test fun availability_line_full_form() {
        assertEquals(
            "Booking opens Mon, Jun 22 · 11:00 AM ET",
            opensAtAvailabilityLine(mondayMorning),
        )
    }
}
