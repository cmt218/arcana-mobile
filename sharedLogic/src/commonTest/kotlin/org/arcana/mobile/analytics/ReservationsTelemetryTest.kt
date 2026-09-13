@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.arcana.mobile.analytics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.arcana.mobile.booking.MyBookingsViewModel
import org.arcana.mobile.booking.ReservationSegment
import org.arcana.mobile.data.BookingDto
import org.arcana.mobile.data.CancelBookingResponse
import org.arcana.mobile.data.CancelPolicyDto
import org.arcana.mobile.data.MyBookingsDto
import org.arcana.mobile.data.MyPastDto
import org.arcana.mobile.data.MyUpcomingDto
import org.arcana.mobile.data.SessionBriefDto
import org.arcana.mobile.networking.BookingApi
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Locks the reservations taxonomy: names and property keys are dashboard contracts. */
class ReservationsTelemetryTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun teardown() { Dispatchers.resetMain() }

    private fun booking(id: Int) = BookingDto(
        id = id, status = "completed", spot = null,
        session = SessionBriefDto(id, "2026-07-07T10:00:00Z", "2026-07-07T10:50:00Z", "RUN", "Barry's", locationId = 3),
        cancelPolicy = CancelPolicyDto(false, null),
    )

    private class FakeApi : BookingApi {
        override suspend fun myBookings(): MyBookingsDto = throw NotImplementedError()
        override suspend fun myUpcoming() = MyUpcomingDto(listOf(booking(1)))
        override suspend fun myPast(cursor: String?, limit: Int) =
            if (cursor == null) MyPastDto(listOf(booking(9), booking(8)), "c1") else MyPastDto(listOf(booking(7)), null)
        override suspend fun createBooking(sessionId: Int, requestedSpotId: Int?, studioVisitedBefore: Boolean?, spotPreference: String?): BookingDto = throw NotImplementedError()
        override suspend fun cancelBooking(bookingId: Int) = CancelBookingResponse("cancelled", true, false)
        private fun booking(id: Int) = BookingDto(
            id = id, status = "completed", spot = null,
            session = SessionBriefDto(id, "2026-07-07T10:00:00Z", "2026-07-07T10:50:00Z", "RUN", "Barry's", locationId = 3),
            cancelPolicy = CancelPolicyDto(false, null),
        )
    }

    @Test fun `event names are stable`() {
        assertEquals("reservations_opened", Telemetry.Events.RESERVATIONS_OPENED)
        assertEquals("reservations_segment_changed", Telemetry.Events.RESERVATIONS_SEGMENT_CHANGED)
        assertEquals("reservations_page_loaded", Telemetry.Events.RESERVATIONS_PAGE_LOADED)
        assertEquals("address_tapped", Telemetry.Events.ADDRESS_TAPPED)
    }

    @Test fun `opened fires once per screen with its source`() = runTest {
        val fake = FakeAnalytics()
        val vm = MyBookingsViewModel(FakeApi(), Telemetry(fake, NoopCrashReporter))
        vm.onOpened("you")
        vm.onOpened("you")
        val opened = fake.events.filter { it.name == "reservations_opened" }
        assertEquals(1, opened.size)
        assertEquals(mapOf<String, Any?>("source" to "you"), opened.single().properties)
    }

    @Test fun `segment change and past pages carry their properties`() = runTest {
        val fake = FakeAnalytics()
        val vm = MyBookingsViewModel(FakeApi(), Telemetry(fake, NoopCrashReporter))
        vm.load()
        vm.selectSegment(ReservationSegment.Past)
        vm.loadMore()
        val names = fake.events.map { it.name }
        assertEquals(
            listOf("reservations_segment_changed", "reservations_page_loaded", "reservations_page_loaded"),
            names,
        )
        assertEquals(mapOf<String, Any?>("segment" to "past"), fake.events[0].properties)
        assertEquals(mapOf<String, Any?>("page_index" to 0, "count" to 2), fake.events[1].properties)
        assertEquals(mapOf<String, Any?>("page_index" to 1, "count" to 1), fake.events[2].properties)
    }

    @Test fun `cancel from reservations reuses the booking cancel taxonomy`() = runTest {
        val fake = FakeAnalytics()
        val vm = MyBookingsViewModel(FakeApi(), Telemetry(fake, NoopCrashReporter))
        vm.load()
        vm.openCancel(booking(1))
        vm.confirmCancel()
        val names = fake.events.map { it.name }
        assertEquals(listOf("booking_cancel_started", "booking_cancelled"), names)
        assertEquals(3, fake.events[1].properties["location_id"])
    }

    @Test fun `address tapped carries surface and app`() {
        val fake = FakeAnalytics()
        Telemetry(fake, NoopCrashReporter).addressTapped(surface = "class_detail", app = "apple")
        assertEquals(mapOf<String, Any?>("surface" to "class_detail", "app" to "apple"), fake.events.single().properties)
    }
}
