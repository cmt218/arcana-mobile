@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.arcana.mobile.schedule

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import org.arcana.mobile.data.FavoritesDto
import org.arcana.mobile.favorites.FavoritesRepository
import org.arcana.mobile.networking.ErrorType
import org.arcana.mobile.networking.ScheduleApi
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Task 5: migrating Schedule's error state to the shared [ErrorType] system
 * (5a), SCHED-02 (5b, favorites scope silently dropped by a successful
 * retry), and ERR-03 (5c, an uncached day-chip fetch failure leaving the day
 * spinning forever with zero UI).
 */
class ScheduleViewModelErrorStateTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun teardown() { Dispatchers.resetMain() }

    private val today = Clock.System.todayIn(ScheduleViewModel.ScheduleTimeZone)
    private val tomorrow = today.plus(1, DateTimeUnit.DAY)

    private fun vm(
        api: ScheduleApi,
        favoritesApi: FakeFavoritesApi = FakeFavoritesApi(),
        bookingApi: FakeBookingApi = FakeBookingApi(),
    ): ScheduleViewModel = ScheduleViewModel(
        api = api,
        favoritesRepository = FavoritesRepository(favoritesApi),
        bookingApi = bookingApi,
    )

    // ── 5a: state type + classification ───────────────────────────────────

    @Test fun `cold-start network failure classifies as CONNECTION`() = runTest {
        val v = vm(FailingScheduleApi(Exception("network failure")))
        assertEquals(ScheduleUiState.Error(ErrorType.CONNECTION), v.uiState.value)
    }

    @Test fun `cold-start 5xx classifies as SERVER`() = runTest {
        val v = vm(FailingScheduleApi(serverException(500)))
        assertEquals(ScheduleUiState.Error(ErrorType.SERVER), v.uiState.value)
    }

    // ── 5b: SCHED-02, restore Favorites scope on retry ─────────────────────

    @Test fun `retry after a favorites outage restores Favorites scope once the outage clears`() = runTest {
        // Both fetches fail on cold start, exactly as one outage does.
        //
        // NOTE (Fix 3, task-5-fix-report.md): this is an END-TO-END /
        // member-visible-outcome test, not an isolated test of reload()'s own
        // code. Under UnconfinedTestDispatcher, reload()'s call to
        // favoritesRepository.refresh() writes _favorites.value, which
        // resumes the init block's favorites-collector INLINE, before
        // control even returns to reload(). In this exact scenario
        // (filters == ScheduleFilters() at cold start, always true here) the
        // collector's own, differently-guarded scope assignment reaches the
        // same conclusion FIRST. So this test would stay green even if
        // reload()'s own `applyFavoritesScope` call were deleted. See
        // `applyFavoritesScope sets Favorites scope only when the result has
        // entries` below for a test that exercises reload()'s call to the
        // shared helper directly, without that race.
        val favoritesApi = FakeFavoritesApi(
            favoritesResult = FavoritesDto(studios = listOf(favStudio(locationIds = listOf(11, 12)))),
        ).apply { failuresBeforeSuccess = 1 }
        val scheduleApi = FlakyScheduleApi(failuresBeforeSuccess = 1)
        val v = vm(scheduleApi, favoritesApi)
        assertTrue(v.uiState.value is ScheduleUiState.Error)

        v.reload()

        val state = v.success()
        // Without the fix this is AllStudios: the member's scope is silently
        // dropped by the very retry that appears to succeed.
        assertEquals(ScopeMode.Favorites, state.scope)
        assertTrue(state.favoritesKnown)
    }

    @Test fun `applyFavoritesScope sets Favorites scope only when the result has entries`() = runTest {
        // Direct unit test of the helper reload() and init both call (Fix 3):
        // internal (not private) specifically so this test can invoke it
        // without going through favoritesRepository.refresh(), which is what
        // wakes the collector above and races reload()'s own assignment.
        val v = vm(FakeScheduleApi()) // cold start succeeds; no favorites configured
        assertEquals(ScopeMode.AllStudios, v.success().scope)

        v.applyFavoritesScope(FavoritesDto(studios = listOf(favStudio(locationIds = listOf(11)))))
        v.refreshBookings() // force a republish so the mutated scope is observable via uiState
        assertEquals(ScopeMode.Favorites, v.success().scope)

        v.applyFavoritesScope(FavoritesDto()) // empty result must not revert scope back
        v.refreshBookings()
        assertEquals(ScopeMode.Favorites, v.success().scope)
    }

    @Test fun `reload does not touch scope when favorites are already known`() = runTest {
        val favoritesApi = FakeFavoritesApi(
            favoritesResult = FavoritesDto(studios = listOf(favStudio(locationIds = listOf(11, 12)))),
        )
        val v = vm(FakeScheduleApi(), favoritesApi)
        assertEquals(ScopeMode.Favorites, v.success().scope) // has favorites by default

        v.showAllStudios() // the member deliberately switches away
        settleFilters()
        assertEquals(ScopeMode.AllStudios, v.success().scope)

        v.reload()

        // Favorites were already known (non-null) - a member who deliberately
        // chose All Studios must never be overridden by a retry.
        assertEquals(ScopeMode.AllStudios, v.success().scope)
    }

    // ── 5c: ERR-03, the silent uncached day-chip failure ───────────────────

    @Test fun `an uncached day-chip fetch failure surfaces a day error instead of spinning forever`() = runTest {
        val api = FlakyScheduleApi()
        val v = vm(api)
        assertTrue(v.uiState.value is ScheduleUiState.Success)

        api.failNext = true
        val target = v.success().days[3]
        v.selectDay(target)

        val state = v.success()
        assertEquals(target, state.selectedDate)
        assertEquals(ErrorType.CONNECTION, state.dayError)
    }

    @Test fun `retryDay clears the day error and loads the day`() = runTest {
        val api = FlakyScheduleApi()
        val v = vm(api)
        api.failNext = true
        val target = v.success().days[3]
        v.selectDay(target)
        assertEquals(ErrorType.CONNECTION, v.success().dayError)

        v.retryDay()

        val state = v.success()
        assertNull(state.dayError)
        assertTrue(state.dayStates.getValue(target).sessions.isNotEmpty())
    }

    @Test fun `dayError clears when the member switches to a different already-loaded day`() = runTest {
        val api = FlakyScheduleApi()
        val v = vm(api) // cold start loads `today`
        api.failNext = true
        val failingTarget = v.success().days[3]
        v.selectDay(failingTarget)
        assertEquals(ErrorType.CONNECTION, v.success().dayError)

        v.selectDay(today) // switch back to the already-cached cold-start day

        val state = v.success()
        assertEquals(today, state.selectedDate)
        assertNull(state.dayError)
    }

    @Test fun `dayError clears once a settled filter change refetches`() = runTest {
        val api = FlakyScheduleApi()
        val v = vm(api)
        api.failNext = true
        val failingTarget = v.success().days[3]
        v.selectDay(failingTarget)
        assertEquals(ErrorType.CONNECTION, v.success().dayError)

        v.toggleModality("cycle") // any filter mutation feeds the debounced refetch
        settleFilters()

        assertNull(v.success().dayError)
    }

    @Test fun `a stale day fetch failure does not set a day error once the member has switched away`() = runTest {
        val api = FakeScheduleApi()
        val gate = CompletableDeferred<Unit>()
        api.pageResult = { call ->
            if (call.date == tomorrow) {
                gate.await()
                throw Exception("network failure")
            } else {
                pageOf(1)
            }
        }
        val v = vm(api) // cold start on `today` succeeds

        v.selectDay(tomorrow) // launches tomorrow's page-1 fetch; suspends on the gate
        assertNull(v.success().dayError) // still in flight, nothing to show yet

        v.selectDay(today) // the member switches back before tomorrow's fetch resolves
        assertEquals(today, v.success().selectedDate)

        gate.complete(Unit) // tomorrow's fetch now fails, but nobody is looking at it
        runCurrent()

        // The stale failure must not paint an error onto `today`, which the
        // member is actually looking at (and which already loaded fine).
        assertNull(v.success().dayError)
    }

    // ── Fix 1 (task-5-fix-report.md): the unconditional dayError clear at ──
    // the top of refetchForFilters reintroduced ERR-03 for any refetch that
    // is NOT preceded by onFiltersChanged (pull-to-refresh chief among them).

    @Test fun `a refetch failure while a day error is already showing does not leave a bare placeholder`() = runTest {
        val api = FlakyScheduleApi()
        val v = vm(api)
        api.failNext = true
        val failingTarget = v.success().days[3]
        v.selectDay(failingTarget) // uncached day tap fails; dayError set
        assertEquals(ErrorType.CONNECTION, v.success().dayError)

        // The outage persists: pull-to-refresh (bypasses onFiltersChanged
        // entirely) retries and fails again.
        api.failNext = true
        v.refresh()

        val state = v.success()
        // The bug: dayError == null AND the day still isn't loaded, so
        // ScheduleScreen falls through to a bare DotMatrixLoader with nothing
        // in flight and no timeout behind it — indistinguishable from "still
        // loading", forever.
        assertTrue(state.dayStates[failingTarget]?.loaded != true)
        assertEquals(ErrorType.CONNECTION, state.dayError)
    }

    @Test fun `a successful refresh after a day error clears it instead of leaving it stale`() = runTest {
        // Companion to the test above: refetchForFilters's success path must
        // ALSO account for dayError, or a day that failed once and then
        // loads fine on retry is left with a stale non-null dayError.
        // ScheduleScreen checks dayError BEFORE dayLoaded, so this is not a
        // no-op leftover — it would incorrectly show InlineError over
        // perfectly good, freshly-loaded content.
        val api = FlakyScheduleApi()
        val v = vm(api)
        api.failNext = true
        val failingTarget = v.success().days[3]
        v.selectDay(failingTarget)
        assertEquals(ErrorType.CONNECTION, v.success().dayError)

        v.refresh() // outage has cleared: this attempt succeeds

        val state = v.success()
        assertNull(state.dayError)
        assertTrue(state.dayStates.getValue(failingTarget).loaded)
    }

    // ── Fix 2 (task-5-fix-report.md): a stale dayError must not survive ──
    // into the ~250ms filter-debounce window under the PREVIOUS filter set.

    @Test fun `dayError clears immediately on a filter change not just after the debounced refetch settles`() = runTest {
        val api = FlakyScheduleApi()
        val v = vm(api)
        api.failNext = true
        val failingTarget = v.success().days[3]
        v.selectDay(failingTarget)
        assertEquals(ErrorType.CONNECTION, v.success().dayError)

        v.toggleModality("cycle") // filter mutation; the refetch is debounced, NOT yet settled
        // Must already be gone BEFORE settleFilters() — otherwise the
        // InlineError describes the day under the filter set that no longer
        // applies for the whole debounce window.
        assertNull(v.success().dayError)

        settleFilters() // drain the pending refetch so the test leaves no dangling coroutine
    }
}
