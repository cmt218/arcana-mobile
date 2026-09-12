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

/** A studio page's "See schedule" hand-off ([ScheduleScopeRequests]) into the Book tab. */
class ScheduleViewModelBrandScopeTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun teardown() { Dispatchers.resetMain() }

    private val barrys = ScheduleScopeRequest("barrys", "Barry's", listOf(1, 2, 3))

    private fun catalog() = overviewOf(
        overviewStudio("barrys", "Barry's", locationIds = listOf(1, 2, 3)),
        overviewStudio("yo-bk", "YO BK", locationIds = listOf(10, 11)),
    )

    private fun vm(favorites: FavoritesDto, requests: ScheduleScopeRequests, api: FakeScheduleApi = FakeScheduleApi()): ScheduleViewModel {
        api.overviewResult = { catalog() }
        return ScheduleViewModel(
            api = api,
            favoritesRepository = FavoritesRepository(FakeFavoritesApi(favoritesResult = favorites)),
            bookingApi = FakeBookingApi(),
            scopeRequests = requests,
        )
    }

    @Test fun `a request pending before the VM exists wins over the cold-start favorites scope`() = runTest {
        val requests = ScheduleScopeRequests().apply { request(barrys) }
        val api = FakeScheduleApi()
        val vm = vm(FavoritesDto(locations = listOf(favLocation(31))), requests, api)
        settleFilters()
        val state = vm.success()
        assertEquals(ScopeMode.AllStudios, state.scope)
        assertEquals(setOf(1, 2, 3), state.filters.locationIds)
        assertEquals(barrys, state.scopedBrand)
        assertNull(requests.pending.value, "the request is consumed once applied")
        assertEquals(listOf(1, 2, 3), api.pageCalls.last().locationIds)
    }

    @Test fun `removing the chip restores Favorites for a member who has them`() = runTest {
        val requests = ScheduleScopeRequests().apply { request(barrys) }
        val vm = vm(FavoritesDto(locations = listOf(favLocation(31))), requests)
        settleFilters()
        vm.clearBrandScope()
        settleFilters()
        val state = vm.success()
        assertEquals(ScopeMode.Favorites, state.scope)
        assertEquals(ScheduleFilters(), state.filters)
        assertNull(state.scopedBrand)
    }

    @Test fun `a request arriving later restores the previous manual picks on removal`() = runTest {
        val requests = ScheduleScopeRequests()
        val vm = vm(FavoritesDto(), requests)
        settleFilters()
        vm.toggleStudioWhole("yo-bk")
        settleFilters()
        requests.request(barrys)
        settleFilters()
        assertEquals(setOf(1, 2, 3), vm.success().filters.locationIds)
        assertEquals(emptySet(), vm.success().filters.studioSlugs)
        vm.clearBrandScope()
        settleFilters()
        val state = vm.success()
        assertEquals(setOf("yo-bk"), state.filters.studioSlugs)
        assertNull(state.scopedBrand)
    }

    @Test fun `a manual pick drops the chip without restoring`() = runTest {
        val requests = ScheduleScopeRequests().apply { request(barrys) }
        val vm = vm(FavoritesDto(), requests)
        settleFilters()
        vm.toggleLocation("yo-bk", 10)
        settleFilters()
        val state = vm.success()
        assertNull(state.scopedBrand)
        assertEquals(setOf(1, 2, 3, 10), state.filters.locationIds)
        vm.clearBrandScope()
        assertEquals(setOf(1, 2, 3, 10), vm.success().filters.locationIds, "nothing to restore once the chip is gone")
    }
}
