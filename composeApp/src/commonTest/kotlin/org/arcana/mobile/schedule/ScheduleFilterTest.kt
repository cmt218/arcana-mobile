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

    // ── filterSummary ──────────────────────────────────────────────────────

    private val names = mapOf("barrys" to "Barry's", "yo-bk" to "YO BK")
    private val locStudio = mapOf(1 to "barrys", 2 to "barrys", 10 to "yo-bk", 11 to "yo-bk")

    private fun summary(
        mode: FilterMode,
        studios: Set<String> = emptySet(),
        locations: Set<Int> = emptySet(),
        modalities: Set<String> = emptySet(),
    ) = filterSummary(mode, studios, locations, names, locStudio, modalities)

    @Test fun `favorites mode reads Favorites`() {
        assertEquals("Favorites", summary(FilterMode.Favorites))
    }

    @Test fun `all-studios mode reads All Studios`() {
        assertEquals("All Studios", summary(FilterMode.AllStudios))
    }

    @Test fun `custom mode with nothing selected reads Filter`() {
        assertEquals("Filter", summary(FilterMode.Custom))
    }

    @Test fun `custom one whole studio reads its name uppercased`() {
        assertEquals("BARRY'S", summary(FilterMode.Custom, studios = setOf("barrys")))
    }

    @Test fun `custom one studio with two locations`() {
        assertEquals("BARRY'S · 2 locations", summary(FilterMode.Custom, locations = setOf(1, 2)))
    }

    @Test fun `custom one studio with one location is singular`() {
        assertEquals("BARRY'S · 1 location", summary(FilterMode.Custom, locations = setOf(1)))
    }

    @Test fun `custom multiple studios touched read N Studios`() {
        assertEquals("2 Studios", summary(FilterMode.Custom, studios = setOf("barrys"), locations = setOf(10)))
    }

    @Test fun `modalities mode with none picked reads Modalities`() {
        assertEquals("Modalities", summary(FilterMode.Modalities))
    }

    @Test fun `modalities mode with one pick is singular`() {
        assertEquals("1 modality", summary(FilterMode.Modalities, modalities = setOf("Reformer")))
    }

    @Test fun `modalities mode with two picks is plural`() {
        assertEquals(
            "2 modalities",
            summary(FilterMode.Modalities, modalities = setOf("Reformer", "Cycle")),
        )
    }
}
