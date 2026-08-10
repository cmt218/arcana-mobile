@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.arcana.mobile.analytics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.arcana.mobile.data.FavoritesDto
import org.arcana.mobile.data.StudioDto
import org.arcana.mobile.data.StudioLocationDto
import org.arcana.mobile.favorites.FavoritesRepository
import org.arcana.mobile.networking.FavoritesApi
import org.arcana.mobile.studios.StudioSelectionViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FavoritesTelemetryTest {
    @BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private val solidcore = StudioDto(
        id = 1, slug = "solidcore", name = "SolidCore",
        locations = listOf(StudioLocationDto(41, "SolidCore Williamsburg"), StudioLocationDto(42, "SolidCore Cobble Hill")),
    )
    private val yobk = StudioDto(id = 2, slug = "yo-bk", name = "YO BK", locations = listOf(StudioLocationDto(7, "YO BK Williamsburg")))

    private class FakeApi(val studios: List<StudioDto>) : FavoritesApi {
        override suspend fun fetchStudios() = studios
        override suspend fun fetchFavorites() = FavoritesDto()
        override suspend fun updateFavorites(studioSlugs: List<String>, locationIds: List<Int>) = FavoritesDto()
    }

    @Test fun `saving favorites emits per-studio and per-location adds plus a summary`() = runTest {
        val (telemetry, analytics, _) = fakeTelemetry()
        val api = FakeApi(listOf(solidcore, yobk))
        val vm = StudioSelectionViewModel(api, FavoritesRepository(api), telemetry)
        vm.toggleStudio("yo-bk")            // whole studio
        vm.toggleLocation("solidcore", 41)  // single location
        vm.save()

        val adds = analytics.events.filter { it.name == "favorite_added" }
        assertEquals(2, adds.size)
        assertTrue(adds.any { it.properties["type"] == "studio" && it.properties["studio_slug"] == "yo-bk" })
        assertTrue(adds.any { it.properties["type"] == "location" && it.properties["location_id"] == 41 })

        val saved = analytics.first("favorites_saved")!!
        assertEquals(1, saved.properties["studio_count"])
        assertEquals(1, saved.properties["location_count"])
        assertTrue(analytics.personProperties.isNotEmpty())  // favorite-studios profile set
    }
}
