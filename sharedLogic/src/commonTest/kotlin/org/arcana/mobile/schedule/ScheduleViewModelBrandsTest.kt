@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.arcana.mobile.schedule

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.arcana.mobile.data.FavoriteBrandDto
import org.arcana.mobile.data.FavoritesDto
import org.arcana.mobile.data.OverviewBrandDto
import org.arcana.mobile.data.OverviewBrandLocationDto
import org.arcana.mobile.data.ScheduleOverviewDto
import org.arcana.mobile.favorites.FavoritesRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The Book tab's catalog is one entry per brand once the overview sends brands. */
class ScheduleViewModelBrandsTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun teardown() { Dispatchers.resetMain() }

    /** ID-shaped brand (three site rows, one location each) beside a one-row brand. */
    private fun brandOverview() = ScheduleOverviewDto(
        studios = listOf(
            overviewStudio("id-hot-yoga-fidi", "ID Hot Yoga FiDi", locationIds = listOf(1)),
            overviewStudio("id-hot-yoga-harlem", "ID Hot Yoga Harlem", locationIds = listOf(2)),
            overviewStudio("id-hot-yoga-nomad", "ID Hot Yoga NoMad", locationIds = listOf(3)),
            overviewStudio("yo-bk", "YO BK", locationIds = listOf(10, 11)),
        ),
        brands = listOf(
            OverviewBrandDto(
                id = 900, slug = "id-hot-yoga", name = "ID Hot Yoga",
                locations = listOf(
                    OverviewBrandLocationDto(1, "ID Hot Yoga FiDi", studioId = 101),
                    OverviewBrandLocationDto(2, "ID Hot Yoga Harlem", studioId = 102),
                    OverviewBrandLocationDto(3, "ID Hot Yoga NoMad", studioId = 103),
                ),
            ),
            OverviewBrandDto(
                id = 901, slug = "yo-bk", name = "YO BK",
                locations = listOf(
                    OverviewBrandLocationDto(10, "YO BK Williamsburg", studioId = 110),
                    OverviewBrandLocationDto(11, "YO BK Bushwick", studioId = 110),
                ),
            ),
        ),
    )

    private fun vm(favorites: FavoritesDto = FavoritesDto(), api: FakeScheduleApi = FakeScheduleApi()): Pair<ScheduleViewModel, FakeScheduleApi> {
        api.overviewResult = { brandOverview() }
        val vm = ScheduleViewModel(api, FavoritesRepository(FakeFavoritesApi(favoritesResult = favorites)), FakeBookingApi())
        return vm to api
    }

    @Test fun `the filter catalog lists each brand once with every site's locations`() = runTest {
        val (vm, _) = vm()
        val studios = vm.success().filterStudios
        assertEquals(listOf("ID Hot Yoga", "YO BK"), studios.map { it.name })
        assertEquals(listOf(1, 2, 3), studios[0].locations.map { it.id })
        assertEquals("id-hot-yoga", studios[0].slug)
    }

    @Test fun `picking a brand fetches every location behind it`() = runTest {
        val (vm, api) = vm()
        vm.toggleStudioWhole("id-hot-yoga")
        settleFilters()
        assertEquals(listOf(1, 2, 3), api.pageCalls.last().locationIds)
    }

    @Test fun `rows learn their brand name from the overview`() = runTest {
        val (vm, _) = vm()
        val names = vm.success().brandNames
        assertEquals("ID Hot Yoga", names[2])
        assertEquals("YO BK", names[11])
    }

    @Test fun `the favorites panel reads brand entries and marks partial coverage`() = runTest {
        val favorites = FavoritesDto(
            brands = listOf(
                FavoriteBrandDto(900, "id-hot-yoga", "ID Hot Yoga", locationIds = listOf(1, 2, 3)),
                FavoriteBrandDto(901, "yo-bk", "YO BK", locationIds = listOf(10)),
            ),
        )
        val (vm, _) = vm(favorites)
        val entries = vm.success().favoriteEntries
        assertEquals(listOf("ID Hot Yoga" to "All locations", "YO BK" to "1 of 2 locations"), entries.map { it.name to it.detail })
    }

    @Test fun `without a brands block the studios block still drives the catalog`() = runTest {
        val api = FakeScheduleApi()
        api.overviewResult = { brandOverview().copy(brands = emptyList()) }
        val vm = ScheduleViewModel(api, FavoritesRepository(FakeFavoritesApi()), FakeBookingApi())
        assertEquals(4, vm.success().filterStudios.size)
        assertEquals(emptyMap(), vm.success().brandNames)
    }
}
