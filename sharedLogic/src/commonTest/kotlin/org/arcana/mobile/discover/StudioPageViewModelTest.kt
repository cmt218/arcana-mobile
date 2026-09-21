@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.arcana.mobile.discover

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.arcana.mobile.data.DiscoverDirectoryDto
import org.arcana.mobile.data.FavoriteLocationDto
import org.arcana.mobile.data.FavoriteStudioDto
import org.arcana.mobile.data.FavoritesDto
import org.arcana.mobile.data.StudioDto
import org.arcana.mobile.data.StudioPageDto
import org.arcana.mobile.data.StudioPageLocationDto
import org.arcana.mobile.favorites.FavoritesRepository
import org.arcana.mobile.networking.ApiHttpError
import org.arcana.mobile.networking.DiscoverApi
import org.arcana.mobile.networking.FavoritesApi
import org.arcana.mobile.schedule.ScheduleScopeRequests
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StudioPageViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun teardown() { Dispatchers.resetMain() }

    private fun page(locations: Int, favorited: List<Int> = emptyList()) = StudioPageDto(
        slug = "solidcore", name = "[solidcore]",
        locations = (1..locations).map { StudioPageLocationDto(id = it, name = "Loc $it") },
        favoriteLocationIds = favorited,
    )

    private class FakeDiscover(val page: StudioPageDto) : DiscoverApi {
        override suspend fun fetchDirectory(categories: Set<String>, neighborhoods: Set<String>) = DiscoverDirectoryDto()
        override suspend fun fetchStudioPage(brandSlug: String) = page
    }

    private class FakeFavorites(var fail: Boolean = false) : FavoritesApi {
        var saved: Pair<List<String>, List<Int>>? = null
        var current = FavoritesDto(
            studios = listOf(FavoriteStudioDto(9, "other", "Other", locationIds = listOf(50))),
            locations = listOf(FavoriteLocationDto(40, "Elsewhere", "elsewhere", "Elsewhere")),
        )
        override suspend fun fetchStudios(): List<StudioDto> = emptyList()
        override suspend fun fetchFavorites(): FavoritesDto = current
        override suspend fun updateFavorites(studioSlugs: List<String>, locationIds: List<Int>, brandSlugs: List<String>): FavoritesDto {
            if (fail) throw ApiHttpError(500)
            saved = studioSlugs to locationIds
            current = current.copy(locations = current.locations + locationIds.filter { id -> current.locations.none { it.id == id } }
                .map { FavoriteLocationDto(it, "Loc $it", "solidcore", "[solidcore]") })
            return current
        }
    }

    private fun vm(
        page: StudioPageDto,
        favorites: FakeFavorites = FakeFavorites(),
        requests: ScheduleScopeRequests = ScheduleScopeRequests(),
        fromLocationId: Int? = null,
    ) = StudioPageViewModel("solidcore", "directory", fromLocationId, FakeDiscover(page), FavoritesRepository(favorites), requests)

    @Test fun `one location favorites on the tap and merges into the existing set`() = runTest {
        val favorites = FakeFavorites()
        val vm = vm(page(1), favorites)
        assertEquals(FavoriteTapResult.Added, vm.onFavoriteTapped())
        assertEquals(listOf("other") to listOf(40, 1), favorites.saved)
        val s = vm.uiState.value as StudioPageUiState.Success
        assertTrue(s.isFavorite)
        assertEquals(setOf(1), s.favoriteLocationIds)
    }

    @Test fun `several locations hand off to the picker sheet`() = runTest {
        val favorites = FakeFavorites()
        val vm = vm(page(3), favorites)
        assertEquals(FavoriteTapResult.Sheet, vm.onFavoriteTapped())
        assertNull(favorites.saved)
        vm.saveFavorites(setOf(2, 3))
        assertEquals(listOf("other") to listOf(40, 2, 3), favorites.saved)
        assertEquals(setOf(2, 3), (vm.uiState.value as StudioPageUiState.Success).favoriteLocationIds)
    }

    @Test fun `an already favorited brand is a no-op`() = runTest {
        val vm = vm(page(2, favorited = listOf(1)))
        assertEquals(FavoriteTapResult.NoOp, vm.onFavoriteTapped())
    }

    @Test fun `a failed save keeps the previous set and reports inline`() = runTest {
        val favorites = FakeFavorites(fail = true)
        val vm = vm(page(1), favorites)
        vm.onFavoriteTapped()
        val s = vm.uiState.value as StudioPageUiState.Success
        assertEquals(emptySet(), s.favoriteLocationIds)
        assertEquals(org.arcana.mobile.networking.ErrorType.SERVER, s.favoritesError)
    }

    @Test fun `see schedule posts the brand's location ids for the Book tab`() = runTest {
        val requests = ScheduleScopeRequests()
        val vm = vm(page(3), requests = requests)
        vm.requestSchedule()
        val r = requests.pending.value!!
        assertEquals("solidcore", r.brandSlug)
        assertEquals(listOf(1, 2, 3), r.locationIds)
        assertEquals("[solidcore]", r.label)
        assertEquals(r, requests.consume())
        assertNull(requests.pending.value)
    }

    @Test fun `reached from a map pin the page leads with that location and see schedule opens on it`() = runTest {
        val requests = ScheduleScopeRequests()
        val vm = vm(page(3), requests = requests, fromLocationId = 2)
        assertEquals(2, (vm.uiState.value as StudioPageUiState.Success).fromLocation?.id)
        vm.requestSchedule()
        val r = requests.pending.value!!
        assertEquals(listOf(2), r.locationIds)
        assertEquals("[solidcore] · Loc 2", r.label)
    }

    @Test fun `a one-location brand and an unknown location have nothing to narrow`() = runTest {
        val requests = ScheduleScopeRequests()
        val single = vm(page(1), requests = requests, fromLocationId = 1)
        assertNull((single.uiState.value as StudioPageUiState.Success).fromLocation)
        single.requestSchedule()
        assertEquals(listOf(1), requests.consume()!!.locationIds)
        val unknown = vm(page(3), requests = requests, fromLocationId = 99)
        assertNull((unknown.uiState.value as StudioPageUiState.Success).fromLocation)
        unknown.requestSchedule()
        assertEquals(listOf(1, 2, 3), requests.consume()!!.locationIds)
    }
}
