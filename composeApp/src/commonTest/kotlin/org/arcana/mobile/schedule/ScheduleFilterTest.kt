package org.arcana.mobile.schedule

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
