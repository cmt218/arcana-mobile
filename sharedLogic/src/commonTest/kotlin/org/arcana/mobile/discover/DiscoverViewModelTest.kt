@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.arcana.mobile.discover

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.arcana.mobile.data.DiscoverCategoryDto
import org.arcana.mobile.data.DiscoverDirectoryDto
import org.arcana.mobile.data.DiscoverStudioDto
import org.arcana.mobile.data.StudioPageDto
import org.arcana.mobile.data.StudioPageLocationDto
import org.arcana.mobile.networking.ApiHttpError
import org.arcana.mobile.networking.DiscoverApi
import org.arcana.mobile.networking.ErrorType
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiscoverViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun teardown() { Dispatchers.resetMain() }

    private val yoga = DiscoverCategoryDto("yoga", "Yoga")
    private val pilates = DiscoverCategoryDto("pilates", "Pilates")
    private val all = listOf(
        DiscoverStudioDto("a-yoga", "A Yoga", categories = listOf(yoga), neighborhoods = listOf("Harlem"), locationCount = 1),
        DiscoverStudioDto("b-pilates", "B Pilates", categories = listOf(pilates), neighborhoods = listOf("Tribeca", "Flatiron"), locationCount = 2),
    )

    private class FakeApi(var fail: Boolean = false) : DiscoverApi {
        val calls = mutableListOf<Pair<Set<String>, Set<String>>>()
        lateinit var studios: List<DiscoverStudioDto>
        override suspend fun fetchDirectory(categories: Set<String>, neighborhoods: Set<String>): DiscoverDirectoryDto {
            calls += categories to neighborhoods
            if (fail) throw ApiHttpError(503)
            val matching = studios.filter { s ->
                (categories.isEmpty() || s.categories.any { it.slug in categories }) &&
                    (neighborhoods.isEmpty() || s.neighborhoods.any { it in neighborhoods })
            }
            return DiscoverDirectoryDto(matching)
        }
        override suspend fun fetchStudioPage(brandSlug: String): StudioPageDto = throw NotImplementedError()
    }

    private fun api() = FakeApi().also { it.studios = all }

    @Test fun `cold load lists every studio and builds sorted catalogs`() = runTest(dispatcher) {
        val vm = DiscoverViewModel(api())
        advanceUntilIdle()
        val s = vm.uiState.value as DiscoverUiState.Success
        assertEquals(listOf("a-yoga", "b-pilates"), s.studios.map { it.slug })
        assertEquals(listOf("Pilates", "Yoga"), s.categories.map { it.name })
        assertEquals(listOf("Flatiron", "Harlem", "Tribeca"), s.neighborhoods)
    }

    @Test fun `a category pick refetches server side after the debounce and keeps the catalogs`() = runTest(dispatcher) {
        val api = api()
        val vm = DiscoverViewModel(api)
        advanceUntilIdle()
        vm.toggleCategory("yoga")
        val dimmed = vm.uiState.value as DiscoverUiState.Success
        assertTrue(dimmed.refreshingFilters)
        assertEquals(2, dimmed.studios.size, "the stale list stays until the refetch lands")
        advanceTimeBy(300)
        advanceUntilIdle()
        val s = vm.uiState.value as DiscoverUiState.Success
        assertEquals(listOf("a-yoga"), s.studios.map { it.slug })
        assertEquals(setOf("yoga"), s.selectedCategories)
        assertEquals(listOf("Pilates", "Yoga"), s.categories.map { it.name }, "catalog survives narrowing")
        assertEquals(setOf("yoga") to emptySet<String>(), api.calls.last())
    }

    @Test fun `rapid toggles collapse into one fetch`() = runTest(dispatcher) {
        val api = api()
        val vm = DiscoverViewModel(api)
        advanceUntilIdle()
        vm.toggleCategory("yoga")
        vm.toggleNeighborhood("Tribeca")
        vm.toggleCategory("yoga")
        advanceTimeBy(300)
        advanceUntilIdle()
        assertEquals(2, api.calls.size)
        assertEquals(emptySet<String>() to setOf("Tribeca"), api.calls.last())
    }

    @Test fun `clear filters restores the full list`() = runTest(dispatcher) {
        val vm = DiscoverViewModel(api())
        advanceUntilIdle()
        vm.toggleNeighborhood("Harlem")
        advanceTimeBy(300); advanceUntilIdle()
        assertEquals(1, (vm.uiState.value as DiscoverUiState.Success).studios.size)
        vm.clearFilters()
        advanceTimeBy(300); advanceUntilIdle()
        assertEquals(2, (vm.uiState.value as DiscoverUiState.Success).studios.size)
    }

    // ── Map ──────────────────────────────────────────────────────────────────

    private val located = listOf(
        DiscoverStudioDto(
            "a-yoga", "A Yoga", categories = listOf(yoga), neighborhoods = listOf("Harlem"), locationCount = 1,
            locations = listOf(StudioPageLocationDto(1, "Harlem", "Harlem", "1 Lenox Ave", 40.8040, -73.9480)),
        ),
        DiscoverStudioDto(
            "b-pilates", "B Pilates", categories = listOf(pilates), neighborhoods = listOf("Tribeca", "Flatiron"), locationCount = 2,
            locations = listOf(
                StudioPageLocationDto(2, "Tribeca", "Tribeca", "1 White St", 40.7190, -74.0060),
                StudioPageLocationDto(3, "Flatiron", "Flatiron", "12 W 21st St", 40.7410, -73.9920),
            ),
        ),
    )

    @Test fun `the first load opens on the default frame and a filter reframes the pins`() = runTest(dispatcher) {
        val vm = DiscoverViewModel(FakeApi().also { it.studios = located })
        advanceUntilIdle()
        val opened = vm.uiState.value as DiscoverUiState.Success
        assertEquals(listOf(1, 2, 3), opened.pins.map { it.locationId })
        assertEquals(0, opened.pinsEpoch, "the opening frame is the map's own, not a fit to the pins")
        vm.toggleNeighborhood("Tribeca")
        advanceTimeBy(300); advanceUntilIdle()
        val narrowed = vm.uiState.value as DiscoverUiState.Success
        assertEquals(listOf(2), narrowed.pins.map { it.locationId }, "Tribeca's pin, not every pin of a brand that has one there")
        assertEquals(1, narrowed.pinsEpoch)
        vm.refresh(); advanceUntilIdle()
        assertEquals(1, (vm.uiState.value as DiscoverUiState.Success).pinsEpoch, "the same pins again leave the camera alone")
    }

    @Test fun `a selected pin clears when a filter removes it or the lens changes`() = runTest(dispatcher) {
        val vm = DiscoverViewModel(FakeApi().also { it.studios = located })
        advanceUntilIdle()
        vm.setMode(DiscoverMode.Map)
        vm.selectPin(3)
        assertEquals(3, vm.selectedPinId.value)
        vm.selectPin(99)
        assertEquals(3, vm.selectedPinId.value, "an unknown pin changes nothing")
        vm.toggleNeighborhood("Tribeca")
        advanceTimeBy(300); advanceUntilIdle()
        assertEquals(null, vm.selectedPinId.value)
        vm.selectPin(2)
        vm.setMode(DiscoverMode.Studios)
        assertEquals(null, vm.selectedPinId.value)
        assertEquals(DiscoverMode.Studios, vm.mode.value)
    }

    // ── "Show on the map" from a class page ──────────────────────────────────

    @Test fun `a map request waiting for the first load opens the map on that pin`() = runTest(dispatcher) {
        val requests = DiscoverMapRequests().apply { request(3) }
        val vm = DiscoverViewModel(FakeApi().also { it.studios = located }, mapRequests = requests)
        advanceUntilIdle()
        val s = vm.uiState.value as DiscoverUiState.Success
        assertEquals(DiscoverMode.Map, vm.mode.value)
        assertEquals(3, vm.selectedPinId.value)
        assertEquals(1, s.focusEpoch)
        assertEquals(null, requests.pending.value, "consumed once taken")
        // A map not built yet opens on the pin rather than on lower Manhattan.
        assertEquals(40.7410, vm.mapCamera.latitude)
        assertEquals(-73.9920, vm.mapCamera.longitude)
    }

    @Test fun `a map request drops the filters that would hide the pin`() = runTest(dispatcher) {
        val requests = DiscoverMapRequests()
        val api = FakeApi().also { it.studios = located }
        val vm = DiscoverViewModel(api, mapRequests = requests)
        advanceUntilIdle()
        vm.toggleNeighborhood("Harlem")
        advanceTimeBy(300); advanceUntilIdle()
        assertEquals(listOf(1), (vm.uiState.value as DiscoverUiState.Success).pins.map { it.locationId })
        requests.request(2)
        advanceTimeBy(300); advanceUntilIdle()
        val s = vm.uiState.value as DiscoverUiState.Success
        assertEquals(emptySet(), s.selectedNeighborhoods)
        assertEquals(2, vm.selectedPinId.value)
        assertEquals(1, s.focusEpoch)
    }

    @Test fun `a map request for a place with no pin still opens the map and selects nothing`() = runTest(dispatcher) {
        val requests = DiscoverMapRequests()
        val vm = DiscoverViewModel(FakeApi().also { it.studios = located }, mapRequests = requests)
        advanceUntilIdle()
        requests.request(99)
        advanceUntilIdle()
        assertEquals(DiscoverMode.Map, vm.mode.value)
        assertEquals(null, vm.selectedPinId.value)
        assertEquals(0, (vm.uiState.value as DiscoverUiState.Success).focusEpoch)
    }

    @Test fun `a failed cold load classifies and retry recovers`() = runTest(dispatcher) {
        val api = api().apply { fail = true }
        val vm = DiscoverViewModel(api)
        advanceUntilIdle()
        assertEquals(DiscoverUiState.Error(ErrorType.SERVER), vm.uiState.value)
        api.fail = false
        vm.retry()
        advanceUntilIdle()
        assertTrue(vm.uiState.value is DiscoverUiState.Success)
    }

    @Test fun `a failed refresh keeps the list and raises the snackbar flag`() = runTest(dispatcher) {
        val api = api()
        val vm = DiscoverViewModel(api)
        advanceUntilIdle()
        api.fail = true
        vm.refresh()
        advanceUntilIdle()
        assertEquals(2, (vm.uiState.value as DiscoverUiState.Success).studios.size)
        assertTrue(vm.refreshFailed.value)
    }
}
