@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.arcana.mobile.studios

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.arcana.mobile.data.FavoriteLocationDto
import org.arcana.mobile.data.FavoriteStudioDto
import org.arcana.mobile.data.FavoritesDto
import org.arcana.mobile.data.StudioDto
import org.arcana.mobile.data.StudioLocationDto
import org.arcana.mobile.favorites.FavoritesRepository
import org.arcana.mobile.networking.FavoritesApi
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StudioSelectionViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun teardown() { Dispatchers.resetMain() }

    private val solidcore = StudioDto(
        id = 1, slug = "solidcore", name = "SolidCore",
        locations = listOf(
            StudioLocationDto(id = 41, name = "SolidCore Williamsburg"),
            StudioLocationDto(id = 42, name = "SolidCore Cobble Hill"),
        ),
    )
    private val yobk = StudioDto(
        id = 2, slug = "yo-bk", name = "YO BK",
        locations = listOf(StudioLocationDto(id = 7, name = "YO BK Williamsburg")),
    )

    private class FakeApi(
        var studios: List<StudioDto> = emptyList(),
        var favorites: FavoritesDto = FavoritesDto(),
        var failFetch: Boolean = false,
        var failUpdate: Boolean = false,
    ) : FavoritesApi {
        val updateCalls = mutableListOf<Pair<List<String>, List<Int>>>()
        override suspend fun fetchStudios(): List<StudioDto> {
            if (failFetch) throw RuntimeException("network down")
            return studios
        }
        override suspend fun fetchFavorites(): FavoritesDto {
            if (failFetch) throw RuntimeException("network down")
            return favorites
        }
        override suspend fun updateFavorites(studioSlugs: List<String>, locationIds: List<Int>): FavoritesDto {
            if (failUpdate) throw RuntimeException("network down")
            updateCalls.add(studioSlugs to locationIds)
            return FavoritesDto(
                studios = studioSlugs.map {
                    FavoriteStudioDto(id = it.hashCode(), slug = it, name = it, locationIds = emptyList())
                },
                locations = locationIds.map {
                    FavoriteLocationDto(id = it, name = "loc$it", studioSlug = "s", studioName = "S")
                },
            )
        }
    }

    private fun vm(api: FakeApi) = StudioSelectionViewModel(api, FavoritesRepository(api))

    private fun ready(vm: StudioSelectionViewModel): StudioSelectionUiState.Ready {
        val s = vm.uiState.value
        assertTrue(s is StudioSelectionUiState.Ready, "expected Ready, was $s")
        return s
    }

    @Test
    fun `load seeds selection from existing favorites at both grains`() = runTest {
        val api = FakeApi(
            studios = listOf(solidcore, yobk),
            favorites = FavoritesDto(
                studios = listOf(
                    FavoriteStudioDto(id = 1, slug = "solidcore", name = "SolidCore", locationIds = listOf(41, 42)),
                ),
                locations = listOf(
                    FavoriteLocationDto(id = 7, name = "YO BK Williamsburg", studioSlug = "yo-bk", studioName = "YO BK"),
                ),
            ),
        )
        val s = ready(vm(api))
        assertEquals(setOf("solidcore"), s.selectedStudioSlugs)
        assertEquals(setOf(7), s.selectedLocationIds)
        assertEquals(listOf(solidcore, yobk), s.studios)
    }

    @Test
    fun `toggleStudio on clears that studio's individual location picks`() = runTest {
        val api = FakeApi(
            studios = listOf(solidcore, yobk),
            favorites = FavoritesDto(
                locations = listOf(
                    FavoriteLocationDto(id = 41, name = "SolidCore Williamsburg", studioSlug = "solidcore", studioName = "SolidCore"),
                    FavoriteLocationDto(id = 7, name = "YO BK Williamsburg", studioSlug = "yo-bk", studioName = "YO BK"),
                ),
            ),
        )
        val viewModel = vm(api)
        viewModel.toggleStudio("solidcore")
        val s = ready(viewModel)
        assertEquals(setOf("solidcore"), s.selectedStudioSlugs)
        // 41 dropped as redundant; the other Partner's pick (7) untouched.
        assertEquals(setOf(7), s.selectedLocationIds)
    }

    @Test
    fun `toggleLocation while whole studio selected narrows to just that location`() = runTest {
        val api = FakeApi(
            studios = listOf(solidcore),
            favorites = FavoritesDto(
                studios = listOf(
                    FavoriteStudioDto(id = 1, slug = "solidcore", name = "SolidCore", locationIds = listOf(41, 42)),
                ),
            ),
        )
        val viewModel = vm(api)
        viewModel.toggleLocation("solidcore", 41)
        val s = ready(viewModel)
        assertEquals(emptySet(), s.selectedStudioSlugs)
        assertEquals(setOf(41), s.selectedLocationIds)
    }

    @Test
    fun `save passes exactly the current selection to the api`() = runTest {
        val api = FakeApi(studios = listOf(solidcore, yobk))
        val viewModel = vm(api)
        viewModel.toggleStudio("yo-bk")            // whole-Partner (studio grain)
        viewModel.toggleLocation("solidcore", 41)  // one of two → partial (location grain)
        viewModel.save()
        val (slugs, locationIds) = api.updateCalls.single()
        assertEquals(setOf("yo-bk"), slugs.toSet())
        assertEquals(setOf(41), locationIds.toSet())
        val s = ready(viewModel)
        assertTrue(s.saved)
        assertFalse(s.saving)
    }

    @Test
    fun `save failure surfaces error and preserves selection`() = runTest {
        val api = FakeApi(studios = listOf(solidcore), failUpdate = true)
        val viewModel = vm(api)
        viewModel.toggleStudio("solidcore")
        viewModel.save()
        val s = ready(viewModel)
        assertNotNull(s.error)
        assertFalse(s.saving)
        assertFalse(s.saved)
        assertEquals(setOf("solidcore"), s.selectedStudioSlugs)
    }

    @Test
    fun `load failure maps to Error state`() = runTest {
        val api = FakeApi(failFetch = true)
        val viewModel = vm(api)
        assertTrue(viewModel.uiState.value is StudioSelectionUiState.Error)
    }

    @Test
    fun `selecting the only location of a single-location studio promotes to whole-Partner`() = runTest {
        val api = FakeApi(studios = listOf(yobk))
        val viewModel = vm(api)
        viewModel.toggleLocation("yo-bk", 7)
        val s = ready(viewModel)
        assertEquals(setOf("yo-bk"), s.selectedStudioSlugs)
        assertEquals(emptySet(), s.selectedLocationIds)
    }

    @Test
    fun `selecting the last location of a multi-location studio promotes to whole-Partner`() = runTest {
        val api = FakeApi(studios = listOf(solidcore))
        val viewModel = vm(api)
        viewModel.toggleLocation("solidcore", 41)
        // One of two selected — still a partial, location-grain selection.
        ready(viewModel).let {
            assertEquals(emptySet(), it.selectedStudioSlugs)
            assertEquals(setOf(41), it.selectedLocationIds)
        }
        viewModel.toggleLocation("solidcore", 42)
        // The set is complete → promote, dropping the explicit picks.
        val s = ready(viewModel)
        assertEquals(setOf("solidcore"), s.selectedStudioSlugs)
        assertEquals(emptySet(), s.selectedLocationIds)
    }
}
