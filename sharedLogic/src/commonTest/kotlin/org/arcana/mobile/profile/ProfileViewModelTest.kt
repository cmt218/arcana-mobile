@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package org.arcana.mobile.profile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.*
import org.arcana.mobile.data.*
import org.arcana.mobile.favorites.FavoritesRepository
import org.arcana.mobile.networking.ApiHttpError
import org.arcana.mobile.networking.ErrorType
import org.arcana.mobile.networking.FavoritesApi
import org.arcana.mobile.networking.MembershipApi
import kotlin.test.*

class ProfileViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun teardown() { Dispatchers.resetMain() }

    private val meDto = MembershipMeDto(
        member = MemberDto(1, "0002", "cole@arcana.fit", "Cole Tomlinson", "CT", "2026-05-01", 3, 2),
        membership = MembershipBriefDto(1, "active", TierDto("all-out-30", "All Out", 30)),
        currentPeriod = CurrentPeriodDto(1, 30, 7, 23, canBrowse = true, canBook = true),
    )
    private class FakeApi(val me: MembershipMeDto) : MembershipApi {
        override suspend fun membershipMe() = me
    }

    private class FakeFavoritesApi : FavoritesApi {
        override suspend fun fetchStudios(): List<StudioDto> = emptyList()
        override suspend fun fetchFavorites(): FavoritesDto = FavoritesDto()
        override suspend fun updateFavorites(studioSlugs: List<String>, locationIds: List<Int>): FavoritesDto =
            FavoritesDto()
    }

    private class FailingApi(private val error: Throwable) : MembershipApi {
        override suspend fun membershipMe(): MembershipMeDto = throw error
    }

    /** A real HTTP-failure exception, the same type `ArcanaApiClient.membershipMe()`
     *  actually throws in production for a non-2xx (via `bodyOrThrow`). */
    private fun serverException(statusCode: Int): Throwable = ApiHttpError(statusCode)

    private fun vm(api: MembershipApi) = ProfileViewModel(api, FavoritesRepository(FakeFavoritesApi()))

    /** Fails until [failing] is flipped off, so a retry can be made to succeed. */
    private class FlakyFavoritesApi(var failing: Boolean = true) : FavoritesApi {
        var calls = 0
        override suspend fun fetchStudios(): List<StudioDto> = emptyList()
        override suspend fun fetchFavorites(): FavoritesDto {
            calls++
            if (failing) throw ApiHttpError(500)
            return FavoritesDto()
        }
        override suspend fun updateFavorites(studioSlugs: List<String>, locationIds: List<Int>): FavoritesDto =
            FavoritesDto()
    }

    /** The bug: a failed favorites fetch with nothing cached left `favorites`
     *  null, which the screen renders as "still loading" — a shimmer that never
     *  resolves. It has to be distinguishable from not-loaded-yet. */
    @Test fun `favorites failure with no cache is an error rather than endless loading`() = runTest {
        val favApi = FlakyFavoritesApi()
        val vm = ProfileViewModel(FakeApi(meDto), FavoritesRepository(favApi))
        vm.load()
        advanceUntilIdle()

        assertNull(vm.favorites.value, "nothing cached to show")
        assertEquals(ErrorType.SERVER, vm.favoritesError.value)
        assertTrue(vm.uiState.value is ProfileUiState.Success, "the profile itself must still load")
    }

    @Test fun `retrying favorites clears the error and shows the section`() = runTest {
        val favApi = FlakyFavoritesApi()
        val vm = ProfileViewModel(FakeApi(meDto), FavoritesRepository(favApi))
        vm.load()
        advanceUntilIdle()
        assertEquals(ErrorType.SERVER, vm.favoritesError.value)

        favApi.failing = false
        vm.retryFavorites()
        advanceUntilIdle()

        assertNull(vm.favoritesError.value)
        assertEquals(FavoritesDto(), vm.favorites.value)
    }

    /** Stale favorites beat no favorites: with a cached value the section keeps
     *  rendering it, which is why the repository swallows failures at all. */
    @Test fun `a later failure with cached favorites shows no error`() = runTest {
        val favApi = FlakyFavoritesApi(failing = false)
        val vm = ProfileViewModel(FakeApi(meDto), FavoritesRepository(favApi))
        vm.load()
        advanceUntilIdle()
        assertNull(vm.favoritesError.value)

        favApi.failing = true
        vm.refresh()
        advanceUntilIdle()

        assertNull(vm.favoritesError.value, "cached favorites are still on screen")
        assertEquals(FavoritesDto(), vm.favorites.value)
    }

    @Test fun `a favorites failure never blocks the profile`() = runTest {
        val vm = ProfileViewModel(FakeApi(meDto), FavoritesRepository(FlakyFavoritesApi()))
        vm.load()
        advanceUntilIdle()
        assertTrue(vm.uiState.value is ProfileUiState.Success)
    }

    @Test fun `loads profile fields`() = runTest {
        val vm = vm(FakeApi(meDto))
        vm.load()
        val s = vm.uiState.value
        assertTrue(s is ProfileUiState.Success)
        s as ProfileUiState.Success
        assertEquals("Cole Tomlinson", s.fullName)
        assertEquals("CT", s.initials)
        assertEquals("0002", s.memberNumber)
        assertEquals(3, s.lifetimeSessions)
        assertEquals(2, s.weekStreak)
        assertEquals("All Out", s.tierName)
        assertEquals(23, s.creditsRemaining)
    }

    /** The session warm-load and the Profile screen both call load(), so two can
     *  overlap. Whichever STARTED last must win, not whichever finishes last. */
    private class OutOfOrderApi(private val stale: MembershipMeDto, private val fresh: MembershipMeDto) :
        MembershipApi {
        var calls = 0
        override suspend fun membershipMe(): MembershipMeDto {
            calls++
            // First call is the slow one, so without single-flight it lands last.
            return if (calls == 1) { delay(100); stale } else { delay(10); fresh }
        }
    }

    @Test fun `a superseded load cannot land after a newer one`() = runTest {
        val stale = meDto.copy(currentPeriod = meDto.currentPeriod!!.copy(creditsRemaining = 1))
        val fresh = meDto.copy(currentPeriod = meDto.currentPeriod!!.copy(creditsRemaining = 99))
        val vm = vm(OutOfOrderApi(stale, fresh))

        vm.load()
        vm.load()
        advanceUntilIdle()

        val s = vm.uiState.value
        assertTrue(s is ProfileUiState.Success)
        assertEquals(99, (s as ProfileUiState.Success).creditsRemaining)
    }

    @Test fun `starts in Loading state`() = runTest {
        // We need a suspending fake to capture the loading state
        val vm = vm(FakeApi(meDto))
        assertTrue(vm.uiState.value is ProfileUiState.Loading)
    }

    @Test fun `error maps to Error state`() = runTest {
        val errorApi = object : MembershipApi {
            override suspend fun membershipMe(): MembershipMeDto =
                throw RuntimeException("network failure")
        }
        val vm = vm(errorApi)
        vm.load()
        val s = vm.uiState.value
        assertTrue(s is ProfileUiState.Error)
    }

    @Test
    fun `a network failure classifies as CONNECTION`() = runTest {
        val vm = vm(FailingApi(Exception("network failure")))
        vm.load()
        assertEquals(ProfileUiState.Error(ErrorType.CONNECTION), vm.uiState.value)
    }

    @Test
    fun `a 5xx classifies as SERVER`() = runTest {
        val vm = vm(FailingApi(serverException(502)))
        vm.load()
        assertEquals(ProfileUiState.Error(ErrorType.SERVER), vm.uiState.value)
    }

    @Test fun `null currentPeriod gives null credits`() = runTest {
        val dto = meDto.copy(currentPeriod = null)
        val vm = vm(FakeApi(dto))
        vm.load()
        val s = vm.uiState.value as ProfileUiState.Success
        assertNull(s.creditsRemaining)
        assertNull(s.creditsGranted)
    }

    @Test fun `null displayName falls back to email`() = runTest {
        val dto = meDto.copy(member = meDto.member.copy(displayName = null))
        val vm = vm(FakeApi(dto))
        vm.load()
        val s = vm.uiState.value as ProfileUiState.Success
        assertEquals("cole@arcana.fit", s.fullName)
    }

    @Test fun `refresh keeps existing content when the re-fetch fails`() = runTest {
        var failNext = false
        val api = object : MembershipApi {
            override suspend fun membershipMe(): MembershipMeDto {
                if (failNext) throw RuntimeException("network failure")
                return meDto
            }
        }
        val vm = vm(api)
        vm.load()
        assertTrue(vm.uiState.value is ProfileUiState.Success)

        failNext = true
        vm.refresh()
        // Still showing the previously-loaded content, not a full-screen error.
        assertTrue(vm.uiState.value is ProfileUiState.Success)
        assertFalse(vm.isRefreshing.value)
    }
}
