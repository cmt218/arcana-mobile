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
import org.arcana.mobile.data.FavoritesDto
import org.arcana.mobile.favorites.FavoritesRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/** Overview + cursor-paging behavior of the Phase-2 ScheduleViewModel:
 *  per-day page caches, loadMore guards, the debounced filter pipeline,
 *  the generation guard, and the error semantics. */
class ScheduleViewModelPagingTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun teardown() { Dispatchers.resetMain() }

    private val today = Clock.System.todayIn(ScheduleViewModel.ScheduleTimeZone)
    private val tomorrow = today.plus(1, DateTimeUnit.DAY)

    private fun vm(
        scheduleApi: FakeScheduleApi,
        favoritesApi: FakeFavoritesApi = FakeFavoritesApi(),
        bookingApi: FakeBookingApi = FakeBookingApi(),
    ): ScheduleViewModel = ScheduleViewModel(
        api = scheduleApi,
        favoritesRepository = FavoritesRepository(favoritesApi),
        bookingApi = bookingApi,
    )

    // ── 1. Init ────────────────────────────────────────────────────────────

    @Test fun `init - exactly one overview and one page-1 fetch for today`() = runTest {
        val api = FakeScheduleApi()
        api.overviewResult = { overviewOf(overviewStudio("solidcore", "SolidCore")) }
        api.pageResult = { pageOf(1, 2, nextCursor = "c1") }
        val vm = vm(api)

        assertEquals(1, api.overviewCalls.size)
        val overviewCall = api.overviewCalls.single()
        assertEquals(today, overviewCall.from)
        assertEquals(today.plus(13, DateTimeUnit.DAY), overviewCall.to)

        assertEquals(1, api.pageCalls.size)
        val pageCall = api.pageCalls.single()
        assertEquals(today, pageCall.date)
        assertNull(pageCall.cursor)

        val state = vm.success()
        assertEquals(today, state.selectedDate)
        assertEquals(ScheduleViewModel.WINDOW_DAYS, state.days.size)
        assertEquals(today, state.days.first())
        assertEquals(listOf("solidcore"), state.knownStudios.map { it.slug })
        val dayState = state.dayStates.getValue(today)
        assertTrue(dayState.loaded)
        assertEquals(listOf(1, 2), dayState.sessions.map { it.id })
        assertEquals("c1", dayState.nextCursor)
        assertFalse(state.refreshingFilters)
    }

    // ── 2. Day selection + cache ───────────────────────────────────────────

    @Test fun `selectDay fetches an uncached day once and serves it from cache after`() = runTest {
        val api = FakeScheduleApi()
        api.pageResult = { call -> if (call.date == today) pageOf(1) else pageOf(7, 8) }
        val vm = vm(api)
        assertEquals(1, api.pageCalls.size)

        vm.selectDay(tomorrow)

        assertEquals(2, api.pageCalls.size)
        assertEquals(tomorrow, api.pageCalls[1].date)
        assertNull(api.pageCalls[1].cursor)
        val state = vm.success()
        assertEquals(tomorrow, state.selectedDate)
        assertEquals(listOf(7, 8), state.dayStates.getValue(tomorrow).sessions.map { it.id })

        vm.selectDay(today)
        vm.selectDay(tomorrow)

        assertEquals(2, api.pageCalls.size) // both days cached — zero new fetches
        assertEquals(1, api.overviewCalls.size) // a day tap never refetches the overview
        assertEquals(tomorrow, vm.success().selectedDate)
    }

    @Test fun `re-tapping a day whose page-1 fetch failed retries it`() = runTest {
        val api = FakeScheduleApi()
        var failTomorrow = true
        api.pageResult = { call ->
            if (call.date == tomorrow && failTomorrow) throw RuntimeException("transient") else pageOf(5)
        }
        val vm = vm(api)

        vm.selectDay(tomorrow) // page-1 fails; content stays, day stays unloaded
        assertEquals(2, api.pageCalls.size)
        assertTrue(vm.success().dayStates[tomorrow]?.loaded != true)

        failTomorrow = false
        vm.selectDay(tomorrow) // same-day re-tap is the natural retry gesture

        assertEquals(3, api.pageCalls.size)
        assertEquals(listOf(5), vm.success().dayStates.getValue(tomorrow).sessions.map { it.id })
    }

    // ── 3. loadMore ────────────────────────────────────────────────────────

    @Test fun `loadMore appends the page, replaces the cursor, and stops at null`() = runTest {
        val api = FakeScheduleApi()
        api.pageResult = { call ->
            if (call.cursor == null) pageOf(1, 2, nextCursor = "c1") else pageOf(3)
        }
        val vm = vm(api)

        vm.loadMore()

        assertEquals(2, api.pageCalls.size)
        assertEquals("c1", api.pageCalls[1].cursor)
        val day = vm.success().dayStates.getValue(today)
        assertEquals(listOf(1, 2, 3), day.sessions.map { it.id })
        assertNull(day.nextCursor)
        assertFalse(day.loadingMore)

        vm.loadMore() // exhausted — nextCursor is null

        assertEquals(2, api.pageCalls.size)
    }

    @Test fun `loadMore while a page is already in flight is a no-op`() = runTest {
        val api = FakeScheduleApi()
        val gate = CompletableDeferred<Unit>()
        api.pageResult = { call ->
            if (call.cursor == null) {
                pageOf(1, nextCursor = "c1")
            } else {
                gate.await()
                pageOf(2)
            }
        }
        val vm = vm(api)

        vm.loadMore() // suspends on the gate
        assertTrue(vm.success().dayStates.getValue(today).loadingMore)
        vm.loadMore() // second call while the first is in flight

        assertEquals(2, api.pageCalls.size) // init page-1 + ONE cursored fetch

        gate.complete(Unit)
        runCurrent()

        val day = vm.success().dayStates.getValue(today)
        assertEquals(listOf(1, 2), day.sessions.map { it.id })
        assertFalse(day.loadingMore)
    }

    // ── 4. Generation guard ────────────────────────────────────────────────

    @Test fun `stale loadMore result is dropped when filters change mid-flight`() = runTest {
        val api = FakeScheduleApi()
        val gate = CompletableDeferred<Unit>()
        api.pageResult = { call ->
            if (call.cursor != null) {
                gate.await()
                pageOf(99)
            } else {
                pageOf(1, 2, nextCursor = "c1")
            }
        }
        val vm = vm(api)

        vm.loadMore() // suspends on the gate
        vm.toggleAvailableOnly()
        settleFilters() // refetch lands: generation bumped, fresh page 1

        gate.complete(Unit) // the stale loadMore finally returns
        runCurrent()

        val day = vm.success().dayStates.getValue(today)
        assertEquals(listOf(1, 2), day.sessions.map { it.id }) // 99 was dropped
        assertEquals("c1", day.nextCursor)
        assertFalse(day.loadingMore)
    }

    // ── 5. Debounced filter pipeline ───────────────────────────────────────

    @Test fun `rapid filter toggles coalesce into one refetch and drop other day caches`() = runTest {
        val api = FakeScheduleApi()
        api.pageResult = { pageOf(1) }
        val vm = vm(api)
        vm.selectDay(tomorrow) // cache a second day
        assertEquals(2, api.pageCalls.size)
        assertEquals(setOf(today, tomorrow), vm.success().dayStates.keys)

        vm.toggleAvailableOnly()
        vm.toggleAvailableOnly()
        vm.toggleAvailableOnly() // settled value: availableOnly = true

        // Inside the debounce window: stale content stays mounted + dimmed,
        // nothing has hit the network yet.
        assertTrue(vm.success().refreshingFilters)
        assertEquals(1, api.overviewCalls.size)
        assertEquals(2, api.pageCalls.size)

        settleFilters()

        assertEquals(2, api.overviewCalls.size) // ONE settled overview refetch
        assertEquals(3, api.pageCalls.size) // ONE settled page-1 refetch
        assertTrue(api.overviewCalls[1].availableOnly)
        assertTrue(api.pageCalls[2].availableOnly)
        assertEquals(tomorrow, api.pageCalls[2].date) // page 1 of the SELECTED day
        assertNull(api.pageCalls[2].cursor)

        val state = vm.success()
        assertFalse(state.refreshingFilters)
        assertTrue(state.filters.availableOnly)
        assertEquals(setOf(tomorrow), state.dayStates.keys) // other day caches dropped
    }

    // ── 6. availableOnly through the pipeline ──────────────────────────────

    @Test fun `availableOnly flows through the pipeline onto both fetches`() = runTest {
        val api = FakeScheduleApi()
        val vm = vm(api)
        assertFalse(api.overviewCalls.single().availableOnly)
        assertFalse(api.pageCalls.single().availableOnly)

        vm.toggleAvailableOnly()
        settleFilters()

        assertTrue(api.overviewCalls[1].availableOnly)
        assertTrue(api.pageCalls[1].availableOnly)
        assertTrue(vm.success().filters.availableOnly)

        vm.toggleAvailableOnly()
        settleFilters()

        assertFalse(api.overviewCalls[2].availableOnly)
        assertFalse(api.pageCalls[2].availableOnly)
    }

    // ── 7. Favorites through the paged pipeline ────────────────────────────

    @Test fun `favorites scope reaches the overview, page-1, and loadMore fetches`() = runTest {
        val api = FakeScheduleApi()
        api.pageResult = { call ->
            if (call.cursor == null) pageOf(1, nextCursor = "c1") else pageOf(2)
        }
        val favoritesApi = FakeFavoritesApi(
            favoritesResult = FavoritesDto(studios = listOf(favStudio(locationIds = listOf(11, 12)))),
        )
        val vm = vm(api, favoritesApi)

        assertEquals(listOf(11, 12), api.overviewCalls.single().locationIds)
        assertEquals(listOf(11, 12), api.pageCalls[0].locationIds)

        vm.loadMore()

        assertEquals(listOf(11, 12), api.pageCalls[1].locationIds)
        assertTrue(vm.success().favoritesMode)
    }

    // ── 8. Failure semantics ───────────────────────────────────────────────

    @Test fun `cold-start failure surfaces the Error state`() = runTest {
        val api = FakeScheduleApi()
        api.overviewResult = { throw RuntimeException("boom") }
        val vm = vm(api)

        assertTrue(vm.uiState.value is ScheduleUiState.Error)
    }

    @Test fun `refetch failure with content on screen keeps the content`() = runTest {
        val api = FakeScheduleApi()
        api.pageResult = { pageOf(1, 2) }
        val vm = vm(api)
        assertEquals(listOf(1, 2), vm.success().dayStates.getValue(today).sessions.map { it.id })

        api.overviewResult = { throw RuntimeException("transient") }
        vm.toggleAvailableOnly()
        settleFilters()

        val state = vm.success() // still Success — content kept, no Error flash
        assertEquals(listOf(1, 2), state.dayStates.getValue(today).sessions.map { it.id })
        assertFalse(state.refreshingFilters)
        assertTrue(state.filters.availableOnly)
    }

    // ── Pull-to-refresh ────────────────────────────────────────────────────

    @Test fun `refresh refetches overview plus selected-day page 1 and drops other day caches`() = runTest {
        val api = FakeScheduleApi()
        api.pageResult = { pageOf(1) }
        val vm = vm(api)
        vm.selectDay(tomorrow)
        assertEquals(2, api.pageCalls.size)

        vm.refresh()

        assertEquals(2, api.overviewCalls.size)
        assertEquals(3, api.pageCalls.size)
        assertEquals(tomorrow, api.pageCalls[2].date)
        assertNull(api.pageCalls[2].cursor)
        assertEquals(setOf(tomorrow), vm.success().dayStates.keys)
        assertFalse(vm.isRefreshing.value)
    }

    // ── 9. Staleness across the refresh/pipeline boundary ─────────────────

    @Test fun `stale pull-to-refresh result is dropped when a filter refetch settles first`() = runTest {
        val api = FakeScheduleApi()
        api.pageResult = { pageOf(1) }
        val vm = vm(api)
        assertEquals(1, api.overviewCalls.size)

        // Gate the pull-to-refresh's overview so its refetch hangs in flight…
        val gate = CompletableDeferred<Unit>()
        api.overviewResult = { gate.await(); overviewOf() }
        api.pageResult = { pageOf(99) }
        vm.refresh()
        runCurrent()

        // …while a chip tap runs a NEWER refetch to completion.
        api.overviewResult = { overviewOf() }
        api.pageResult = { pageOf(42) }
        vm.toggleAvailableOnly()
        settleFilters()
        assertEquals(listOf(42), vm.success().dayStates.getValue(today).sessions.map { it.id })

        // The stale refresh finally completes — its results must be dropped,
        // not overwrite the newer filter state.
        gate.complete(Unit)
        runCurrent()
        val state = vm.success()
        assertEquals(listOf(42), state.dayStates.getValue(today).sessions.map { it.id })
    }

    // ── 10. Favorites-exit filter carry-over ──────────────────────────────

    @Test fun `toggleStudio exiting favorites mode preserves availableOnly`() = runTest {
        val api = FakeScheduleApi()
        val favoritesApi = FakeFavoritesApi(
            favoritesResult = FavoritesDto(studios = listOf(favStudio(locationIds = listOf(11)))),
        )
        val vm = vm(api, favoritesApi)
        vm.toggleAvailableOnly()
        settleFilters()
        assertTrue(api.overviewCalls.last().availableOnly)
        assertEquals(listOf(11), api.overviewCalls.last().locationIds)

        vm.toggleStudio("barrys")
        settleFilters()

        val call = api.overviewCalls.last()
        assertTrue(call.availableOnly) // carried through the favorites exit
        assertEquals(listOf("barrys"), call.studioSlugs)
        assertNull(call.locationIds)
        val state = vm.success()
        assertFalse(state.favoritesMode)
        assertTrue(state.filters.availableOnly)
    }
}
