@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package org.arcana.mobile.profile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import org.arcana.mobile.data.*
import org.arcana.mobile.favorites.FavoritesRepository
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

    private fun vm(api: MembershipApi) = ProfileViewModel(api, FavoritesRepository(FakeFavoritesApi()))

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
        // No HTTP response was ever received, so this is the member's
        // connection, not our server.
        assertEquals(ErrorType.CONNECTION, (s as ProfileUiState.Error).type)
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
