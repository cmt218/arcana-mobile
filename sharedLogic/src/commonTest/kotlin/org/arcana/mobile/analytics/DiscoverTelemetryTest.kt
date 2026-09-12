@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.arcana.mobile.analytics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.arcana.mobile.data.DiscoverDirectoryDto
import org.arcana.mobile.data.DiscoverStudioDto
import org.arcana.mobile.data.StudioPageDto
import org.arcana.mobile.data.StudioPageLocationDto
import org.arcana.mobile.discover.DiscoverViewModel
import org.arcana.mobile.discover.StudioPageViewModel
import org.arcana.mobile.favorites.FavoritesRepository
import org.arcana.mobile.data.FavoritesDto
import org.arcana.mobile.data.StudioDto
import org.arcana.mobile.networking.DiscoverApi
import org.arcana.mobile.networking.FavoritesApi
import org.arcana.mobile.schedule.ScheduleScopeRequests
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Locks the Discover taxonomy: names and property keys are dashboard contracts. */
class DiscoverTelemetryTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun teardown() { Dispatchers.resetMain() }

    private class FakeApi : DiscoverApi {
        override suspend fun fetchDirectory(categories: Set<String>, neighborhoods: Set<String>) =
            DiscoverDirectoryDto(listOf(DiscoverStudioDto("solidcore", "[solidcore]")))
        override suspend fun fetchStudioPage(brandSlug: String) = StudioPageDto(
            slug = brandSlug, name = "[solidcore]",
            locations = listOf(StudioPageLocationDto(1, "Chelsea"), StudioPageLocationDto(2, "FiDi")),
        )
    }

    private class FakeFavorites : FavoritesApi {
        override suspend fun fetchStudios(): List<StudioDto> = emptyList()
        override suspend fun fetchFavorites() = FavoritesDto()
        override suspend fun updateFavorites(studioSlugs: List<String>, locationIds: List<Int>) = FavoritesDto()
    }

    @Test fun `event names are stable`() {
        assertEquals("discover_opened", Telemetry.Events.DISCOVER_OPENED)
        assertEquals("discover_filter_changed", Telemetry.Events.DISCOVER_FILTER_CHANGED)
        assertEquals("studio_page_viewed", Telemetry.Events.STUDIO_PAGE_VIEWED)
        assertEquals("studio_schedule_tapped", Telemetry.Events.STUDIO_SCHEDULE_TAPPED)
        assertEquals("studio_favorite_tapped", Telemetry.Events.STUDIO_FAVORITE_TAPPED)
        assertEquals("instructor_tapped", Telemetry.Events.INSTRUCTOR_TAPPED)
        assertEquals("Discover", Telemetry.Screens.DISCOVER)
        assertEquals("StudioPage", Telemetry.Screens.STUDIO_PAGE)
    }

    @Test fun `directory opened once and filter counts on every change`() = runTest {
        val fake = FakeAnalytics()
        val vm = DiscoverViewModel(FakeApi(), Telemetry(fake, NoopCrashReporter))
        vm.onOpened(); vm.onOpened()
        vm.toggleCategory("yoga")
        vm.toggleNeighborhood("Harlem")
        assertEquals(listOf("discover_opened", "discover_filter_changed", "discover_filter_changed"), fake.events.map { it.name })
        assertEquals(mapOf<String, Any?>("category_count" to 1, "neighborhood_count" to 1), fake.events[2].properties)
    }

    @Test fun `studio page events carry the brand slug`() = runTest {
        val fake = FakeAnalytics()
        val vm = StudioPageViewModel(
            "solidcore", "directory", FakeApi(), FavoritesRepository(FakeFavorites()), ScheduleScopeRequests(),
            Telemetry(fake, NoopCrashReporter),
        )
        vm.requestSchedule()
        vm.onFavoriteTapped()
        vm.onInstructorTapped(77)
        assertEquals(
            listOf("studio_page_viewed", "studio_schedule_tapped", "studio_favorite_tapped", "instructor_tapped"),
            fake.events.map { it.name },
        )
        assertEquals(mapOf<String, Any?>("brand_slug" to "solidcore", "source" to "directory"), fake.events[0].properties)
        assertEquals(mapOf<String, Any?>("brand_slug" to "solidcore", "location_count" to 2, "result" to "sheet"), fake.events[2].properties)
        assertEquals(mapOf<String, Any?>("profile_id" to 77, "source" to "studio_page"), fake.events[3].properties)
    }
}
