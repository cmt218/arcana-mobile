@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package org.arcana.mobile.profile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import org.arcana.mobile.data.*
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

    @Test fun `loads profile fields`() = runTest {
        val vm = ProfileViewModel(FakeApi(meDto))
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
        val vm = ProfileViewModel(FakeApi(meDto))
        assertTrue(vm.uiState.value is ProfileUiState.Loading)
    }

    @Test fun `error maps to Error state`() = runTest {
        val errorApi = object : MembershipApi {
            override suspend fun membershipMe(): MembershipMeDto =
                throw RuntimeException("network failure")
        }
        val vm = ProfileViewModel(errorApi)
        vm.load()
        val s = vm.uiState.value
        assertTrue(s is ProfileUiState.Error)
    }

    @Test fun `null currentPeriod gives null credits`() = runTest {
        val dto = meDto.copy(currentPeriod = null)
        val vm = ProfileViewModel(FakeApi(dto))
        vm.load()
        val s = vm.uiState.value as ProfileUiState.Success
        assertNull(s.creditsRemaining)
        assertNull(s.creditsGranted)
    }

    @Test fun `null displayName falls back to email`() = runTest {
        val dto = meDto.copy(member = meDto.member.copy(displayName = null))
        val vm = ProfileViewModel(FakeApi(dto))
        vm.load()
        val s = vm.uiState.value as ProfileUiState.Success
        assertEquals("cole@arcana.fit", s.fullName)
    }
}
