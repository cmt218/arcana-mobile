@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.arcana.mobile.analytics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.arcana.mobile.booking.BookingStudioContext
import org.arcana.mobile.booking.BookingViewModel
import org.arcana.mobile.data.BookingDto
import org.arcana.mobile.data.CancelBookingResponse
import org.arcana.mobile.data.CancelPolicyDto
import org.arcana.mobile.data.CurrentPeriodDto
import org.arcana.mobile.data.MemberDto
import org.arcana.mobile.data.MembershipBriefDto
import org.arcana.mobile.data.MembershipMeDto
import org.arcana.mobile.data.MyBookingsDto
import org.arcana.mobile.data.SessionBriefDto
import org.arcana.mobile.data.TierDto
import org.arcana.mobile.networking.BookingApi
import org.arcana.mobile.networking.MembershipApi
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BookingTelemetryTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun teardown() { Dispatchers.resetMain() }

    private fun me() = MembershipMeDto(
        member = MemberDto(1, "0002", "c@x.com", "Cole", "CT", "2026-05-01", 3, 2),
        membership = MembershipBriefDto(1, "active", TierDto("all-out-30", "All Out", 30)),
        currentPeriod = CurrentPeriodDto(1, 30, 20, 10, canBrowse = true, canBook = true),
    )
    private fun booking() = BookingDto(
        id = 17, status = "requested", spot = null,
        session = SessionBriefDto(482, "2026-07-07T10:00:00Z", "2026-07-07T10:50:00Z", "RUN", "Barry's"),
        cancelPolicy = CancelPolicyDto(false, null),
    )

    private class FakeApi(val meResult: MembershipMeDto, val createResult: () -> BookingDto) : BookingApi, MembershipApi {
        override suspend fun membershipMe() = meResult
        override suspend fun myBookings() = MyBookingsDto(emptyList(), emptyList())
        override suspend fun createBooking(sessionId: Int, requestedSpotId: Int?, studioVisitedBefore: Boolean?) = createResult()
        override suspend fun cancelBooking(bookingId: Int) = CancelBookingResponse("cancelled", true, false)
    }

    private fun vm(api: FakeApi, telemetry: Telemetry) = BookingViewModel(
        sessionId = 482, spotsAvailable = 5, requiresSpot = false,
        bookingApi = api, membershipApi = api,
        telemetry = telemetry,
        studioContext = BookingStudioContext(studioId = 9, studioName = "Barry's", locationId = 3, locationName = "Williamsburg"),
    )

    @Test fun `successful booking emits submitted then succeeded with studio + location`() = runTest {
        val (telemetry, analytics, _) = fakeTelemetry()
        val api = FakeApi(me(), createResult = { booking() })
        val v = vm(api, telemetry)
        v.load()
        v.confirmBooking()

        assertEquals(listOf("booking_submitted", "booking_succeeded"), analytics.names())
        val ok = analytics.first("booking_succeeded")!!
        assertEquals(17, ok.properties["booking_id"])
        assertEquals("requested", ok.properties["status"])
        assertEquals(9, ok.properties["studio_id"])
        assertEquals(3, ok.properties["location_id"])
    }

    @Test fun `studio-visit prompt-shown fires when opening a sheet that asks`() = runTest {
        val (telemetry, analytics, _) = fakeTelemetry()
        val api = FakeApi(me(), createResult = { booking() })
        val v = vm(api, telemetry)
        v.load()
        v.setShouldAskStudioVisit(true)
        v.openSheet()
        val shown = analytics.first("studio_visit_prompt_shown")
        assertTrue(shown != null)
        assertEquals("Barry's", shown.properties["studio_name"])
    }

    @Test fun `studio-visit prompt-shown does NOT fire when not asked`() = runTest {
        val (telemetry, analytics, _) = fakeTelemetry()
        val api = FakeApi(me(), createResult = { booking() })
        val v = vm(api, telemetry)
        v.load()
        v.openSheet()  // shouldAsk defaults false
        assertTrue("studio_visit_prompt_shown" !in analytics.names())
    }

    @Test fun `answering the studio-visit prompt emits the answer`() = runTest {
        val (telemetry, analytics, _) = fakeTelemetry()
        val api = FakeApi(me(), createResult = { booking() })
        val v = vm(api, telemetry)
        v.load()
        v.setShouldAskStudioVisit(true)
        v.answerStudioVisit(true)
        val ans = analytics.first("studio_visit_answered")!!
        assertEquals(true, ans.properties["visited_before"])
        assertEquals("Barry's", ans.properties["studio_name"])
    }

    @Test fun `dismissing an open sheet without booking emits abandonment`() = runTest {
        val (telemetry, analytics, _) = fakeTelemetry()
        val api = FakeApi(me(), createResult = { booking() })
        val v = vm(api, telemetry)
        v.load()
        v.openSheet()
        v.dismissSheet()

        val names = analytics.names()
        assertTrue("booking_sheet_opened" in names)
        assertTrue("booking_sheet_abandoned" in names)
        assertTrue("booking_succeeded" !in names)
        assertEquals(false, analytics.first("booking_sheet_abandoned")!!.properties["had_selected_spot"])
    }
}
