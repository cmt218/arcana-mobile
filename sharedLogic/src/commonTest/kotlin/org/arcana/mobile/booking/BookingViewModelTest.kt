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
import org.arcana.mobile.networking.ApiHttpError
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
    // Fakes an exception carrying a real HTTP status, the way ApiHttpError does
    // for a genuine server answer (see ArcanaApiClient.bodyOrThrow).
    private fun serverException(statusCode: Int): Throwable = ApiHttpError(statusCode)

    private class FakeApi(
        val meResult: MembershipMeDto,
        var upcoming: List<BookingDto> = emptyList(),
        val createResult: () -> BookingDto = { throw IllegalStateException() },
        val cancelResult: () -> CancelBookingResponse = { CancelBookingResponse("cancelled", true, false) },
    ) : BookingApi, MembershipApi {
        var created: Pair<Int, Int?>? = null
        var createdVisitedBefore: Boolean? = null
        var createdSpotPreference: String? = null
        var cancelledId: Int? = null
        var cancelCalls: Int = 0
        var createCalls: Int = 0
        override suspend fun membershipMe() = meResult
        override suspend fun myBookings() = MyBookingsDto(upcoming, emptyList())
        override suspend fun createBooking(sessionId: Int, requestedSpotId: Int?, studioVisitedBefore: Boolean?, spotPreference: String?): BookingDto {
            created = sessionId to requestedSpotId
            createdVisitedBefore = studioVisitedBefore
            createdSpotPreference = spotPreference
            createCalls++; return createResult()
        }
        override suspend fun cancelBooking(bookingId: Int): CancelBookingResponse {
            cancelledId = bookingId; cancelCalls++; return cancelResult()
        }
    }

    /** Fails membershipMe; everything else behaves. */
    private class FailingMeApi(private val err: Throwable) : BookingApi, MembershipApi {
        override suspend fun membershipMe(): MembershipMeDto = throw err
        override suspend fun myBookings() = MyBookingsDto(emptyList(), emptyList())
        override suspend fun createBooking(sessionId: Int, requestedSpotId: Int?, studioVisitedBefore: Boolean?, spotPreference: String?): BookingDto =
            throw IllegalStateException()
        override suspend fun cancelBooking(bookingId: Int): CancelBookingResponse =
            throw IllegalStateException()
    }

    @Test fun `a failed membership fetch flags the failure and still marks loaded`() = runTest {
        // `loaded` is what the CTA reads as "still fetching". Leaving it false on
        // a failed FIRST fetch spins the primary CTA forever.
        val api = FailingMeApi(serverException(500))
        val vm = BookingViewModel(sessionId = 482, spotsAvailable = 5, requiresSpot = false, bookingApi = api, membershipApi = api)
        vm.load()
        assertEquals(true, vm.membershipLoadFailed.value)
        assertEquals(true, vm.loaded.value, "a failed fetch must not leave the CTA spinning")
    }

    @Test fun `a failed refresh keeps the CTA it already resolved`() = runTest {
        val ok = FakeApi(me(remaining = 10))
        val vm = BookingViewModel(sessionId = 482, spotsAvailable = 5, requiresSpot = false, bookingApi = ok, membershipApi = ok)
        vm.load()
        assertEquals(BookCta.Bookable, vm.ctaState.value)

        // Same VM, membership now failing: the CTA must not downgrade to a claim
        // about the member's account that the failure doesn't support.
        val failing = FailingMeApi(serverException(500))
        val vm2 = BookingViewModel(sessionId = 482, spotsAvailable = 5, requiresSpot = false, bookingApi = failing, membershipApi = failing)
        vm2.load()
        assertEquals(
            BookCta.Unknown,
            vm2.ctaState.value,
            "a cold failure knows nothing; it must not claim the member has no membership",
        )
        assertEquals(true, vm2.membershipLoadFailed.value)
    }

    /** The bug: a failed first fetch used to land on NotBookable, whose label is
     *  "NO ACTIVE MEMBERSHIP" — a false statement about a paying member's
     *  account, shown at the moment they are trying to book. */
    @Test fun `a cold membership failure never claims the member has no membership`() = runTest {
        val failing = FailingMeApi(serverException(500))
        val vm = BookingViewModel(sessionId = 482, spotsAvailable = 5, requiresSpot = false, bookingApi = failing, membershipApi = failing)
        vm.load()

        assertEquals(BookCta.Unknown, vm.ctaState.value)
        assertNotEquals(BookCta.NotBookable.label, vm.ctaState.value.label)
        assertEquals(false, vm.ctaState.value.enabled, "unknown must never be actionable")
        assertEquals(true, vm.loaded.value, "must not spin forever")
    }

    /** The opposite case still has to work: a member who genuinely has no usable
     *  membership is told so, because that IS established. */
    @Test fun `a member with no usable period still gets NO ACTIVE MEMBERSHIP`() = runTest {
        val api = FakeApi(me(remaining = 10).copy(currentPeriod = null, upcomingPeriod = null))
        val vm = BookingViewModel(sessionId = 482, spotsAvailable = 5, requiresSpot = false, bookingApi = api, membershipApi = api)
        vm.load()
        assertEquals(BookCta.NotBookable, vm.ctaState.value)
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

    @Test fun `studio-visit prompt gates confirm until answered`() = runTest {
        val api = FakeApi(me(), createResult = { booking() })
        val vm = BookingViewModel(482, 5, requiresSpot = false, bookingApi = api, membershipApi = api)
        vm.load()
        vm.setShouldAskStudioVisit(true)
        assertFalse(vm.canConfirm)          // unanswered → blocked
        vm.answerStudioVisit(true)
        assertTrue(vm.canConfirm)            // answered → allowed
    }

    @Test fun `confirm forwards the studio-visit answer`() = runTest {
        val api = FakeApi(me(), createResult = { booking() })
        val vm = BookingViewModel(482, 5, requiresSpot = false, bookingApi = api, membershipApi = api)
        vm.load()
        vm.setShouldAskStudioVisit(true)
        vm.answerStudioVisit(false)
        vm.confirmBooking()
        assertEquals(false, api.createdVisitedBefore)
    }

    @Test fun `no prompt - confirm sends a null visit answer`() = runTest {
        val api = FakeApi(me(), createResult = { booking() })
        val vm = BookingViewModel(482, 5, requiresSpot = false, bookingApi = api, membershipApi = api)
        vm.load()  // shouldAsk defaults false
        assertTrue(vm.canConfirm)
        vm.confirmBooking()
        assertNull(api.createdVisitedBefore)
    }

    @Test fun `spot studios still gate on both spot and visit answer`() = runTest {
        val api = FakeApi(me(), createResult = { booking() })
        val vm = BookingViewModel(482, 5, requiresSpot = true, bookingApi = api, membershipApi = api)
        vm.load()
        vm.setShouldAskStudioVisit(true)
        assertFalse(vm.canConfirm)                       // neither spot nor answer
        vm.selectSpot(SpotDto(id = 1, label = "Bike 14"))
        assertFalse(vm.canConfirm)                       // spot but no answer
        vm.answerStudioVisit(true)
        assertTrue(vm.canConfirm)                        // both → allowed
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

    @Test fun `openCancelSheet no-ops without an existing booking but opens with one`() = runTest {
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

    @Test fun `confirmCancel cancels and clears booking then closes sheet and reloads`() = runTest {
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

    @Test fun `confirmCancel failure surfaces Failed but keeps sheet and booking`() = runTest {
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
        // RuntimeException("boom") carries no HTTP status, so per toErrorType()
        // it classifies as CONNECTION, not the old flat "cancel_failed".
        assertEquals("connection_failed", (s as CancelState.Failed).code)
        assertTrue(vm.cancelSheetOpen.value)
        assertEquals(17, vm.existingBooking.value?.id)
    }

    @Test fun `a cancel network failure is not reported as a generic cancel failure`() = runTest {
        val api = FakeApi(
            me(),
            upcoming = listOf(booking(id = 17, sessionId = 482)),
            cancelResult = { throw Exception("network failure") },
        )
        val vm = BookingViewModel(482, 5, false, api, api)
        vm.load()
        vm.openCancelSheet()
        vm.confirmCancel()
        assertEquals(CancelState.Failed("connection_failed"), vm.cancelState.value)
    }

    @Test fun `a cancel 5xx is reported as a server failure`() = runTest {
        val api = FakeApi(
            me(),
            upcoming = listOf(booking(id = 17, sessionId = 482)),
            cancelResult = { throw serverException(500) },
        )
        val vm = BookingViewModel(482, 5, false, api, api)
        vm.load()
        vm.openCancelSheet()
        vm.confirmCancel()
        assertEquals(CancelState.Failed("server_failed"), vm.cancelState.value)
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

    // ── CONNECTION/SERVER conflation (ERR-11) ---------------------------------

    @Test fun `a network failure is not reported as a generic booking failure`() = runTest {
        val api = FakeApi(me(), createResult = { throw Exception("network failure") })
        val vm = BookingViewModel(482, 5, false, api, api)
        vm.load()
        vm.confirmBooking()
        assertEquals(BookingSubmit.Failed("connection_failed"), vm.submitState.value)
    }

    @Test fun `a 5xx is reported as a server failure`() = runTest {
        val api = FakeApi(me(), createResult = { throw serverException(500) })
        val vm = BookingViewModel(482, 5, false, api, api)
        vm.load()
        vm.confirmBooking()
        assertEquals(BookingSubmit.Failed("server_failed"), vm.submitState.value)
    }

    @Test fun `a typed server reason code still wins over the transport category`() = runTest {
        val api = FakeApi(me(), createResult = { throw BookingError("session_full") })
        val vm = BookingViewModel(482, 5, false, api, api)
        vm.load()
        vm.confirmBooking()
        // The server told us WHY. That is strictly more useful than "server".
        assertEquals(BookingSubmit.Failed("session_full"), vm.submitState.value)
    }

    // ── spot preference dropdown ---------------------------------------------

    @Test fun `spot preference options exposed when template has them and no real spots`() = runTest {
        val api = FakeApi(me(), createResult = { booking() })
        val vm = BookingViewModel(482, 5, requiresSpot = false, api, api)
        vm.setSpotPreferenceOptions(listOf("Bag", "Bench"), label = "Side")
        vm.load()
        assertEquals(listOf("Bag", "Bench"), vm.spotPreferenceOptions)
        assertEquals("Side", vm.spotPreferenceLabel)
        assertNull(vm.selectedSpotPreference.value)  // null → placeholder, deliberate choice
    }

    @Test fun `blank label from server normalizes to null so the UI default applies`() = runTest {
        val api = FakeApi(me(), createResult = { booking() })
        val vm = BookingViewModel(482, 5, requiresSpot = false, api, api)
        // The server stores an unset label as "" (CharField, not null), so the VM
        // must normalize blank → null for the sheet's "Spot preference" fallback.
        vm.setSpotPreferenceOptions(listOf("Bag", "Bench"), label = "")
        vm.load()
        assertNull(vm.spotPreferenceLabel)
    }

    @Test fun `no spot preference options exposed when template has none`() = runTest {
        val api = FakeApi(me(), createResult = { booking() })
        val vm = BookingViewModel(482, 5, requiresSpot = false, api, api)
        vm.load()
        assertTrue(vm.spotPreferenceOptions.isEmpty())
    }

    @Test fun `real spots win - preference suppressed even if options present`() = runTest {
        val api = FakeApi(me(), createResult = { booking() })
        val vm = BookingViewModel(482, 5, requiresSpot = true, api, api)
        vm.setSpotPreferenceOptions(listOf("Bag", "Bench"), label = "Side")
        vm.load()
        // Options are not surfaced and don't gate confirm — the real spot picker owns this class.
        assertTrue(vm.spotPreferenceOptions.isEmpty())
        vm.selectSpot(SpotDto(29, "DF-21"))
        assertTrue(vm.canConfirm)  // not blocked on a (suppressed) preference
    }

    @Test fun `preference never gates confirm - it is always best-effort`() = runTest {
        val api = FakeApi(me(), createResult = { booking() })
        val vm = BookingViewModel(482, 5, requiresSpot = false, api, api)
        vm.setSpotPreferenceOptions(listOf("Bag", "Bench"), label = "Side")
        vm.load()
        // Even with options present and nothing chosen, confirm is allowed.
        assertTrue(vm.canConfirm)
    }

    @Test fun `confirm forwards the chosen spot preference`() = runTest {
        val api = FakeApi(me(), createResult = { booking() })
        val vm = BookingViewModel(482, 5, requiresSpot = false, api, api)
        vm.setSpotPreferenceOptions(listOf("Bag", "Bench"), label = "Side")
        vm.load()
        vm.selectSpotPreference("Bench")
        vm.confirmBooking()
        assertEquals("Bench", api.createdSpotPreference)
    }

    @Test fun `no options - confirm sends a null preference`() = runTest {
        val api = FakeApi(me(), createResult = { booking() })
        val vm = BookingViewModel(482, 5, requiresSpot = false, api, api)
        vm.load()
        vm.confirmBooking()
        assertNull(api.createdSpotPreference)
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
