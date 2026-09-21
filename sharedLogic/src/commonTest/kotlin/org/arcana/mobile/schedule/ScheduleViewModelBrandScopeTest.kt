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

/** A studio page's Book button hand-off ([ScheduleScopeRequests]) into the Book tab. */
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

    // ── Studio chips: one per brand, read off the selection however it was made.

    @Test fun `chips tell a whole brand from part of one`() {
        val catalog = listOf(
            FilterStudio("barrys", "Barry's", "", listOf(FilterLocation(1, "Chelsea"), FilterLocation(2, "Noho"), FilterLocation(3, "Tribeca"))),
            FilterStudio("yo-bk", "YO BK", "", listOf(FilterLocation(10, "Williamsburg"), FilterLocation(11, "Greenpoint"))),
        )
        fun labels(filters: ScheduleFilters) = studioChips(catalog, filters).map { it.label }
        assertEquals(emptyList(), labels(ScheduleFilters()))
        assertEquals(listOf("Barry's"), labels(ScheduleFilters(studioSlugs = setOf("barrys"))))
        assertEquals(listOf("Barry's · Chelsea"), labels(ScheduleFilters(locationIds = setOf(1))))
        assertEquals(listOf("Barry's · 2 of 3"), labels(ScheduleFilters(locationIds = setOf(1, 3))))
        // Every location picked one by one is the whole brand, and reads like it.
        assertEquals(listOf("Barry's"), labels(ScheduleFilters(locationIds = setOf(1, 2, 3))))
        assertEquals(
            listOf("Barry's · Noho", "YO BK"),
            labels(ScheduleFilters(studioSlugs = setOf("yo-bk"), locationIds = setOf(2))),
            "catalog order, one chip per brand",
        )
    }

    @Test fun `manual picks under All Studios show chips and a chip's X drops that brand only`() = runTest {
        val vm = vm(FavoritesDto(), ScheduleScopeRequests())
        settleFilters()
        assertEquals(emptyList(), vm.success().studioChips)
        vm.toggleStudioWhole("yo-bk")
        vm.toggleLocation("barrys", 2)
        settleFilters()
        assertEquals(listOf("Barry's · loc2", "YO BK"), vm.success().studioChips.map { it.label })
        vm.removeStudioChip("barrys")
        settleFilters()
        val state = vm.success()
        assertEquals(listOf("YO BK"), state.studioChips.map { it.label })
        assertEquals(ScheduleFilters(studioSlugs = setOf("yo-bk")), state.filters)
        assertEquals(ScopeMode.AllStudios, state.scope)
    }

    @Test fun `see schedule for one location reads as that location and its X restores what was there`() = runTest {
        val requests = ScheduleScopeRequests().apply {
            request(ScheduleScopeRequest("barrys", "Barry's", listOf(2), label = "Barry's · Noho"))
        }
        val api = FakeScheduleApi()
        val vm = vm(FavoritesDto(locations = listOf(favLocation(31))), requests, api)
        settleFilters()
        assertEquals(listOf("Barry's · loc2"), vm.success().studioChips.map { it.label })
        assertEquals(listOf(2), api.pageCalls.last().locationIds)
        vm.removeStudioChip("barrys")
        settleFilters()
        assertEquals(ScopeMode.Favorites, vm.success().scope)
        assertEquals(emptyList(), vm.success().studioChips)
    }

    @Test fun `a scoped brand the catalog lacks still gets its chip`() = runTest {
        // No class in the window means no catalog entry; the chip must not vanish.
        val requests = ScheduleScopeRequests().apply {
            request(ScheduleScopeRequest("pvolve", "Pvolve", listOf(77), label = "Pvolve · Chelsea"))
        }
        val vm = vm(FavoritesDto(), requests)
        settleFilters()
        assertEquals(listOf(StudioChip("pvolve", "Pvolve · Chelsea")), vm.success().studioChips)
    }

    @Test fun `a manual pick after see schedule ends the restore and keeps the chips`() = runTest {
        val requests = ScheduleScopeRequests().apply { request(barrys) }
        val vm = vm(FavoritesDto(), requests)
        settleFilters()
        vm.toggleLocation("yo-bk", 10)
        settleFilters()
        val state = vm.success()
        assertNull(state.scopedBrand)
        assertEquals(setOf(1, 2, 3, 10), state.filters.locationIds)
        assertEquals(listOf("Barry's", "YO BK · loc10"), state.studioChips.map { it.label }, "the chips keep saying what is picked")
        vm.clearBrandScope()
        assertEquals(setOf(1, 2, 3, 10), vm.success().filters.locationIds, "nothing to restore once the chip is gone")
    }
}
