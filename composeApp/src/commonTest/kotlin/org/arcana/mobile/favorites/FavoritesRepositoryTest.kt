package org.arcana.mobile.favorites

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.arcana.mobile.data.FavoriteLocationDto
import org.arcana.mobile.data.FavoriteStudioDto
import org.arcana.mobile.data.FavoritesDto
import org.arcana.mobile.data.StudioDto
import org.arcana.mobile.networking.FavoritesApi

private class FakeFavoritesApi(
    var favorites: FavoritesDto = FavoritesDto(),
    var shouldThrow: Boolean = false,
) : FavoritesApi {
    val updateCalls = mutableListOf<Pair<List<String>, List<Int>>>()
    override suspend fun fetchStudios(): List<StudioDto> = emptyList()
    override suspend fun fetchFavorites(): FavoritesDto {
        if (shouldThrow) throw RuntimeException("network down")
        return favorites
    }
    override suspend fun updateFavorites(studioSlugs: List<String>, locationIds: List<Int>): FavoritesDto {
        updateCalls.add(studioSlugs to locationIds)
        favorites = FavoritesDto(
            studios = studioSlugs.map { FavoriteStudioDto(id = it.hashCode(), slug = it, name = it, locationIds = emptyList()) },
            locations = locationIds.map { FavoriteLocationDto(id = it, name = "loc$it", studioSlug = "s", studioName = "S") },
        )
        return favorites
    }
}

class FavoritesRepositoryTest {

    @Test
    fun expandedLocationIdsUnionsBothGrainsAndDedupes() {
        val favs = FavoritesDto(
            studios = listOf(
                FavoriteStudioDto(id = 1, slug = "solidcore", name = "SolidCore", locationIds = listOf(41, 42)),
                FavoriteStudioDto(id = 2, slug = "empty-studio", name = "Empty", locationIds = emptyList()),
            ),
            locations = listOf(
                FavoriteLocationDto(id = 42, name = "dup", studioSlug = "solidcore", studioName = "SolidCore"),
                FavoriteLocationDto(id = 7, name = "YO BK Williamsburg", studioSlug = "yo-bk", studioName = "YO BK"),
            ),
        )
        assertEquals(listOf(41, 42, 7), favs.expandedLocationIds())
    }

    @Test
    fun studioFavoriteWithNoActiveLocationsContributesNothing() {
        // Guard: must NOT become an empty location_id= param upstream.
        val favs = FavoritesDto(
            studios = listOf(FavoriteStudioDto(id = 1, slug = "dormant", name = "Dormant", locationIds = emptyList())),
        )
        assertTrue(favs.expandedLocationIds().isEmpty())
        assertTrue(!favs.isEmpty()) // still counts as "has favorites" for UI mode
    }

    @Test
    fun refreshPopulatesStateFlow() = runTest {
        val api = FakeFavoritesApi(FavoritesDto(studios = listOf(FavoriteStudioDto(1, "a", "A", locationIds = emptyList()))))
        val repo = FavoritesRepository(api)
        val result = repo.refresh()
        assertEquals(1, result?.studios?.size)
        assertEquals(1, repo.favorites.value?.studios?.size)
    }

    @Test
    fun refreshFailureKeepsPriorValue() = runTest {
        val api = FakeFavoritesApi(FavoritesDto(studios = listOf(FavoriteStudioDto(1, "a", "A", locationIds = emptyList()))))
        val repo = FavoritesRepository(api)
        repo.refresh()
        api.shouldThrow = true
        val result = repo.refresh()
        assertEquals(1, result?.studios?.size) // stale-but-present beats null
    }

    @Test
    fun saveWritesThroughAndUpdatesCache() = runTest {
        val api = FakeFavoritesApi()
        val repo = FavoritesRepository(api)
        repo.save(studioSlugs = listOf("solidcore"), locationIds = listOf(7))
        assertEquals(listOf("solidcore") to listOf(7), api.updateCalls.single())
        assertEquals(1, repo.favorites.value?.studios?.size)
    }

    @Test
    fun clearResetsCache() = runTest {
        val api = FakeFavoritesApi(FavoritesDto(studios = listOf(FavoriteStudioDto(1, "a", "A", locationIds = emptyList()))))
        val repo = FavoritesRepository(api)
        repo.refresh()
        repo.clear()
        assertNull(repo.favorites.value)
    }
}
