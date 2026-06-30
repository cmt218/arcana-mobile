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

/** Modalities filter mode on the paged pipeline: the genre filter is standalone
 *  (mutually exclusive with the studio picks) and narrows by repeated `modality`
 *  param across all studios. Mutations are debounced ([settleFilters]). */
class ScheduleViewModelModalitiesTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun teardown() { Dispatchers.resetMain() }

    private fun vm(
        scheduleApi: FakeScheduleApi,
        favoritesApi: FakeFavoritesApi = FakeFavoritesApi(),
        bookingApi: FakeBookingApi = FakeBookingApi(),
        telemetry: org.arcana.mobile.analytics.Telemetry = org.arcana.mobile.analytics.Telemetry.Noop,
    ): ScheduleViewModel = ScheduleViewModel(
        api = scheduleApi,
        favoritesRepository = FavoritesRepository(favoritesApi),
        bookingApi = bookingApi,
        telemetry = telemetry,
    )

    /** Overview carrying a category catalog (slug to name) + one studio. */
    private fun modalitiesOverview() = overviewWithCategories(
        categories = listOf("cycle" to "Cycle", "reformer" to "Reformer"),
        overviewStudio("barrys", "Barry's", locationIds = listOf(1, 2)),
    )

    @Test fun `available categories are surfaced from the overview`() = runTest {
        val scheduleApi = FakeScheduleApi()
        scheduleApi.overviewResult = { modalitiesOverview() }
        val vm = vm(scheduleApi)

        val options = vm.success().availableModalities
        assertEquals(listOf("cycle", "reformer"), options.map { it.slug })
        assertEquals(listOf("Cycle", "Reformer"), options.map { it.label })
    }

    @Test fun `entering modalities mode with no picks sends no category param`() = runTest {
        val scheduleApi = FakeScheduleApi()
        scheduleApi.overviewResult = { modalitiesOverview() }
        val vm = vm(scheduleApi)

        vm.useModalities()
        settleFilters()

        val state = vm.success()
        assertEquals(FilterMode.Modalities, state.filterMode)
        assertEquals("Modalities", state.filterSummary)
        // No picks yet → null category filter (shows everything).
        assertNull(scheduleApi.overviewCalls.last().categorySlugs)
        assertNull(scheduleApi.pageCalls.last().categorySlugs)
    }

    @Test fun `toggling categories sends their slugs as the category whitelist`() = runTest {
        val scheduleApi = FakeScheduleApi()
        scheduleApi.overviewResult = { modalitiesOverview() }
        val vm = vm(scheduleApi)

        vm.useModalities()
        vm.toggleModality("reformer")
        vm.toggleModality("cycle")
        settleFilters()

        val state = vm.success()
        assertEquals(FilterMode.Modalities, state.filterMode)
        assertEquals(setOf("reformer", "cycle"), state.selectedModalitySlugs)
        assertEquals("2 modalities", state.filterSummary)
        // Both slugs sent; no location/studio narrowing in this mode.
        assertEquals(listOf("reformer", "cycle"), scheduleApi.pageCalls.last().categorySlugs)
        assertEquals(listOf("reformer", "cycle"), scheduleApi.overviewCalls.last().categorySlugs)
        assertNull(scheduleApi.pageCalls.last().locationIds)
        assertNull(scheduleApi.pageCalls.last().studioSlugs)
    }

    @Test fun `deselecting a category removes it from the whitelist`() = runTest {
        val scheduleApi = FakeScheduleApi()
        scheduleApi.overviewResult = { modalitiesOverview() }
        val vm = vm(scheduleApi)

        vm.useModalities()
        vm.toggleModality("reformer")
        vm.toggleModality("reformer") // off again
        settleFilters()

        assertTrue(vm.success().selectedModalitySlugs.isEmpty())
        assertNull(scheduleApi.pageCalls.last().categorySlugs)
    }

    @Test fun `switching to a studio filter clears the category selection`() = runTest {
        val scheduleApi = FakeScheduleApi()
        scheduleApi.overviewResult = { modalitiesOverview() }
        val vm = vm(scheduleApi)

        vm.useModalities()
        vm.toggleModality("reformer")
        settleFilters()
        assertEquals(FilterMode.Modalities, vm.success().filterMode)

        vm.toggleStudioWhole("barrys") // jump to a studio filter
        settleFilters()

        val state = vm.success()
        assertEquals(FilterMode.Custom, state.filterMode)
        assertTrue(state.selectedModalitySlugs.isEmpty())
        // Studio narrowing applies; category filter is gone.
        assertNull(scheduleApi.pageCalls.last().categorySlugs)
        assertEquals(listOf(1, 2), scheduleApi.pageCalls.last().locationIds)
    }

    @Test fun `switching to All Studios clears categories`() = runTest {
        val scheduleApi = FakeScheduleApi()
        scheduleApi.overviewResult = { modalitiesOverview() }
        val vm = vm(scheduleApi)

        vm.useModalities()
        vm.toggleModality("cycle")
        settleFilters()

        vm.showAllStudios()
        settleFilters()

        val state = vm.success()
        assertEquals(FilterMode.AllStudios, state.filterMode)
        assertTrue(state.selectedModalitySlugs.isEmpty())
        assertNull(scheduleApi.pageCalls.last().categorySlugs)
    }

    @Test fun `category toggle emits filter-changed telemetry with mode and count`() = runTest {
        val (telemetry, analytics, _) = fakeTelemetry()
        val scheduleApi = FakeScheduleApi()
        scheduleApi.overviewResult = { modalitiesOverview() }
        val vm = vm(scheduleApi, telemetry = telemetry)

        vm.useModalities()
        vm.toggleModality("reformer")
        settleFilters()

        val event = analytics.all("schedule_filter_changed").last()
        assertEquals("modalities", event.properties["mode"])
        assertEquals(1, event.properties["modality_count"])
    }
}
