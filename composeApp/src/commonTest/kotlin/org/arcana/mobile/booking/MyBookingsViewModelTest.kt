@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.arcana.mobile.booking

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import org.arcana.mobile.data.*
import org.arcana.mobile.networking.BookingApi
import kotlin.test.*

class MyBookingsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun teardown() { Dispatchers.resetMain() }

    private fun booking(id: Int, status: String = "requested") = BookingDto(
        id = id, status = status, spot = null,
        session = SessionBriefDto(id, "2026-07-07T10:00:00Z", "2026-07-07T10:50:00Z", "RUN", "Barry's"),
        cancelPolicy = CancelPolicyDto(false, null),
    )

    private class FakeApi(var data: MyBookingsDto) : BookingApi {
        var cancelled: Int? = null
        override suspend fun myBookings() = data
        override suspend fun createBooking(sessionId: Int, requestedSpotId: Int?, studioVisitedBefore: Boolean?): BookingDto = throw NotImplementedError()
        override suspend fun cancelBooking(bookingId: Int): CancelBookingResponse {
            cancelled = bookingId; return CancelBookingResponse("cancelled", true, false)
        }
    }

    @Test fun `loads upcoming and past`() = runTest {
        val api = FakeApi(MyBookingsDto(upcoming = listOf(booking(1)), past = listOf(booking(2, "completed"))))
        val vm = MyBookingsViewModel(api)
        vm.load()
        val s = vm.uiState.value
        assertTrue(s is MyBookingsUiState.Success)
        assertEquals(1, (s as MyBookingsUiState.Success).upcoming.size)
        assertEquals(1, s.past.size)
    }

    @Test fun `cancel reloads both lists`() = runTest {
        val api = FakeApi(MyBookingsDto(upcoming = listOf(booking(1)), past = emptyList()))
        val vm = MyBookingsViewModel(api)
        vm.load()
        api.data = MyBookingsDto(upcoming = emptyList(), past = listOf(booking(1, "cancelled")))
        vm.cancel(1)
        assertEquals(1, api.cancelled)
        val s = vm.uiState.value as MyBookingsUiState.Success
        assertEquals(0, s.upcoming.size)
        assertEquals(1, s.past.size)
    }
}
