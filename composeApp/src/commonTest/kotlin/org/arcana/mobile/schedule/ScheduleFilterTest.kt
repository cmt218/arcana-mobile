package org.arcana.mobile.schedule

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScheduleFilterTest {

    // ── expandSelectionToLocationIds ───────────────────────────────────────

    private val catalog = mapOf(
        "barrys" to listOf(1, 2, 3),
        "yo-bk" to listOf(10, 11),
    )

    @Test fun `whole studio expands to its catalog location ids`() {
        assertEquals(
            listOf(1, 2, 3),
            expandSelectionToLocationIds(setOf("barrys"), emptySet(), catalog),
        )
    }

    @Test fun `whole studio plus individual locations union and dedupe`() {
        assertEquals(
            listOf(1, 2, 3, 11),
            expandSelectionToLocationIds(setOf("barrys"), setOf(11), catalog),
        )
    }

    @Test fun `overlapping whole-studio and explicit location dedupe`() {
        assertEquals(
            listOf(1, 2, 3),
            expandSelectionToLocationIds(setOf("barrys"), setOf(2), catalog),
        )
    }

    @Test fun `empty selection expands to empty`() {
        assertEquals(emptyList(), expandSelectionToLocationIds(emptySet(), emptySet(), catalog))
    }

    @Test fun `unknown slug contributes nothing`() {
        assertEquals(listOf(11), expandSelectionToLocationIds(setOf("ghost"), setOf(11), catalog))
    }

    // ── time filter ────────────────────────────────────────────────────────

    @Test fun `formatTime12h converts 24h to friendly 12h`() {
        assertEquals("6:00 AM", formatTime12h("06:00"))
        assertEquals("12:00 PM", formatTime12h("12:00"))
        assertEquals("6:00 PM", formatTime12h("18:00"))
        assertEquals("12:00 AM", formatTime12h("00:00"))
        assertEquals("9:30 PM", formatTime12h("21:30"))
    }

    @Test fun `presets carry the expected NY bounds`() {
        assertEquals(null to "11:59", TimePreset.Morning.startGte to TimePreset.Morning.startLte)
        assertEquals("12:00" to "16:59", TimePreset.Afternoon.startGte to TimePreset.Afternoon.startLte)
        assertEquals("17:00" to null, TimePreset.Evening.startGte to TimePreset.Evening.startLte)
    }

    @Test fun `preset toFilter carries label + bounds`() {
        val f = TimePreset.Evening.toFilter()
        assertEquals("Evening", f.label)
        assertEquals("17:00", f.startGte)
        assertEquals(null, f.startLte)
    }

    @Test fun `custom range label reads both bounds`() {
        assertEquals("6:00 PM – 9:00 PM", customTimeLabel("18:00", "21:00"))
        assertEquals("After 6:00 AM", customTimeLabel("06:00", null))
        assertEquals("Before 9:00 PM", customTimeLabel(null, "21:00"))
    }

    @Test fun `customTimeFilter builds label + bounds`() {
        val f = customTimeFilter("18:00", "21:00")
        assertEquals("18:00", f.startGte)
        assertEquals("21:00", f.startLte)
        assertEquals("6:00 PM – 9:00 PM", f.label)
    }

    // ── Custom-range slider arithmetic ─────────────────────────────────────

    @Test fun `hhmmToMinutes parses hours and minutes`() {
        assertEquals(360, hhmmToMinutes("06:00"))
        assertEquals(570, hhmmToMinutes("09:30"))
        assertEquals(1320, hhmmToMinutes("22:00"))
        assertEquals(719, hhmmToMinutes("11:59"))
    }

    @Test fun `hhmmToMinutes is null for absent or unparseable input`() {
        assertNull(hhmmToMinutes(null))
        assertNull(hhmmToMinutes(""))
        assertNull(hhmmToMinutes("nope"))
    }

    @Test fun `minutesToHhmm zero-pads both components`() {
        assertEquals("06:00", minutesToHhmm(360))
        assertEquals("09:30", minutesToHhmm(570))
        assertEquals("22:00", minutesToHhmm(1320))
    }

    @Test fun `minutes round-trip through hhmm`() {
        // Every tick the slider can produce must survive the trip to the
        // TimeFilter's "HH:MM" bound and back.
        var m = TIME_SLIDER_MIN_MINUTE
        while (m <= TIME_SLIDER_MAX_MINUTE) {
            assertEquals(m, hhmmToMinutes(minutesToHhmm(m)))
            m += TIME_SLIDER_STEP_MINUTES
        }
    }

    @Test fun `snapMinutes snaps to the nearest half hour`() {
        assertEquals(540, snapMinutes(540))   // 09:00 exact
        assertEquals(570, snapMinutes(570))   // 09:30 exact — the new tick
        assertEquals(540, snapMinutes(547))   // 09:07 → 09:00
        assertEquals(570, snapMinutes(555))   // 09:15 → 09:30 (ties round up)
        assertEquals(570, snapMinutes(560))   // 09:20 → 09:30
        assertEquals(600, snapMinutes(590))   // 09:50 → 10:00
    }

    @Test fun `snapMinutes clamps to the slider bounds`() {
        assertEquals(TIME_SLIDER_MIN_MINUTE, snapMinutes(0))
        assertEquals(TIME_SLIDER_MIN_MINUTE, snapMinutes(300))    // 05:00
        assertEquals(TIME_SLIDER_MAX_MINUTE, snapMinutes(1439))   // 23:59
    }

    @Test fun `snapMinutes lands the off-tick presets on a tick`() {
        // The presets are deliberately off-tick (11:59 / 16:59); seeding the
        // custom slider from one must still produce a reachable handle value.
        assertEquals(720, snapMinutes(hhmmToMinutes("11:59")!!))   // → 12:00
        assertEquals(1020, snapMinutes(hhmmToMinutes("16:59")!!))  // → 17:00
    }

    @Test fun `slider bounds and gap are step-aligned`() {
        // The drag clamps (curTo - minGap, curFrom + minGap) only stay on ticks
        // if these are all multiples of the step.
        assertEquals(0, TIME_SLIDER_MIN_MINUTE % TIME_SLIDER_STEP_MINUTES)
        assertEquals(0, TIME_SLIDER_MAX_MINUTE % TIME_SLIDER_STEP_MINUTES)
        assertEquals(0, TIME_SLIDER_MIN_GAP_MINUTES % TIME_SLIDER_STEP_MINUTES)
    }
}
