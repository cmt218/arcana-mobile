@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.arcana.mobile.schedule

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.arcana.mobile.analytics.fakeTelemetry
import org.arcana.mobile.data.FavoritesDto
import org.arcana.mobile.favorites.FavoritesRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The ADDITIVE filter model: the Time + Modality overlays AND on top of the
 *  studio/location scope (Favorites vs All Studios) rather than replacing it.
 *  Mutations are debounced ([settleFilters]). */
class ScheduleViewModelAdditiveFilterTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun teardown() { Dispatchers.resetMain() }

    private fun vm(
        scheduleApi: FakeScheduleApi,
        favoritesApi: FakeFavoritesApi = FakeFavoritesApi(),
        telemetry: org.arcana.mobile.analytics.Telemetry = org.arcana.mobile.analytics.Telemetry.Noop,
    ): ScheduleViewModel = ScheduleViewModel(
        api = scheduleApi,
        favoritesRepository = FavoritesRepository(favoritesApi),
        bookingApi = FakeBookingApi(),
        telemetry = telemetry,
    )

    private fun categoriesOverview() = overviewWithCategories(
        categories = listOf("cycle" to "Cycle", "reformer" to "Reformer"),
        overviewStudio("barrys", "Barry's", locationIds = listOf(1, 2)),
    )

    private fun favApi() = FakeFavoritesApi(
        favoritesResult = FavoritesDto(studios = listOf(favStudio(locationIds = listOf(11, 12)))),
    )

    @Test fun `available categories are surfaced from the overview`() = runTest {
        val api = FakeScheduleApi().apply { overviewResult = { categoriesOverview() } }
        val options = vm(api).success().availableModalities
        assertEquals(listOf("cycle", "reformer"), options.map { it.slug })
        assertEquals(listOf("Cycle", "Reformer"), options.map { it.label })
    }

    @Test fun `a modality is an overlay - it does not change the scope`() = runTest {
        val api = FakeScheduleApi().apply { overviewResult = { categoriesOverview() } }
        val vm = vm(api)  // no favorites -> AllStudios scope
        vm.toggleModality("reformer")
        vm.toggleModality("cycle")
        settleFilters()

        val state = vm.success()
        assertEquals(ScopeMode.AllStudios, state.scope)  // scope untouched
        assertEquals(setOf("reformer", "cycle"), state.selectedModalitySlugs)
        assertEquals(listOf("reformer", "cycle"), api.pageCalls.last().categorySlugs)
        assertNull(api.pageCalls.last().locationIds)  // no scope narrowing
    }

    @Test fun `modality overlay AND-s with the Favorites scope - both are sent`() = runTest {
        val api = FakeScheduleApi().apply { overviewResult = { categoriesOverview() } }
        val vm = vm(api, favApi())  // has favorites -> Favorites scope
        assertEquals(ScopeMode.Favorites, vm.success().scope)

        vm.toggleModality("cycle")
        settleFilters()

        val call = api.pageCalls.last()
        assertEquals(ScopeMode.Favorites, vm.success().scope)      // still favorites
        assertEquals(listOf(11, 12), call.locationIds)             // favorites scope
        assertEquals(listOf("cycle"), call.categorySlugs)          // AND the modality
    }

    @Test fun `switching scope keeps the modality overlay`() = runTest {
        val api = FakeScheduleApi().apply { overviewResult = { categoriesOverview() } }
        val vm = vm(api, favApi())
        vm.toggleModality("cycle")
        settleFilters()

        vm.showAllStudios()  // toggle scope
        settleFilters()

        val state = vm.success()
        assertEquals(ScopeMode.AllStudios, state.scope)
        assertEquals(setOf("cycle"), state.selectedModalitySlugs)  // overlay preserved
        assertEquals(listOf("cycle"), api.pageCalls.last().categorySlugs)
    }

    @Test fun `removeModality clears just that chip`() = runTest {
        val api = FakeScheduleApi().apply { overviewResult = { categoriesOverview() } }
        val vm = vm(api)
        vm.toggleModality("reformer")
        vm.toggleModality("cycle")
        settleFilters()

        vm.removeModality("reformer")
        settleFilters()

        assertEquals(setOf("cycle"), vm.success().selectedModalitySlugs)
        assertEquals(listOf("cycle"), api.pageCalls.last().categorySlugs)
    }

    @Test fun `no modality picks sends no category param`() = runTest {
        val api = FakeScheduleApi().apply { overviewResult = { categoriesOverview() } }
        val vm = vm(api)
        vm.toggleModality("reformer")
        vm.toggleModality("reformer")  // off again
        settleFilters()
        assertTrue(vm.success().selectedModalitySlugs.isEmpty())
        assertNull(api.pageCalls.last().categorySlugs)
    }

    @Test fun `a preset time filter sends the NY start-time bounds`() = runTest {
        val api = FakeScheduleApi().apply { overviewResult = { categoriesOverview() } }
        val vm = vm(api)
        vm.setTimeFilter(TimePreset.Evening.toFilter())
        settleFilters()

        val call = api.pageCalls.last()
        assertEquals("17:00", call.startTimeGte)
        assertNull(call.startTimeLte)
        assertEquals("Evening", vm.success().timeFilter?.label)
    }

    @Test fun `a custom time range sends both bounds`() = runTest {
        val api = FakeScheduleApi().apply { overviewResult = { categoriesOverview() } }
        val vm = vm(api)
        vm.setTimeFilter(customTimeFilter("18:00", "21:00"))
        settleFilters()

        val call = api.pageCalls.last()
        assertEquals("18:00", call.startTimeGte)
        assertEquals("21:00", call.startTimeLte)
    }

    @Test fun `clearTimeFilter removes the time overlay`() = runTest {
        val api = FakeScheduleApi().apply { overviewResult = { categoriesOverview() } }
        val vm = vm(api)
        vm.setTimeFilter(TimePreset.Morning.toFilter())
        settleFilters()

        vm.clearTimeFilter()
        settleFilters()

        assertNull(vm.success().timeFilter)
        assertNull(api.pageCalls.last().startTimeGte)
        assertNull(api.pageCalls.last().startTimeLte)
    }

    @Test fun `all three facets AND together - favorites + modality + time`() = runTest {
        val api = FakeScheduleApi().apply { overviewResult = { categoriesOverview() } }
        val vm = vm(api, favApi())
        vm.toggleModality("cycle")
        vm.setTimeFilter(customTimeFilter("18:00", "21:00"))
        settleFilters()

        val call = api.pageCalls.last()
        assertEquals(listOf(11, 12), call.locationIds)
        assertEquals(listOf("cycle"), call.categorySlugs)
        assertEquals("18:00", call.startTimeGte)
        assertEquals("21:00", call.startTimeLte)
    }

    @Test fun `filter-changed telemetry carries the scope + modality count`() = runTest {
        val (telemetry, analytics, _) = fakeTelemetry()
        val api = FakeScheduleApi().apply { overviewResult = { categoriesOverview() } }
        val vm = vm(api, telemetry = telemetry)  // AllStudios scope
        vm.toggleModality("reformer")
        settleFilters()

        val event = analytics.all("schedule_filter_changed").last()
        assertEquals("all", event.properties["mode"])
        assertEquals(1, event.properties["modality_count"])
    }
}
