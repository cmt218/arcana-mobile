@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.arcana.mobile.schedule

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.arcana.mobile.data.FavoritesDto
import org.arcana.mobile.data.OverviewLocationDto
import org.arcana.mobile.data.OverviewStudioDto
import org.arcana.mobile.data.ScheduleOverviewDto
import org.arcana.mobile.favorites.FavoritesRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Favorites scoping + chip behavior on the paged (Phase 2) pipeline. Every
 *  refetch is now an overview + page-1 pair; filter mutations are debounced
 *  ([settleFilters] advances virtual time past the debounce window). */
class ScheduleViewModelFavoritesTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun teardown() { Dispatchers.resetMain() }

    private fun vm(
        scheduleApi: FakeScheduleApi,
        favoritesApi: FakeFavoritesApi,
        repository: FavoritesRepository = FavoritesRepository(favoritesApi),
        bookingApi: FakeBookingApi = FakeBookingApi(),
    ): ScheduleViewModel = ScheduleViewModel(
        api = scheduleApi,
        favoritesRepository = repository,
        bookingApi = bookingApi,
    )

    @Test fun `favorites present - both first fetches carry expanded location ids`() = runTest {
        val scheduleApi = FakeScheduleApi()
        val favoritesApi = FakeFavoritesApi(
            favoritesResult = FavoritesDto(
                studios = listOf(favStudio(locationIds = listOf(11, 12))),
                locations = listOf(favLocation(31)),
            ),
        )
        val vm = vm(scheduleApi, favoritesApi)

        assertEquals(1, scheduleApi.overviewCalls.size)
        assertEquals(1, scheduleApi.pageCalls.size)
        assertEquals(listOf(11, 12, 31), scheduleApi.overviewCalls.single().locationIds)
        assertEquals(listOf(11, 12, 31), scheduleApi.pageCalls.single().locationIds)
        assertNull(scheduleApi.pageCalls.single().cursor)
        val state = vm.success()
        assertTrue(state.favoritesMode)
        assertTrue(state.hasFavorites)
    }

    @Test fun `favorites empty - first fetches have no location ids and mode stays off`() = runTest {
        val scheduleApi = FakeScheduleApi()
        val favoritesApi = FakeFavoritesApi(favoritesResult = FavoritesDto())
        val vm = vm(scheduleApi, favoritesApi)

        assertEquals(1, scheduleApi.overviewCalls.size)
        assertNull(scheduleApi.overviewCalls.single().locationIds)
        assertNull(scheduleApi.pageCalls.single().locationIds)
        val state = vm.success()
        assertFalse(state.favoritesMode)
        assertFalse(state.hasFavorites)
    }

    @Test fun `studio-grain favorite with zero locations - no empty location param but mode is on`() = runTest {
        val scheduleApi = FakeScheduleApi()
        val favoritesApi = FakeFavoritesApi(
            favoritesResult = FavoritesDto(studios = listOf(favStudio(locationIds = emptyList()))),
        )
        val vm = vm(scheduleApi, favoritesApi)

        assertEquals(1, scheduleApi.overviewCalls.size)
        assertNull(scheduleApi.overviewCalls.single().locationIds)
        assertNull(scheduleApi.pageCalls.single().locationIds)
        val state = vm.success()
        assertTrue(state.favoritesMode)
        assertTrue(state.hasFavorites)
    }

    @Test fun `toggleStudio in favoritesMode exits mode and solos the studio server-side`() = runTest {
        val scheduleApi = FakeScheduleApi()
        val favoritesApi = FakeFavoritesApi(
            favoritesResult = FavoritesDto(studios = listOf(favStudio(locationIds = listOf(11)))),
        )
        val vm = vm(scheduleApi, favoritesApi)
        assertEquals(1, scheduleApi.overviewCalls.size)

        vm.toggleStudio("barrys")

        // Chips update instantly; the refetch waits out the debounce.
        val pending = vm.success()
        assertFalse(pending.favoritesMode)
        assertEquals(setOf("barrys"), pending.filters.studioSlugs)
        assertTrue(pending.refreshingFilters)
        assertEquals(1, scheduleApi.overviewCalls.size)

        settleFilters()

        assertEquals(2, scheduleApi.overviewCalls.size)
        assertEquals(2, scheduleApi.pageCalls.size)
        assertEquals(listOf("barrys"), scheduleApi.overviewCalls[1].studioSlugs)
        assertNull(scheduleApi.overviewCalls[1].locationIds)
        assertEquals(listOf("barrys"), scheduleApi.pageCalls[1].studioSlugs)
        assertNull(scheduleApi.pageCalls[1].locationIds)
        val state = vm.success()
        assertFalse(state.favoritesMode)
        assertTrue(state.hasFavorites)
        assertFalse(state.refreshingFilters)
        assertEquals(setOf("barrys"), state.filters.studioSlugs)
    }

    @Test fun `enterFavoritesMode after exiting re-fetches with the favorite ids and clears filters`() = runTest {
        val scheduleApi = FakeScheduleApi()
        val favoritesApi = FakeFavoritesApi(
            favoritesResult = FavoritesDto(studios = listOf(favStudio(locationIds = listOf(11, 12)))),
        )
        val vm = vm(scheduleApi, favoritesApi)
        vm.toggleStudio("barrys")
        settleFilters()
        assertEquals(2, scheduleApi.overviewCalls.size)

        vm.enterFavoritesMode()
        settleFilters()

        assertEquals(3, scheduleApi.overviewCalls.size)
        assertEquals(3, scheduleApi.pageCalls.size)
        assertEquals(listOf(11, 12), scheduleApi.overviewCalls[2].locationIds)
        assertNull(scheduleApi.overviewCalls[2].studioSlugs)
        assertEquals(listOf(11, 12), scheduleApi.pageCalls[2].locationIds)
        val state = vm.success()
        assertTrue(state.favoritesMode)
        assertTrue(state.filters.studioSlugs.isEmpty())
        assertTrue(state.filters.locationIds.isEmpty())
    }

    @Test fun `saving favorites in the manager re-enters favorites mode and refetches`() = runTest {
        val scheduleApi = FakeScheduleApi()
        val favoritesApi = FakeFavoritesApi(favoritesResult = FavoritesDto())
        val repository = FavoritesRepository(favoritesApi)
        val vm = vm(scheduleApi, favoritesApi, repository)
        assertEquals(1, scheduleApi.overviewCalls.size)
        assertNull(scheduleApi.overviewCalls.single().locationIds)

        // The favorites manager saves a new set while this VM sits on the
        // back stack — the repository StateFlow is the change signal.
        favoritesApi.favoritesResult =
            FavoritesDto(studios = listOf(favStudio(locationIds = listOf(11, 12))))
        repository.save(studioSlugs = listOf("barrys"), locationIds = emptyList())
        settleFilters()

        assertEquals(2, scheduleApi.overviewCalls.size)
        assertEquals(listOf(11, 12), scheduleApi.overviewCalls[1].locationIds)
        assertEquals(listOf(11, 12), scheduleApi.pageCalls[1].locationIds)
        val state = vm.success()
        assertTrue(state.favoritesMode)
        assertTrue(state.hasFavorites)
    }

    @Test fun `clearing favorites in the manager exits favorites mode and refetches`() = runTest {
        val scheduleApi = FakeScheduleApi()
        val favoritesApi = FakeFavoritesApi(
            favoritesResult = FavoritesDto(studios = listOf(favStudio(locationIds = listOf(11)))),
        )
        val repository = FavoritesRepository(favoritesApi)
        val vm = vm(scheduleApi, favoritesApi, repository)
        assertEquals(1, scheduleApi.overviewCalls.size)
        assertEquals(listOf(11), scheduleApi.overviewCalls.single().locationIds)

        favoritesApi.favoritesResult = FavoritesDto()
        repository.save(studioSlugs = emptyList(), locationIds = emptyList())
        settleFilters()

        assertEquals(2, scheduleApi.overviewCalls.size)
        assertNull(scheduleApi.overviewCalls[1].locationIds)
        assertNull(scheduleApi.pageCalls[1].locationIds)
        val state = vm.success()
        assertFalse(state.favoritesMode)
        assertFalse(state.hasFavorites)
    }

    @Test fun `enterFavoritesMode is a no-op when already active`() = runTest {
        val scheduleApi = FakeScheduleApi()
        val favoritesApi = FakeFavoritesApi(
            favoritesResult = FavoritesDto(studios = listOf(favStudio(locationIds = listOf(11)))),
        )
        val vm = vm(scheduleApi, favoritesApi)
        assertEquals(1, scheduleApi.overviewCalls.size)

        vm.enterFavoritesMode()
        settleFilters()

        assertEquals(1, scheduleApi.overviewCalls.size) // no redundant refetch
        assertEquals(1, scheduleApi.pageCalls.size)
        assertTrue(vm.success().favoritesMode)
    }

    @Test fun `enterFavoritesMode is a no-op when the member has no favorites`() = runTest {
        val scheduleApi = FakeScheduleApi()
        val favoritesApi = FakeFavoritesApi(favoritesResult = FavoritesDto())
        val vm = vm(scheduleApi, favoritesApi)
        assertEquals(1, scheduleApi.overviewCalls.size)

        vm.enterFavoritesMode()
        settleFilters()

        assertEquals(1, scheduleApi.overviewCalls.size)
        assertFalse(vm.success().favoritesMode)
    }

    @Test fun `knownStudios comes from the overview studios block sorted by name`() = runTest {
        val scheduleApi = FakeScheduleApi()
        scheduleApi.overviewResult = {
            ScheduleOverviewDto(
                studios = listOf(
                    OverviewStudioDto(id = 2, slug = "yo-bk", name = "YO BK", primaryColor = "#3C5D1A"),
                    OverviewStudioDto(id = 1, slug = "barrys", name = "Barry's", primaryColor = "#F65713"),
                ),
            )
        }
        val vm = vm(scheduleApi, FakeFavoritesApi())

        val state = vm.success()
        assertEquals(
            listOf(
                StudioChipData(slug = "barrys", name = "Barry's", primaryColor = "#F65713"),
                StudioChipData(slug = "yo-bk", name = "YO BK", primaryColor = "#3C5D1A"),
            ),
            state.knownStudios,
        )
    }

    @Test fun `tier-2 location chips derive from the soloed overview studio`() = runTest {
        val scheduleApi = FakeScheduleApi()
        scheduleApi.overviewResult = {
            ScheduleOverviewDto(
                studios = listOf(
                    OverviewStudioDto(
                        id = 2, slug = "yo-bk", name = "YO BK", primaryColor = "#3C5D1A",
                        locations = listOf(
                            OverviewLocationDto(id = 45, name = "YO BK Williamsburg", timezone = "America/New_York"),
                            OverviewLocationDto(id = 44, name = "YO BK Cobble Hill", timezone = "America/New_York"),
                        ),
                    ),
                ),
            )
        }
        val vm = vm(scheduleApi, FakeFavoritesApi())
        assertTrue(vm.success().knownLocationsForBrand.isEmpty()) // nothing soloed yet

        vm.toggleStudio("yo-bk")

        // Sorted by full name; labels are brand-prefix-stripped + uppercased.
        assertEquals(
            listOf(
                LocationChipData(id = 44, shortLabel = "COBBLE HILL"),
                LocationChipData(id = 45, shortLabel = "WILLIAMSBURG"),
            ),
            vm.success().knownLocationsForBrand,
        )
    }
}
