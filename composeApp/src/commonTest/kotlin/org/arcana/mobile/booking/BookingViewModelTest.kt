@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.arcana.mobile.booking

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
        var upcoming: List<BookingDto> = emptyList(),
        val createResult: () -> BookingDto = { throw IllegalStateException() },
        val cancelResult: () -> CancelBookingResponse = { CancelBookingResponse("cancelled", true, false) },
    ) : BookingApi, MembershipApi {
        var created: Pair<Int, Int?>? = null
        var cancelledId: Int? = null
        var cancelCalls: Int = 0
        var createCalls: Int = 0
        override suspend fun membershipMe() = meResult
        override suspend fun myBookings() = MyBookingsDto(upcoming, emptyList())
        override suspend fun createBooking(sessionId: Int, requestedSpotId: Int?): BookingDto {
            created = sessionId to requestedSpotId; createCalls++; return createResult()
        }
        override suspend fun cancelBooking(bookingId: Int): CancelBookingResponse {
            cancelledId = bookingId; cancelCalls++; return cancelResult()
        }
    }

    @Test fun `loads eligibility - bookable`() = runTest {
        val api = FakeApi(me(remaining = 10))
        val vm = BookingViewModel(sessionId = 482, spotsAvailable = 5, requiresSpot = false, bookingApi = api, membershipApi = api)
        vm.load()
        assertEquals(BookCta.Bookable, vm.ctaState.value)
        assertEquals(10, vm.creditsRemaining.value)
    }

    @Test fun `two-wallet member shows the credits of the wallet that pays for THIS class`() = runTest {
        val july = CurrentPeriodDto(
            1, 12, 7, 5, canBrowse = true, canBook = true,
            label = "July Beta", windowStart = "2026-07-01T04:00:00Z", windowEnd = "2026-08-01T04:00:00Z",
        )
        val august = CurrentPeriodDto(
            2, 12, 0, 12, canBrowse = true, canBook = true,
            label = "August Beta", windowStart = "2026-08-01T04:00:00Z", windowEnd = "2026-09-01T04:00:00Z",
        )
        val meBoth = MembershipMeDto(
            member = MemberDto(1, "0002", "c@x.com", "Cole", "CT", "2026-05-01", 3, 2),
            membership = MembershipBriefDto(1, "active", TierDto("std", "Standard", 12)),
            currentPeriod = july, upcomingPeriod = august,
        )
        val api = FakeApi(meBoth)
        // An August class → the August wallet (12), not July's 5.
        val vm = BookingViewModel(482, 5, false, api, api, sessionStartIso = "2026-08-04T18:00:00Z")
        vm.load()
        assertEquals(12, vm.creditsRemaining.value)
        assertEquals("July and August", vm.coveredMonths.value)
    }

    @Test fun `already-booked detected from upcoming`() = runTest {
        val api = FakeApi(me(), upcoming = listOf(booking(sessionId = 482)))
        val vm = BookingViewModel(482, 5, false, api, api)
        vm.load()
        assertEquals(BookCta.AlreadyBooked, vm.ctaState.value)
        assertEquals(17, vm.existingBooking.value?.id)
        assertEquals("requested", vm.existingBooking.value?.status)
    }

    @Test fun `booked status reflects a confirmed booking`() = runTest {
        val confirmed = booking(sessionId = 482).copy(status = "confirmed")
        val api = FakeApi(me(), upcoming = listOf(confirmed))
        val vm = BookingViewModel(482, 5, false, api, api)
        vm.load()
        assertEquals("confirmed", vm.existingBooking.value?.status)
    }

    @Test fun `submit success transitions to Booked and sets existingBooking`() = runTest {
        val created = booking(id = 99, sessionId = 482).copy(status = "requested")
        val api = FakeApi(me(), createResult = { created })
        val vm = BookingViewModel(482, 5, false, api, api)
        vm.load()
        vm.confirmBooking()
        assertEquals(482 to null, api.created)
        assertTrue(vm.submitState.value is BookingSubmit.Booked)
        assertEquals(99, vm.existingBooking.value?.id)
    }

    // ── loaded flag (Item 2) --------------------------------------------------

    @Test fun `loaded is false before load and true after`() = runTest {
        val api = FakeApi(me())
        val vm = BookingViewModel(482, 5, false, api, api)
        assertFalse(vm.loaded.value)
        vm.load()
        assertTrue(vm.loaded.value)
    }

    // ── cancel (Item 4) -------------------------------------------------------

    @Test fun `openCancelSheet no-ops without an existing booking, opens with one`() = runTest {
        val api = FakeApi(me())
        val vm = BookingViewModel(482, 5, false, api, api)
        vm.load()
        vm.openCancelSheet()
        assertFalse(vm.cancelSheetOpen.value)

        val booked = FakeApi(me(), upcoming = listOf(booking(sessionId = 482)))
        val vm2 = BookingViewModel(482, 5, false, booked, booked)
        vm2.load()
        vm2.openCancelSheet()
        assertTrue(vm2.cancelSheetOpen.value)
    }

    @Test fun `confirmCancel cancels, clears booking, closes sheet, reloads`() = runTest {
        val api = FakeApi(me(), upcoming = listOf(booking(id = 17, sessionId = 482)))
        val vm = BookingViewModel(482, 5, false, api, api)
        vm.load()
        vm.openCancelSheet()
        assertTrue(vm.cancelSheetOpen.value)
        // After the cancel call, the reload's myBookings must no longer carry it.
        api.upcoming = emptyList()
        vm.confirmCancel()
        assertEquals(17, api.cancelledId)
        assertEquals(1, api.cancelCalls)
        assertFalse(vm.cancelSheetOpen.value)
        assertNull(vm.existingBooking.value)
        assertTrue(vm.cancelState.value is CancelState.Idle)
        // Reload re-derived the CTA back to bookable.
        assertEquals(BookCta.Bookable, vm.ctaState.value)
    }

    @Test fun `confirmCancel failure surfaces Failed, keeps sheet and booking`() = runTest {
        val api = FakeApi(
            me(),
            upcoming = listOf(booking(id = 17, sessionId = 482)),
            cancelResult = { throw RuntimeException("boom") },
        )
        val vm = BookingViewModel(482, 5, false, api, api)
        vm.load()
        vm.openCancelSheet()
        vm.confirmCancel()
        val s = vm.cancelState.value
        assertTrue(s is CancelState.Failed)
        assertEquals("cancel_failed", (s as CancelState.Failed).code)
        assertTrue(vm.cancelSheetOpen.value)
        assertEquals(17, vm.existingBooking.value?.id)
    }

    @Test fun `confirmBooking is guarded against double-submit`() = runTest {
        // StandardTestDispatcher keeps the first launch suspended (Submitting)
        // until we advance, so the second synchronous call hits the guard.
        val sched = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(sched)
        try {
            val api = FakeApi(me(), createResult = { booking() })
            val vm = BookingViewModel(482, 5, false, api, api)
            vm.load(); advanceUntilIdle()
            vm.confirmBooking()   // launches, parks in Submitting
            vm.confirmBooking()   // blocked by the Submitting guard
            advanceUntilIdle()
            assertEquals(1, api.createCalls)
        } finally {
            Dispatchers.setMain(dispatcher)
        }
    }

    @Test fun `confirmCancel is guarded against double-submit`() = runTest {
        val sched = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(sched)
        try {
            val api = FakeApi(me(), upcoming = listOf(booking(id = 17, sessionId = 482)))
            val vm = BookingViewModel(482, 5, false, api, api)
            vm.load(); advanceUntilIdle()
            vm.confirmCancel()   // launches, parks in Submitting
            vm.confirmCancel()   // blocked by the Submitting guard
            advanceUntilIdle()
            assertEquals(1, api.cancelCalls)
        } finally {
            Dispatchers.setMain(dispatcher)
        }
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
