@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.arcana.mobile.booking

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import org.arcana.mobile.data.*
import org.arcana.mobile.home.HomeUiState
import org.arcana.mobile.home.HomeViewModel
import org.arcana.mobile.networking.BookingApi
import org.arcana.mobile.networking.MembershipApi
import kotlin.test.*

class HomeViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun teardown() { Dispatchers.resetMain() }

    private val meDto = MembershipMeDto(
        member = MemberDto(1, "0002", "c@x.com", "Cole", "CT", "2026-05-01", 3, 2),
        membership = MembershipBriefDto(1, "active", TierDto("all-out-30", "All Out", 30)),
        currentPeriod = CurrentPeriodDto(1, 30, 7, 23, canBrowse = true, canBook = true),
    )
    private fun booking(id: Int) = BookingDto(
        id, "confirmed", spot = null,
        session = SessionBriefDto(id, "2026-07-07T10:00:00Z", "2026-07-07T10:50:00Z", "RUN", "Barry's"),
        cancelPolicy = CancelPolicyDto(false, null),
    )
    private class FakeApi(val me: MembershipMeDto, val up: List<BookingDto>) : BookingApi, MembershipApi {
        override suspend fun membershipMe() = me
        override suspend fun myBookings() = MyBookingsDto(up, emptyList())
        override suspend fun createBooking(sessionId: Int, requestedSpotId: Int?, studioVisitedBefore: Boolean?) = throw NotImplementedError()
        override suspend fun cancelBooking(bookingId: Int) = CancelBookingResponse("cancelled", true, false)
    }

    @Test fun `loads greeting credits and upcoming`() = runTest {
        val vm = HomeViewModel(FakeApi(meDto, listOf(booking(1), booking(2))))
        vm.load()
        val s = vm.uiState.value
        assertTrue(s is HomeUiState.Success)
        s as HomeUiState.Success
        assertEquals("Cole", s.displayName)
        assertEquals(23, s.creditsRemaining)
        assertEquals(2, s.upcoming.size)
        assertEquals(2, s.weekStreak)
    }

    @Test fun `refresh keeps existing content when the re-fetch fails`() = runTest {
        var failNext = false
        val api = object : BookingApi, MembershipApi {
            override suspend fun membershipMe(): MembershipMeDto {
                if (failNext) throw RuntimeException("network failure")
                return meDto
            }
            override suspend fun myBookings() = MyBookingsDto(listOf(booking(1)), emptyList())
            override suspend fun createBooking(sessionId: Int, requestedSpotId: Int?, studioVisitedBefore: Boolean?) = throw NotImplementedError()
            override suspend fun cancelBooking(bookingId: Int) = CancelBookingResponse("cancelled", true, false)
        }
        val vm = HomeViewModel(api)
        vm.load()
        assertTrue(vm.uiState.value is HomeUiState.Success)

        failNext = true
        vm.refresh()
        // Still showing the previously-loaded content, not a full-screen error.
        assertTrue(vm.uiState.value is HomeUiState.Success)
        assertFalse(vm.isRefreshing.value)
    }
}
