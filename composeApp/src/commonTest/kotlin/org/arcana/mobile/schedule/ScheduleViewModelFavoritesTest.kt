@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.arcana.mobile.schedule

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.arcana.mobile.data.FavoriteLocationDto
import org.arcana.mobile.data.FavoriteStudioDto
import org.arcana.mobile.data.FavoritesDto
import org.arcana.mobile.data.ScheduleSessionDto
import org.arcana.mobile.data.StudioDto
import org.arcana.mobile.favorites.FavoritesRepository
import org.arcana.mobile.networking.FavoritesApi
import org.arcana.mobile.networking.ScheduleApi
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScheduleViewModelFavoritesTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun teardown() { Dispatchers.resetMain() }

    private class FakeScheduleApi : ScheduleApi {
        data class Call(
            val from: LocalDate,
            val to: LocalDate,
            val studioSlugs: List<String>?,
            val locationIds: List<Int>?,
            val availableOnly: Boolean,
        )
        val calls = mutableListOf<Call>()
        override suspend fun fetchSchedule(
            from: LocalDate,
            to: LocalDate,
            studioSlugs: List<String>?,
            locationIds: List<Int>?,
            modality: String?,
            availableOnly: Boolean,
        ): List<ScheduleSessionDto> {
            calls += Call(from, to, studioSlugs, locationIds, availableOnly)
            return emptyList()
        }
    }

    private class FakeFavoritesApi(
        var favoritesResult: FavoritesDto = FavoritesDto(),
        var studiosResult: List<StudioDto> = emptyList(),
    ) : FavoritesApi {
        override suspend fun fetchStudios(): List<StudioDto> = studiosResult
        override suspend fun fetchFavorites(): FavoritesDto = favoritesResult
        override suspend fun updateFavorites(studioSlugs: List<String>, locationIds: List<Int>): FavoritesDto =
            favoritesResult
    }

    private fun favStudio(slug: String = "barrys", locationIds: List<Int>) = FavoriteStudioDto(
        id = 1, slug = slug, name = "Barry's", locationIds = locationIds,
    )

    private fun favLocation(id: Int) = FavoriteLocationDto(
        id = id, name = "YO BK Williamsburg", studioSlug = "yo-bk", studioName = "YO BK",
    )

    private fun vm(
        scheduleApi: FakeScheduleApi,
        favoritesApi: FakeFavoritesApi,
        repository: FavoritesRepository = FavoritesRepository(favoritesApi),
    ): ScheduleViewModel = ScheduleViewModel(
        api = scheduleApi,
        favoritesRepository = repository,
        favoritesApi = favoritesApi,
    )

    private fun ScheduleViewModel.success(): ScheduleUiState.Success {
        val s = uiState.value
        assertTrue(s is ScheduleUiState.Success, "expected Success but was $s")
        return s
    }

    @Test fun `favorites present - first fetch carries expanded location ids`() = runTest {
        val scheduleApi = FakeScheduleApi()
        val favoritesApi = FakeFavoritesApi(
            favoritesResult = FavoritesDto(
                studios = listOf(favStudio(locationIds = listOf(11, 12))),
                locations = listOf(favLocation(31)),
            ),
        )
        val vm = vm(scheduleApi, favoritesApi)

        assertEquals(1, scheduleApi.calls.size)
        assertEquals(listOf(11, 12, 31), scheduleApi.calls.single().locationIds)
        val state = vm.success()
        assertTrue(state.favoritesMode)
        assertTrue(state.hasFavorites)
    }

    @Test fun `favorites empty - first fetch has no location ids and mode stays off`() = runTest {
        val scheduleApi = FakeScheduleApi()
        val favoritesApi = FakeFavoritesApi(favoritesResult = FavoritesDto())
        val vm = vm(scheduleApi, favoritesApi)

        assertEquals(1, scheduleApi.calls.size)
        assertNull(scheduleApi.calls.single().locationIds)
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

        assertEquals(1, scheduleApi.calls.size)
        assertNull(scheduleApi.calls.single().locationIds)
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
        assertEquals(1, scheduleApi.calls.size)

        vm.toggleStudio("barrys")

        assertEquals(2, scheduleApi.calls.size)
        assertNull(scheduleApi.calls[1].locationIds)
        val state = vm.success()
        assertFalse(state.favoritesMode)
        assertTrue(state.hasFavorites)
        assertEquals(setOf("barrys"), state.filters.studioSlugs)
    }

    @Test fun `enterFavoritesMode after exiting re-fetches with the favorite ids and clears filters`() = runTest {
        val scheduleApi = FakeScheduleApi()
        val favoritesApi = FakeFavoritesApi(
            favoritesResult = FavoritesDto(studios = listOf(favStudio(locationIds = listOf(11, 12)))),
        )
        val vm = vm(scheduleApi, favoritesApi)
        vm.toggleStudio("barrys")
        assertEquals(2, scheduleApi.calls.size)

        vm.enterFavoritesMode()

        assertEquals(3, scheduleApi.calls.size)
        assertEquals(listOf(11, 12), scheduleApi.calls[2].locationIds)
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
        assertEquals(1, scheduleApi.calls.size)
        assertNull(scheduleApi.calls.single().locationIds)

        // The favorites manager saves a new set while this VM sits on the
        // back stack — the repository StateFlow is the change signal.
        favoritesApi.favoritesResult =
            FavoritesDto(studios = listOf(favStudio(locationIds = listOf(11, 12))))
        repository.save(studioSlugs = listOf("barrys"), locationIds = emptyList())

        assertEquals(2, scheduleApi.calls.size)
        assertEquals(listOf(11, 12), scheduleApi.calls[1].locationIds)
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
        assertEquals(1, scheduleApi.calls.size)
        assertEquals(listOf(11), scheduleApi.calls.single().locationIds)

        favoritesApi.favoritesResult = FavoritesDto()
        repository.save(studioSlugs = emptyList(), locationIds = emptyList())

        assertEquals(2, scheduleApi.calls.size)
        assertNull(scheduleApi.calls[1].locationIds)
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
        assertEquals(1, scheduleApi.calls.size)

        vm.enterFavoritesMode()

        assertEquals(1, scheduleApi.calls.size) // no redundant reload
        assertTrue(vm.success().favoritesMode)
    }

    @Test fun `enterFavoritesMode is a no-op when the member has no favorites`() = runTest {
        val scheduleApi = FakeScheduleApi()
        val favoritesApi = FakeFavoritesApi(favoritesResult = FavoritesDto())
        val vm = vm(scheduleApi, favoritesApi)
        assertEquals(1, scheduleApi.calls.size)

        vm.enterFavoritesMode()

        assertEquals(1, scheduleApi.calls.size)
        assertFalse(vm.success().favoritesMode)
    }

    @Test fun `knownStudios comes from the studio directory when present`() = runTest {
        val scheduleApi = FakeScheduleApi()
        val favoritesApi = FakeFavoritesApi(
            studiosResult = listOf(
                StudioDto(id = 2, slug = "yo-bk", name = "YO BK", primaryColor = "#3C5D1A"),
                StudioDto(id = 1, slug = "barrys", name = "Barry's", primaryColor = "#F65713"),
            ),
        )
        val vm = vm(scheduleApi, favoritesApi)

        // The schedule response is empty, so a cache-derived list would have no
        // chips at all — the directory must drive them, sorted by name.
        val state = vm.success()
        assertEquals(
            listOf(
                StudioChipData(slug = "barrys", name = "Barry's", primaryColor = "#F65713"),
                StudioChipData(slug = "yo-bk", name = "YO BK", primaryColor = "#3C5D1A"),
            ),
            state.knownStudios,
        )
    }
}
