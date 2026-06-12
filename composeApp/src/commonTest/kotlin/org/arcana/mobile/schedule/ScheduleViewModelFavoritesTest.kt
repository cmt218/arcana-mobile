@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.arcana.mobile.schedule

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.arcana.mobile.data.FavoritesDto
import org.arcana.mobile.favorites.FavoritesRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Filter-mode scoping (Favorites / All Studios / Custom) on the paged
 *  pipeline. Filter mutations are debounced ([settleFilters] advances past the
 *  debounce). */
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

    /** Overview carrying barrys (3 locations) + yo-bk (2 locations). */
    private fun catalogOverview() = overviewOf(
        overviewStudio("barrys", "Barry's", locationIds = listOf(1, 2, 3)),
        overviewStudio("yo-bk", "YO BK", locationIds = listOf(10, 11)),
    )

    @Test fun `favorites present - first fetches carry expanded location ids in Favorites mode`() = runTest {
        val scheduleApi = FakeScheduleApi()
        val favoritesApi = FakeFavoritesApi(
            favoritesResult = FavoritesDto(
                studios = listOf(favStudio(locationIds = listOf(11, 12))),
                locations = listOf(favLocation(31)),
            ),
        )
        val vm = vm(scheduleApi, favoritesApi)

        assertEquals(listOf(11, 12, 31), scheduleApi.overviewCalls.single().locationIds)
        assertEquals(listOf(11, 12, 31), scheduleApi.pageCalls.single().locationIds)
        assertNull(scheduleApi.overviewCalls.single().studioSlugs)
        val state = vm.success()
        assertEquals(FilterMode.Favorites, state.filterMode)
        assertTrue(state.hasFavorites)
        assertEquals("Favorites", state.filterSummary)
    }

    @Test fun `favorites empty - first fetches have no location ids in All Studios mode`() = runTest {
        val scheduleApi = FakeScheduleApi()
        val favoritesApi = FakeFavoritesApi(favoritesResult = FavoritesDto())
        val vm = vm(scheduleApi, favoritesApi)

        assertNull(scheduleApi.overviewCalls.single().locationIds)
        assertNull(scheduleApi.pageCalls.single().locationIds)
        val state = vm.success()
        assertEquals(FilterMode.AllStudios, state.filterMode)
        assertEquals("All Studios", state.filterSummary)
    }

    @Test fun `entering filter mode and selecting a studio scopes to its locations`() = runTest {
        val scheduleApi = FakeScheduleApi()
        scheduleApi.overviewResult = { catalogOverview() }
        val favoritesApi = FakeFavoritesApi(
            favoritesResult = FavoritesDto(studios = listOf(favStudio(locationIds = listOf(99)))),
        )
        val vm = vm(scheduleApi, favoritesApi)
        assertEquals(FilterMode.Favorites, vm.success().filterMode)

        vm.enterFilterMode()
        vm.toggleStudioWhole("barrys")
        settleFilters()

        // Whole-studio barrys → its catalog location ids; studio_slug null.
        val call = scheduleApi.overviewCalls.last()
        assertNull(call.studioSlugs)
        assertEquals(listOf(1, 2, 3), call.locationIds)
        val state = vm.success()
        assertEquals(FilterMode.Custom, state.filterMode)
        assertEquals(setOf("barrys"), state.filters.studioSlugs)
        assertEquals("BARRY'S", state.filterSummary)
    }

    @Test fun `useMyFavorites after a manual selection re-applies favorites`() = runTest {
        val scheduleApi = FakeScheduleApi()
        scheduleApi.overviewResult = { catalogOverview() }
        val favoritesApi = FakeFavoritesApi(
            favoritesResult = FavoritesDto(studios = listOf(favStudio(locationIds = listOf(11, 12)))),
        )
        val vm = vm(scheduleApi, favoritesApi)
        vm.enterFilterMode()
        vm.toggleStudioWhole("barrys")
        settleFilters()
        assertEquals(FilterMode.Custom, vm.success().filterMode)

        vm.useMyFavorites()
        settleFilters()

        val call = scheduleApi.overviewCalls.last()
        assertEquals(listOf(11, 12), call.locationIds)
        assertNull(call.studioSlugs)
        val state = vm.success()
        assertEquals(FilterMode.Favorites, state.filterMode)
        assertTrue(state.filters.studioSlugs.isEmpty())
        assertTrue(state.filters.locationIds.isEmpty())
    }

    @Test fun `selecting all locations of a studio promotes to whole-studio`() = runTest {
        val scheduleApi = FakeScheduleApi()
        scheduleApi.overviewResult = { catalogOverview() }
        val vm = vm(scheduleApi, FakeFavoritesApi())

        vm.toggleLocation("yo-bk", 10)
        vm.toggleLocation("yo-bk", 11) // completes the set → promote
        settleFilters()

        val state = vm.success()
        assertEquals(FilterMode.Custom, state.filterMode)
        assertEquals(setOf("yo-bk"), state.filters.studioSlugs)
        assertTrue(state.filters.locationIds.isEmpty())
        // Expanded back to its locations for the fetch.
        assertEquals(listOf(10, 11), scheduleApi.overviewCalls.last().locationIds)
    }

    @Test fun `showAllStudios resets to all studios`() = runTest {
        val scheduleApi = FakeScheduleApi()
        scheduleApi.overviewResult = { catalogOverview() }
        val vm = vm(scheduleApi, FakeFavoritesApi())
        vm.toggleStudioWhole("barrys")
        settleFilters()

        vm.showAllStudios()
        settleFilters()

        assertNull(scheduleApi.overviewCalls.last().locationIds)
        val state = vm.success()
        assertEquals(FilterMode.AllStudios, state.filterMode)
        assertTrue(state.filters.studioSlugs.isEmpty())
        assertEquals("All Studios", state.filterSummary)
    }

    @Test fun `saving favorites in the manager from empty applies favorites`() = runTest {
        val scheduleApi = FakeScheduleApi()
        val favoritesApi = FakeFavoritesApi(favoritesResult = FavoritesDto())
        val repository = FavoritesRepository(favoritesApi)
        val vm = vm(scheduleApi, favoritesApi, repository)
        assertNull(scheduleApi.overviewCalls.single().locationIds)

        favoritesApi.favoritesResult =
            FavoritesDto(studios = listOf(favStudio(locationIds = listOf(11, 12))))
        repository.save(studioSlugs = listOf("barrys"), locationIds = emptyList())
        settleFilters()

        assertEquals(listOf(11, 12), scheduleApi.overviewCalls.last().locationIds)
        val state = vm.success()
        assertEquals(FilterMode.Favorites, state.filterMode)
        assertTrue(state.hasFavorites)
    }

    @Test fun `clearing favorites in the manager exits to all studios`() = runTest {
        val scheduleApi = FakeScheduleApi()
        val favoritesApi = FakeFavoritesApi(
            favoritesResult = FavoritesDto(studios = listOf(favStudio(locationIds = listOf(11)))),
        )
        val repository = FavoritesRepository(favoritesApi)
        val vm = vm(scheduleApi, favoritesApi, repository)
        assertEquals(listOf(11), scheduleApi.overviewCalls.single().locationIds)

        favoritesApi.favoritesResult = FavoritesDto()
        repository.save(studioSlugs = emptyList(), locationIds = emptyList())
        settleFilters()

        assertNull(scheduleApi.overviewCalls.last().locationIds)
        val state = vm.success()
        assertEquals(FilterMode.AllStudios, state.filterMode)
        assertTrue(!state.hasFavorites)
    }

    @Test fun `editing favorites does not clobber an active custom selection`() = runTest {
        val scheduleApi = FakeScheduleApi()
        scheduleApi.overviewResult = { catalogOverview() }
        val favoritesApi = FakeFavoritesApi(favoritesResult = FavoritesDto())
        val repository = FavoritesRepository(favoritesApi)
        val vm = vm(scheduleApi, favoritesApi, repository)
        vm.enterFilterMode()
        vm.toggleStudioWhole("barrys") // Custom selection active
        settleFilters()

        favoritesApi.favoritesResult =
            FavoritesDto(studios = listOf(favStudio(locationIds = listOf(11, 12))))
        repository.save(studioSlugs = listOf("yo-bk"), locationIds = emptyList())
        settleFilters()

        // Custom selection preserved; favorites NOT auto-applied.
        val state = vm.success()
        assertEquals(FilterMode.Custom, state.filterMode)
        assertEquals(setOf("barrys"), state.filters.studioSlugs)
    }

    @Test fun `filterStudios comes from the overview studios block sorted by name`() = runTest {
        val scheduleApi = FakeScheduleApi()
        scheduleApi.overviewResult = {
            overviewOf(
                overviewStudio("yo-bk", "YO BK", locationIds = listOf(45, 44)),
                overviewStudio("barrys", "Barry's", locationIds = listOf(1)),
            )
        }
        val vm = vm(scheduleApi, FakeFavoritesApi())

        val state = vm.success()
        assertEquals(listOf("barrys", "yo-bk"), state.filterStudios.map { it.slug })
        // Locations sorted by name; overviewStudio names them "loc<id>".
        assertEquals(listOf(44, 45), state.filterStudios.last().locations.map { it.id })
    }
}
