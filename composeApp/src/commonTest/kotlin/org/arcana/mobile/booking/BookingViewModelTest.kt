@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.arcana.mobile.booking

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.arcana.mobile.data.*
import org.arcana.mobile.networking.BookingApi
import org.arcana.mobile.networking.BookingError
import org.arcana.mobile.networking.MembershipApi
import kotlin.test.*

class BookingViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun teardown() { Dispatchers.resetMain() }

    private fun me(remaining: Int = 10, canBook: Boolean = true) = MembershipMeDto(
        member = MemberDto(1, "0002", "c@x.com", "Cole", "CT", "2026-05-01", 3, 2),
        membership = MembershipBriefDto(1, "active", TierDto("all-out-30", "All Out", 30)),
        currentPeriod = CurrentPeriodDto(1, 30, 30 - remaining, remaining, canBrowse = true, canBook = canBook),
    )
    private fun booking(id: Int = 17, sessionId: Int = 482) = BookingDto(
        id = id, status = "requested", spot = null,
        session = SessionBriefDto(sessionId, "2026-07-07T10:00:00Z", "2026-07-07T10:50:00Z", "RUN", "Barry's"),
        cancelPolicy = CancelPolicyDto(false, null),
    )

    private class FakeApi(
        val meResult: MembershipMeDto,
        val upcoming: List<BookingDto> = emptyList(),
        val createResult: () -> BookingDto = { throw IllegalStateException() },
    ) : BookingApi, MembershipApi {
        var created: Pair<Int, Int?>? = null
        override suspend fun membershipMe() = meResult
        override suspend fun myBookings() = MyBookingsDto(upcoming, emptyList())
        override suspend fun createBooking(sessionId: Int, requestedSpotId: Int?): BookingDto {
            created = sessionId to requestedSpotId; return createResult()
        }
        override suspend fun cancelBooking(bookingId: Int) = CancelBookingResponse("cancelled", true, false)
    }

    @Test fun `loads eligibility - bookable`() = runTest {
        val api = FakeApi(me(remaining = 10))
        val vm = BookingViewModel(sessionId = 482, spotsAvailable = 5, requiresSpot = false, bookingApi = api, membershipApi = api)
        vm.load()
        assertEquals(BookCta.Bookable, vm.ctaState.value)
        assertEquals(10, vm.creditsRemaining.value)
    }

    @Test fun `already-booked detected from upcoming`() = runTest {
        val api = FakeApi(me(), upcoming = listOf(booking(sessionId = 482)))
        val vm = BookingViewModel(482, 5, false, api, api)
        vm.load()
        assertEquals(BookCta.AlreadyBooked, vm.ctaState.value)
        assertEquals("requested", vm.bookedStatus.value)
    }

    @Test fun `booked status reflects a confirmed booking`() = runTest {
        val confirmed = booking(sessionId = 482).copy(status = "confirmed")
        val api = FakeApi(me(), upcoming = listOf(confirmed))
        val vm = BookingViewModel(482, 5, false, api, api)
        vm.load()
        assertEquals("confirmed", vm.bookedStatus.value)
    }

    @Test fun `submit success transitions to Booked`() = runTest {
        val api = FakeApi(me(), createResult = { booking() })
        val vm = BookingViewModel(482, 5, false, api, api)
        vm.load()
        vm.confirmBooking()
        assertEquals(482 to null, api.created)
        assertTrue(vm.submitState.value is BookingSubmit.Booked)
    }

    @Test fun `submit maps BookingError code to message`() = runTest {
        val api = FakeApi(me(), createResult = { throw BookingError("session_full") })
        val vm = BookingViewModel(482, 5, false, api, api)
        vm.load()
        vm.confirmBooking()
        val s = vm.submitState.value
        assertTrue(s is BookingSubmit.Failed)
        assertEquals("session_full", (s as BookingSubmit.Failed).code)
    }

    @Test fun `requiresSpot blocks confirm until spot chosen`() = runTest {
        val api = FakeApi(me(), createResult = { booking() })
        val vm = BookingViewModel(482, 5, requiresSpot = true, api, api)
        vm.load()
        vm.confirmBooking()
        assertTrue(vm.submitState.value is BookingSubmit.Idle)
        assertNull(api.created)
        vm.selectSpot(SpotDto(29, "DF-21"))
        vm.confirmBooking()
        assertEquals(482 to 29, api.created)
    }
}
