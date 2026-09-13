@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.arcana.mobile.booking

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import org.arcana.mobile.data.*
import org.arcana.mobile.networking.ApiHttpError
import org.arcana.mobile.networking.BookingApi
import org.arcana.mobile.networking.ErrorType
import kotlin.test.*

class MyBookingsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun teardown() { Dispatchers.resetMain() }

    private fun booking(id: Int, status: String = "confirmed", cancelledBy: String = "") = BookingDto(
        id = id, status = status, spot = null,
        session = SessionBriefDto(id, "2026-07-07T10:00:00Z", "2026-07-07T10:50:00Z", "RUN", "Barry's", locationId = 7),
        cancelPolicy = CancelPolicyDto(false, null),
        cancelledBy = cancelledBy,
    )

    /** Scoped reads are programmable per call; the unscoped read is never used here. */
    private class FakeApi(
        var upcoming: suspend () -> MyUpcomingDto = { MyUpcomingDto(emptyList()) },
        var pastPages: suspend (String?) -> MyPastDto = { MyPastDto(emptyList(), null) },
    ) : BookingApi {
        var cancelled = mutableListOf<Int>()
        var cancelResult: suspend () -> CancelBookingResponse = { CancelBookingResponse("cancelled", true, false) }
        val pastCursors = mutableListOf<String?>()
        override suspend fun myBookings(): MyBookingsDto = throw NotImplementedError()
        override suspend fun myUpcoming(): MyUpcomingDto = upcoming()
        override suspend fun myPast(cursor: String?, limit: Int): MyPastDto { pastCursors += cursor; return pastPages(cursor) }
        override suspend fun createBooking(sessionId: Int, requestedSpotId: Int?, studioVisitedBefore: Boolean?, spotPreference: String?): BookingDto = throw NotImplementedError()
        override suspend fun cancelBooking(bookingId: Int): CancelBookingResponse { cancelled += bookingId; return cancelResult() }
    }

    @Test fun `load fetches upcoming only and leaves past untouched`() = runTest {
        val api = FakeApi(upcoming = { MyUpcomingDto(listOf(booking(1))) })
        val vm = MyBookingsViewModel(api)
        vm.load()
        assertEquals(UpcomingUiState.Success(listOf(booking(1))), vm.upcoming.value)
        assertEquals(PastUiState.Loading, vm.past.value)
        assertTrue(api.pastCursors.isEmpty())
    }

    @Test fun `studio cancelled rows stay in upcoming as delivered`() = runTest {
        val row = booking(4, status = "cancelled", cancelledBy = "studio")
        val vm = MyBookingsViewModel(FakeApi(upcoming = { MyUpcomingDto(listOf(row)) }))
        vm.load()
        val s = vm.upcoming.value as UpcomingUiState.Success
        assertTrue(s.bookings.single().cancelledByStudio)
    }

    @Test fun `selecting past loads its first page once`() = runTest {
        val api = FakeApi(pastPages = { MyPastDto(listOf(booking(9, "completed")), "c1") })
        val vm = MyBookingsViewModel(api)
        vm.load()
        vm.selectSegment(ReservationSegment.Past)
        vm.selectSegment(ReservationSegment.Upcoming)
        vm.selectSegment(ReservationSegment.Past)
        assertEquals(listOf<String?>(null), api.pastCursors)
        assertEquals(PastUiState.Success(listOf(booking(9, "completed")), "c1"), vm.past.value)
    }

    @Test fun `load more appends the next page dedupes and stops at the last cursor`() = runTest {
        val api = FakeApi(pastPages = { cursor ->
            when (cursor) {
                null -> MyPastDto(listOf(booking(9, "completed"), booking(8, "completed")), "c1")
                "c1" -> MyPastDto(listOf(booking(8, "completed"), booking(7, "completed")), null)
                else -> error("unexpected cursor $cursor")
            }
        })
        val vm = MyBookingsViewModel(api)
        vm.selectSegment(ReservationSegment.Past)
        vm.loadMore()
        val s = vm.past.value as PastUiState.Success
        assertEquals(listOf(9, 8, 7), s.bookings.map { it.id })
        assertNull(s.nextCursor)
        assertFalse(s.loadingMore)
        vm.loadMore()
        assertEquals(listOf(null, "c1"), api.pastCursors)
    }

    @Test fun `a failed page keeps the rows and blocks auto load until retried`() = runTest {
        var fail = true
        val api = FakeApi(pastPages = { cursor ->
            if (cursor == null) MyPastDto(listOf(booking(9, "completed")), "c1")
            else if (fail) throw ApiHttpError(502)
            else MyPastDto(listOf(booking(8, "completed")), null)
        })
        val vm = MyBookingsViewModel(api)
        vm.selectSegment(ReservationSegment.Past)
        vm.loadMore()
        val failed = vm.past.value as PastUiState.Success
        assertEquals(ErrorType.SERVER, failed.pageError)
        assertEquals(listOf(9), failed.bookings.map { it.id })
        vm.loadMore()
        assertEquals(2, api.pastCursors.size, "a scroll-triggered loadMore must not retry a failed page")
        fail = false
        vm.retryLoadMore()
        val ok = vm.past.value as PastUiState.Success
        assertNull(ok.pageError)
        assertEquals(listOf(9, 8), ok.bookings.map { it.id })
    }

    @Test fun `refresh on past resets to page one and discards a stale load more`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val api = FakeApi(pastPages = { cursor ->
            when (cursor) {
                null -> MyPastDto(listOf(booking(9, "completed")), "c1")
                "c1" -> { gate.await(); MyPastDto(listOf(booking(1, "completed")), null) }
                else -> error("unexpected")
            }
        })
        val vm = MyBookingsViewModel(api)
        vm.selectSegment(ReservationSegment.Past)
        vm.loadMore()
        vm.refresh()
        gate.complete(Unit)
        val s = vm.past.value as PastUiState.Success
        assertEquals(listOf(9), s.bookings.map { it.id }, "the stale page 2 must not append after a refresh")
        assertEquals("c1", s.nextCursor)
        assertFalse(vm.isRefreshing.value)
    }

    @Test fun `a failed refresh keeps content and raises the snackbar flag`() = runTest {
        var fail = false
        val api = FakeApi(upcoming = { if (fail) throw Exception("network failure") else MyUpcomingDto(listOf(booking(1))) })
        val vm = MyBookingsViewModel(api)
        vm.load()
        fail = true
        vm.refresh()
        assertEquals(UpcomingUiState.Success(listOf(booking(1))), vm.upcoming.value)
        assertTrue(vm.refreshFailed.value)
        vm.dismissRefreshFailed()
        assertFalse(vm.refreshFailed.value)
    }

    @Test fun `a network failure with nothing loaded classifies as CONNECTION`() = runTest {
        val vm = MyBookingsViewModel(FakeApi(upcoming = { throw Exception("network failure") }))
        vm.load()
        assertEquals(UpcomingUiState.Error(ErrorType.CONNECTION), vm.upcoming.value)
    }

    @Test fun `a 5xx on the past page classifies as SERVER`() = runTest {
        val vm = MyBookingsViewModel(FakeApi(pastPages = { throw ApiHttpError(502) }))
        vm.selectSegment(ReservationSegment.Past)
        assertEquals(PastUiState.Error(ErrorType.SERVER), vm.past.value)
    }

    @Test fun `retry replaces the error with content and clears the retrying flag`() = runTest {
        var fail = true
        val vm = MyBookingsViewModel(FakeApi(upcoming = { if (fail) throw ApiHttpError(503) else MyUpcomingDto(listOf(booking(2))) }))
        vm.load()
        assertEquals(UpcomingUiState.Error(ErrorType.SERVER), vm.upcoming.value)
        fail = false
        vm.retry()
        assertEquals(UpcomingUiState.Success(listOf(booking(2))), vm.upcoming.value)
        assertFalse(vm.retrying.value)
    }

    @Test fun `confirm cancel drops the row immediately then refetches`() = runTest {
        var calls = 0
        val api = FakeApi(upcoming = {
            calls += 1
            if (calls == 1) MyUpcomingDto(listOf(booking(1), booking(2))) else MyUpcomingDto(listOf(booking(2)))
        })
        val vm = MyBookingsViewModel(api)
        vm.load()
        vm.openCancel(booking(1))
        assertEquals(booking(1), vm.cancelTarget.value)
        vm.confirmCancel()
        assertEquals(listOf(1), api.cancelled)
        assertNull(vm.cancelTarget.value)
        assertEquals(CancelState.Idle, vm.cancelState.value)
        assertEquals(listOf(2), (vm.upcoming.value as UpcomingUiState.Success).bookings.map { it.id })
        assertEquals(2, calls)
    }

    @Test fun `a failed cancel keeps the sheet open with the transport code`() = runTest {
        val api = FakeApi(upcoming = { MyUpcomingDto(listOf(booking(1))) })
        api.cancelResult = { throw ApiHttpError(500) }
        val vm = MyBookingsViewModel(api)
        vm.load()
        vm.openCancel(booking(1))
        vm.confirmCancel()
        assertEquals(booking(1), vm.cancelTarget.value)
        assertEquals(CancelState.Failed("server_failed"), vm.cancelState.value)
        assertEquals(listOf(1), (vm.upcoming.value as UpcomingUiState.Success).bookings.map { it.id })
        vm.dismissCancel()
        assertNull(vm.cancelTarget.value)
        assertEquals(CancelState.Idle, vm.cancelState.value)
    }
}
