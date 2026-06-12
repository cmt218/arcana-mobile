@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.arcana.mobile.schedule

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.arcana.mobile.favorites.FavoritesRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The already-booked indicator: the schedule VM fetches the member's live
 *  bookings (best-effort) and exposes a sessionId → status map so rows for
 *  classes the member is already in can show a status pill. */
class ScheduleViewModelBookingsTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun teardown() { Dispatchers.resetMain() }

    private fun vm(
        scheduleApi: FakeScheduleApi,
        bookingApi: FakeBookingApi,
        favoritesApi: FakeFavoritesApi = FakeFavoritesApi(),
    ): ScheduleViewModel = ScheduleViewModel(
        api = scheduleApi,
        favoritesRepository = FavoritesRepository(favoritesApi),
        bookingApi = bookingApi,
    )

    @Test fun `init maps upcoming bookings' session ids to their statuses`() = runTest {
        val api = FakeScheduleApi()
        api.pageResult = { pageOf(1, 2, 3) }
        val bookingApi = FakeBookingApi(
            myBookingsResult = {
                myBookings(
                    bookingOn(sessionId = 1, status = "requested"),
                    bookingOn(sessionId = 3, status = "confirmed"),
                )
            },
        )
        val vm = vm(api, bookingApi)

        val state = vm.success()
        assertEquals(mapOf(1 to "requested", 3 to "confirmed"), state.bookedSessions)
    }

    @Test fun `a bookings-fetch failure does not break the schedule`() = runTest {
        val api = FakeScheduleApi()
        api.pageResult = { pageOf(1, 2) }
        val bookingApi = FakeBookingApi(
            myBookingsResult = { throw RuntimeException("bookings down") },
        )
        val vm = vm(api, bookingApi)

        val state = vm.success() // schedule still renders
        assertEquals(listOf(1, 2), state.dayStates.getValue(state.selectedDate).sessions.map { it.id })
        assertTrue(state.bookedSessions.isEmpty())
    }

    @Test fun `refresh re-fetches bookings and reflects the changed result`() = runTest {
        val api = FakeScheduleApi()
        api.pageResult = { pageOf(1, 2) }
        val bookingApi = FakeBookingApi(
            myBookingsResult = { myBookings(bookingOn(sessionId = 1, status = "requested")) },
        )
        val vm = vm(api, bookingApi)
        assertEquals(mapOf(1 to "requested"), vm.success().bookedSessions)

        // The booking was confirmed (and a new one added) while the member sat
        // on the schedule — pull-to-refresh must surface the new state.
        bookingApi.myBookingsResult = {
            myBookings(
                bookingOn(sessionId = 1, status = "confirmed"),
                bookingOn(sessionId = 2, status = "requested"),
            )
        }
        vm.refresh()

        assertEquals(mapOf(1 to "confirmed", 2 to "requested"), vm.success().bookedSessions)
    }

    @Test fun `refreshBookings re-fetches bookings on resume and clears a cancelled pill`() = runTest {
        val api = FakeScheduleApi()
        api.pageResult = { pageOf(1, 2) }
        val bookingApi = FakeBookingApi(
            myBookingsResult = { myBookings(bookingOn(sessionId = 1, status = "requested")) },
        )
        val vm = vm(api, bookingApi)
        assertEquals(mapOf(1 to "requested"), vm.success().bookedSessions)

        // The member cancelled session 1 from ClassDetail and backed out — the
        // resume-refresh must clear its stale pill without a manual refresh.
        bookingApi.myBookingsResult = { myBookings() }
        vm.refreshBookings()

        assertTrue(vm.success().bookedSessions.isEmpty())
    }
}
