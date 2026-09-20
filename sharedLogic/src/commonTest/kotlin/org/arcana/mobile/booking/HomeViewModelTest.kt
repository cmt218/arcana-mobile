@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.arcana.mobile.booking

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.arcana.mobile.data.*
import org.arcana.mobile.home.HomeUiState
import org.arcana.mobile.home.HomeViewModel
import org.arcana.mobile.networking.ApiHttpError
import org.arcana.mobile.networking.BookingApi
import org.arcana.mobile.networking.ErrorType
import org.arcana.mobile.networking.MembershipApi
import org.arcana.mobile.schedule.FakeBookingApi
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

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
        override suspend fun myUpcoming(): MyUpcomingDto = MyUpcomingDto(myBookings().upcoming)
        override suspend fun myPast(cursor: String?, limit: Int): MyPastDto = MyPastDto(myBookings().past, null)
        override suspend fun createBooking(sessionId: Int, requestedSpotId: Int?, studioVisitedBefore: Boolean?, spotPreference: String?) = throw NotImplementedError()
        override suspend fun cancelBooking(bookingId: Int) = CancelBookingResponse("cancelled", true, false)
    }

    /** A real HTTP-failure exception, the same type `ArcanaApiClient.membershipMe()`
     *  actually throws in production for a non-2xx (via `bodyOrThrow`). */
    private fun serverException(statusCode: Int): Throwable = ApiHttpError(statusCode)

    private class FailingMembershipApi(private val error: Throwable) : MembershipApi {
        override suspend fun membershipMe(): MembershipMeDto = throw error
    }

    /** Fails [failuresBeforeSuccess] times, then succeeds; [failNext] can arm one
     *  more failure afterward (used to simulate a refresh failing). */
    /** Fails, but only after [gate] completes — so a retry can be held in flight. */
    private class GatedMembershipApi : MembershipApi {
        var calls = 0
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun membershipMe(): MembershipMeDto {
            calls++
            gate?.await()
            throw Exception("network failure")
        }
    }

    private class FlakyMembershipApi(failuresBeforeSuccess: Int) : MembershipApi {
        private var remainingFailures = failuresBeforeSuccess
        var failNext = false
        /** Counts every call so a test can prove retry taps didn't stack. */
        var calls = 0
        override suspend fun membershipMe(): MembershipMeDto {
            calls++
            if (remainingFailures > 0) {
                remainingFailures--
                throw Exception("network failure")
            }
            if (failNext) {
                failNext = false
                throw Exception("network failure")
            }
            return MembershipMeDto(
                member = MemberDto(1, "0002", "c@x.com", "Cole", "CT", "2026-05-01", 3, 2),
                membership = MembershipBriefDto(1, "active", TierDto("all-out-30", "All Out", 30)),
                currentPeriod = CurrentPeriodDto(1, 30, 7, 23, canBrowse = true, canBook = true),
            )
        }
    }

    /** Counts calls and can hold each one open, so a second load can be started
     *  while the first is still in flight. */
    private class CountingMembershipApi(private val me: MembershipMeDto) : MembershipApi, BookingApi {
        var calls = 0
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun membershipMe(): MembershipMeDto {
            calls++
            gate?.await()
            return me
        }
        override suspend fun myBookings() = MyBookingsDto(emptyList(), emptyList())
        override suspend fun myUpcoming(): MyUpcomingDto = MyUpcomingDto(myBookings().upcoming)
        override suspend fun myPast(cursor: String?, limit: Int): MyPastDto = MyPastDto(myBookings().past, null)
        override suspend fun createBooking(sessionId: Int, requestedSpotId: Int?, studioVisitedBefore: Boolean?, spotPreference: String?) = throw NotImplementedError()
        override suspend fun cancelBooking(bookingId: Int) = CancelBookingResponse("cancelled", true, false)
    }

    @Test fun `a burst of resumes does not spray fetches`() = runTest {
        val api = CountingMembershipApi(meDto)
        val time = TestTimeSource()
        val vm = HomeViewModel(api, api, time)
        vm.load()
        assertEquals(1, api.calls, "cold load should fetch once")

        // Tab flipping: every resume inside the window is dropped.
        repeat(10) { vm.load() }
        assertEquals(1, api.calls, "resumes inside the window must not refetch")

        time += 3.seconds
        vm.load()
        assertEquals(2, api.calls, "a resume past the window refetches")
    }

    @Test fun `an explicit refresh is never throttled`() = runTest {
        val api = CountingMembershipApi(meDto)
        val vm = HomeViewModel(api, api, TestTimeSource())
        vm.load()
        assertEquals(1, api.calls)
        // The member asked for this, so it goes through even inside the window.
        vm.refresh()
        assertEquals(2, api.calls)
    }

    @Test fun `a new fetch cancels the one in flight so the newest response wins`() = runTest {
        val api = CountingMembershipApi(meDto)
        val time = TestTimeSource()
        val gate = CompletableDeferred<Unit>()
        api.gate = gate
        val vm = HomeViewModel(api, api, time)
        vm.load()
        assertEquals(1, api.calls)

        // Second fetch while the first is parked on the gate.
        time += 3.seconds
        vm.load()
        assertEquals(2, api.calls)

        gate.complete(Unit)
        advanceUntilIdle()
        // The first was cancelled, so it cannot land after the second and
        // resurrect older data; state comes from the surviving fetch.
        assertTrue(vm.uiState.value is HomeUiState.Success)
    }

    @Test fun `a cancelled fetch is not reported as a failed refresh`() = runTest {
        val api = CountingMembershipApi(meDto)
        val time = TestTimeSource()
        api.gate = CompletableDeferred()
        val vm = HomeViewModel(api, api, time)
        vm.load()
        time += 3.seconds
        vm.load()          // cancels the parked first fetch
        advanceUntilIdle()
        assertFalse(vm.refreshFailed.value, "cancellation is not a refresh failure")
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
            override suspend fun myUpcoming(): MyUpcomingDto = MyUpcomingDto(myBookings().upcoming)
            override suspend fun myPast(cursor: String?, limit: Int): MyPastDto = MyPastDto(myBookings().past, null)
            override suspend fun createBooking(sessionId: Int, requestedSpotId: Int?, studioVisitedBefore: Boolean?, spotPreference: String?) = throw NotImplementedError()
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

    @Test
    fun `a network failure classifies as CONNECTION and never as a server error`() = runTest {
        val vm = HomeViewModel(
            membershipApi = FailingMembershipApi(Exception("network failure")),
            bookingApi = FakeBookingApi(),
        )
        vm.load()
        advanceUntilIdle()
        assertEquals(HomeUiState.Error(ErrorType.CONNECTION), vm.uiState.value)
    }

    @Test
    fun `a 5xx classifies as SERVER`() = runTest {
        val vm = HomeViewModel(
            membershipApi = FailingMembershipApi(serverException(503)),
            bookingApi = FakeBookingApi(),
        )
        vm.load()
        advanceUntilIdle()
        assertEquals(HomeUiState.Error(ErrorType.SERVER), vm.uiState.value)
    }

    @Test
    fun `retry re-runs the load and reaches Success once the API recovers`() = runTest {
        val api = FlakyMembershipApi(failuresBeforeSuccess = 1)
        val vm = HomeViewModel(membershipApi = api, bookingApi = FakeBookingApi())
        vm.load()
        advanceUntilIdle()
        assertEquals(HomeUiState.Error(ErrorType.CONNECTION), vm.uiState.value)

        vm.retry()
        advanceUntilIdle()
        assertTrue(vm.uiState.value is HomeUiState.Success)
    }

    @Test
    fun `a retry keeps the error on screen instead of flashing the loading skeleton`() = runTest {
        // The member reported this: tapping retry while still offline briefly
        // showed the loading UI before snapping back to the error. retry() used
        // to set Loading first; now the error stays put and the button carries
        // the progress via `retrying`.
        val api = FlakyMembershipApi(failuresBeforeSuccess = 2)
        val vm = HomeViewModel(membershipApi = api, bookingApi = FakeBookingApi())
        vm.load()
        advanceUntilIdle()
        val errorBefore = vm.uiState.value
        assertTrue(errorBefore is HomeUiState.Error)

        val seen = mutableListOf<HomeUiState>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.toList(seen) }
        vm.retry()
        advanceUntilIdle()
        job.cancel()

        assertTrue(
            seen.none { it is HomeUiState.Loading },
            "a failed retry must never pass through Loading: $seen",
        )
        assertTrue(vm.uiState.value is HomeUiState.Error)
        assertEquals(false, vm.retrying.value, "the retrying flag must clear when it settles")
    }

    @Test
    fun `repeat retry taps while one is in flight are ignored`() = runTest {
        // The fake must genuinely SUSPEND. With an instantly-throwing fake on an
        // immediate dispatcher the taps run sequentially and never overlap, so
        // the test would pass without a guard existing at all.
        val gate = CompletableDeferred<Unit>()
        val api = GatedMembershipApi()
        val vm = HomeViewModel(membershipApi = api, bookingApi = FakeBookingApi())
        vm.load()
        advanceUntilIdle()
        assertTrue(vm.uiState.value is HomeUiState.Error)

        api.gate = gate
        val before = api.calls
        vm.retry()
        vm.retry()
        vm.retry()
        assertEquals(1, api.calls - before, "retry taps stacked while one was in flight")

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(false, vm.retrying.value)
    }

    @Test
    fun `a refresh failure keeps Success content and raises refreshFailed`() = runTest {
        val api = FlakyMembershipApi(failuresBeforeSuccess = 0)
        val vm = HomeViewModel(membershipApi = api, bookingApi = FakeBookingApi())
        vm.load()
        advanceUntilIdle()
        assertTrue(vm.uiState.value is HomeUiState.Success)

        api.failNext = true
        vm.refresh()
        advanceUntilIdle()

        // The member keeps their last-good content; the failure is a notice,
        // not a takeover. This is the behavior the old caption silently lost.
        assertTrue(vm.uiState.value is HomeUiState.Success)
        assertTrue(vm.refreshFailed.value)
    }

    @Test
    fun `a refresh failure clears once a later refresh succeeds`() = runTest {
        val api = FlakyMembershipApi(failuresBeforeSuccess = 0)
        val vm = HomeViewModel(membershipApi = api, bookingApi = FakeBookingApi())
        vm.load()
        advanceUntilIdle()
        assertTrue(vm.uiState.value is HomeUiState.Success)

        api.failNext = true
        vm.refresh()
        advanceUntilIdle()
        assertTrue(vm.refreshFailed.value)

        // A later refresh succeeds (FlakyMembershipApi consumed failNext on
        // the throw above) — the notice must not persist past the recovery.
        vm.refresh()
        advanceUntilIdle()
        assertTrue(vm.uiState.value is HomeUiState.Success)
        assertFalse(vm.refreshFailed.value)
    }

    @Test
    fun `dismissRefreshFailed clears the flag`() = runTest {
        val api = FlakyMembershipApi(failuresBeforeSuccess = 0)
        val vm = HomeViewModel(membershipApi = api, bookingApi = FakeBookingApi())
        vm.load()
        advanceUntilIdle()
        assertTrue(vm.uiState.value is HomeUiState.Success)

        api.failNext = true
        vm.refresh()
        advanceUntilIdle()
        assertTrue(vm.refreshFailed.value)

        vm.dismissRefreshFailed()
        assertFalse(vm.refreshFailed.value)
        // Dismiss only clears the flag; it must not itself trigger a re-fetch.
        assertTrue(vm.uiState.value is HomeUiState.Success)
    }

    // ---- Review prompt (spec 6.4) ------------------------------------------

    private fun promptFor(id: Int) = org.arcana.mobile.review.prompt(id)

    private class PromptApi(val me: MembershipMeDto, var dto: MyUpcomingDto) : BookingApi, MembershipApi {
        var failUpcoming: Throwable? = null
        override suspend fun membershipMe() = me
        override suspend fun myBookings(): MyBookingsDto = throw AssertionError("Home reads the scoped list")
        override suspend fun myUpcoming(): MyUpcomingDto = failUpcoming?.let { throw it } ?: dto
        override suspend fun myPast(cursor: String?, limit: Int): MyPastDto = MyPastDto(emptyList(), null)
        override suspend fun createBooking(sessionId: Int, requestedSpotId: Int?, studioVisitedBefore: Boolean?, spotPreference: String?) = throw NotImplementedError()
        override suspend fun cancelBooking(bookingId: Int) = CancelBookingResponse("cancelled", true, false)
    }

    @Test fun `home reads the scoped list keeps the prompt and drops studio cancelled rows`() = runTest {
        val cancelled = booking(3).copy(status = "cancelled", cancelledBy = "studio")
        val api = PromptApi(meDto, MyUpcomingDto(listOf(cancelled, booking(1)), reviewPrompt = promptFor(9)))
        val vm = HomeViewModel(api, api, TestTimeSource())
        vm.load()
        val s = vm.uiState.value as HomeUiState.Success
        assertEquals(listOf(1), s.upcoming.map { it.id })
        assertEquals(9, s.reviewPrompt?.bookingId)
    }

    // The server quiets Home for a day after "Not now" (arcana-server
    // reviews/prompting.py), so its answer is normally no prompt at all; the
    // client still shows whatever it is told, so that rule lives in one place.
    @Test fun `not now hides the card at once and shows only what the server answers`() = runTest {
        val api = PromptApi(meDto, MyUpcomingDto(emptyList(), reviewPrompt = promptFor(9)))
        val reviews = org.arcana.mobile.review.FakeReviewApi().apply { nextPrompt = promptFor(8) }
        val (telemetry, fake, _) = org.arcana.mobile.analytics.fakeTelemetry()
        val vm = HomeViewModel(api, api, TestTimeSource(), reviewApi = reviews, telemetry = telemetry)
        vm.load()
        vm.dismissReviewPrompt()
        assertEquals(listOf(9), reviews.dismissed)
        assertEquals(8, (vm.uiState.value as HomeUiState.Success).reviewPrompt?.bookingId)
        assertEquals(listOf("review_dismissed"), fake.names())
        reviews.nextPrompt = null
        vm.dismissReviewPrompt()
        assertNull((vm.uiState.value as HomeUiState.Success).reviewPrompt)
        vm.dismissReviewPrompt()
        assertEquals(listOf(9, 8), reviews.dismissed)
    }

    // Swallowed, a failed reservations read rendered as "No upcoming classes":
    // a server fault dressed as an empty state. It is a failed fetch like any other.
    @Test fun `a failed reservations read is an error and never an empty home`() = runTest {
        val api = PromptApi(meDto, MyUpcomingDto(listOf(booking(1))))
        api.failUpcoming = org.arcana.mobile.networking.ApiHttpError(502)
        val vm = HomeViewModel(api, api, TestTimeSource())
        vm.load()
        assertEquals(HomeUiState.Error(org.arcana.mobile.networking.ErrorType.SERVER), vm.uiState.value)
        api.failUpcoming = null
        vm.retry()
        assertEquals(listOf(1), (vm.uiState.value as HomeUiState.Success).upcoming.map { it.id })
        // Warm: the reservations already on screen stay, and the notice is raised.
        api.failUpcoming = Exception("no route to host")
        vm.refresh()
        assertEquals(listOf(1), (vm.uiState.value as HomeUiState.Success).upcoming.map { it.id })
        assertTrue(vm.refreshFailed.value)
    }

    // Offline, the dismissal never reaches the server, so the next read offers
    // the same class again. The member already said "Not now".
    @Test fun `not now holds for the session when its request is lost`() = runTest {
        val api = PromptApi(meDto, MyUpcomingDto(emptyList(), reviewPrompt = promptFor(9)))
        val reviews = org.arcana.mobile.review.FakeReviewApi().apply { failWith = Exception("offline") }
        val vm = HomeViewModel(api, api, TestTimeSource(), reviewApi = reviews)
        vm.load()
        vm.dismissReviewPrompt()
        assertNull((vm.uiState.value as HomeUiState.Success).reviewPrompt)
        vm.refresh()
        assertNull((vm.uiState.value as HomeUiState.Success).reviewPrompt)
    }

    // Step one creates the review, so the very next read carries no prompt
    // (or, before the quiet day existed, the NEXT class). Either would pull
    // the card out from under a member who is still answering it.
    @Test fun `a card being answered survives a refresh until it is closed`() = runTest {
        val api = PromptApi(meDto, MyUpcomingDto(emptyList(), reviewPrompt = promptFor(9)))
        val vm = HomeViewModel(api, api, TestTimeSource())
        vm.load()
        vm.reviewStarted()
        api.dto = MyUpcomingDto(emptyList(), reviewPrompt = null)
        vm.refresh()
        assertEquals(9, (vm.uiState.value as HomeUiState.Success).reviewPrompt?.bookingId)
        vm.clearReviewPrompt()
        assertNull((vm.uiState.value as HomeUiState.Success).reviewPrompt)
        vm.refresh()
        assertNull((vm.uiState.value as HomeUiState.Success).reviewPrompt)
    }

    @Test fun `a finished card clears the prompt without a request`() = runTest {
        val api = PromptApi(meDto, MyUpcomingDto(emptyList(), reviewPrompt = promptFor(9)))
        val reviews = org.arcana.mobile.review.FakeReviewApi()
        val vm = HomeViewModel(api, api, TestTimeSource(), reviewApi = reviews)
        vm.load()
        vm.clearReviewPrompt()
        assertNull((vm.uiState.value as HomeUiState.Success).reviewPrompt)
        assertTrue(reviews.dismissed.isEmpty())
    }
}
