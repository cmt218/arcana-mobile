@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.arcana.mobile.analytics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.arcana.mobile.data.FavoritesDto
import org.arcana.mobile.data.OverviewBrandDto
import org.arcana.mobile.data.OverviewBrandLocationDto
import org.arcana.mobile.data.ScheduleOverviewDto
import org.arcana.mobile.schedule.FakeScheduleApi
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
        override suspend fun updateFavorites(studioSlugs: List<String>, locationIds: List<Int>, brandSlugs: List<String>) = FavoritesDto()
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

    @Test fun `a brand card saves as a brand favorite`() = runTest {
        val (telemetry, analytics, _) = fakeTelemetry()
        val fidi = StudioDto(id = 101, slug = "id-hot-yoga-fidi", name = "ID Hot Yoga FiDi", locations = listOf(StudioLocationDto(1, "ID Hot Yoga FiDi")))
        val harlem = StudioDto(id = 102, slug = "id-hot-yoga-harlem", name = "ID Hot Yoga Harlem", locations = listOf(StudioLocationDto(2, "ID Hot Yoga Harlem")))
        val brand = OverviewBrandDto(
            id = 900, slug = "id-hot-yoga", name = "ID Hot Yoga",
            locations = listOf(OverviewBrandLocationDto(1, "ID Hot Yoga FiDi", studioId = 101), OverviewBrandLocationDto(2, "ID Hot Yoga Harlem", studioId = 102)),
        )
        val api = FakeApi(listOf(fidi, harlem, yobk))
        val schedule = FakeScheduleApi().apply { overviewResult = { ScheduleOverviewDto(brands = listOf(brand)) } }
        val vm = StudioSelectionViewModel(api, FavoritesRepository(api), telemetry, scheduleApi = schedule)
        vm.toggleStudio("id-hot-yoga")
        vm.save()

        val add = analytics.events.single { it.name == "favorite_added" }
        assertEquals("brand", add.properties["type"])
        assertEquals("id-hot-yoga", add.properties["studio_slug"])
        val saved = analytics.first("favorites_saved")!!
        assertEquals(0, saved.properties["studio_count"])
        assertEquals(1, saved.properties["brand_count"])
        assertEquals("id-hot-yoga", saved.properties["brand_slugs"])
    }
}
