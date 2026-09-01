@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.arcana.mobile.search

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import org.arcana.mobile.analytics.fakeTelemetry
import org.arcana.mobile.data.SchedulePageDto
import org.arcana.mobile.data.SearchEntitiesDto
import org.arcana.mobile.data.SearchInstructorDto
import org.arcana.mobile.data.SearchLocationDto
import org.arcana.mobile.data.SearchStudioDto
import org.arcana.mobile.networking.ErrorType
import org.arcana.mobile.networking.SearchApi
import org.arcana.mobile.schedule.ScheduleViewModel
import org.arcana.mobile.schedule.pageOf
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

private class FakeSearchApi : SearchApi {
    data class SessionsCall(
        val from: LocalDate,
        val to: LocalDate,
        val query: String?,
        val instructor: String?,
        val studioSlug: String?,
        val locationId: Int?,
        val cursor: String?,
    )

    val entitiesCalls = mutableListOf<String>()
    val sessionsCalls = mutableListOf<SessionsCall>()
    var entitiesResult: (String) -> SearchEntitiesDto = { SearchEntitiesDto() }
    var sessionsResult: (SessionsCall) -> SchedulePageDto = { pageOf(1) }
    /** When set, searchSessions suspends until completed — for in-flight tests. */
    var sessionsGate: CompletableDeferred<Unit>? = null

    override suspend fun searchEntities(query: String): SearchEntitiesDto {
        entitiesCalls += query
        return entitiesResult(query)
    }

    override suspend fun searchSessions(
        from: LocalDate,
        to: LocalDate,
        query: String?,
        instructor: String?,
        studioSlug: String?,
        locationId: Int?,
        cursor: String?,
    ): SchedulePageDto {
        val call = SessionsCall(from, to, query, instructor, studioSlug, locationId, cursor)
        sessionsCalls += call
        sessionsGate?.await()
        return sessionsResult(call)
    }
}

private fun inMemoryRecents(seed: List<String> = emptyList()): RecentSearches {
    var stored: String? = seed.joinToString("\n").ifBlank { null }
    return RecentSearches(
        loadRaw = { stored },
        saveRaw = { stored = it },
        deleteRaw = { stored = null },
    )
}

class SearchViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun teardown() { Dispatchers.resetMain() }

    private fun vm(
        api: FakeSearchApi,
        recents: RecentSearches = inMemoryRecents(),
    ) = SearchViewModel(api = api, recentSearches = recents)

    private fun results(vm: SearchViewModel): SearchUiState.Results =
        assertIs<SearchUiState.Results>(vm.uiState.value)

    @Test fun `starts idle with stored recents and no api calls`() = runTest {
        val api = FakeSearchApi()
        val vm = vm(api, inMemoryRecents(listOf("pilates", "sarah")))

        assertEquals(SearchUiState.Idle(listOf("pilates", "sarah")), vm.uiState.value)
        advanceUntilIdle()
        assertTrue(api.sessionsCalls.isEmpty())
        assertTrue(api.entitiesCalls.isEmpty())
    }

    @Test fun `query under two chars stays idle and never fetches`() = runTest {
        val api = FakeSearchApi()
        val vm = vm(api)

        vm.onQueryChanged("y")
        advanceTimeBy(SearchViewModel.SEARCH_DEBOUNCE_MS + 1)
        advanceUntilIdle()

        assertIs<SearchUiState.Idle>(vm.uiState.value)
        assertTrue(api.sessionsCalls.isEmpty())
    }

    @Test fun `debounce collapses rapid keystrokes into one fetch of the latest query`() = runTest {
        val api = FakeSearchApi()
        val vm = vm(api)

        vm.onQueryChanged("yo")
        advanceTimeBy(100)
        vm.onQueryChanged("yog")
        advanceTimeBy(100)
        vm.onQueryChanged("yoga")
        advanceTimeBy(SearchViewModel.SEARCH_DEBOUNCE_MS + 1)
        advanceUntilIdle()

        assertEquals(listOf("yoga"), api.sessionsCalls.map { it.query })
        assertEquals(listOf("yoga"), api.entitiesCalls)
    }

    @Test fun `results carry sessions plus entity chips over the full window`() = runTest {
        val api = FakeSearchApi()
        api.entitiesResult = {
            SearchEntitiesDto(
                studios = listOf(SearchStudioDto(slug = "barrys", name = "Barry's")),
                instructors = listOf(SearchInstructorDto("Sarah Chen")),
            )
        }
        api.sessionsResult = { pageOf(1, 2, nextCursor = "c1") }
        val vm = vm(api)

        vm.onQueryChanged("bar")
        advanceTimeBy(SearchViewModel.SEARCH_DEBOUNCE_MS + 1)
        advanceUntilIdle()

        val state = results(vm)
        assertEquals(listOf(1, 2), state.sessions.map { it.id })
        assertEquals("c1", state.nextCursor)
        assertEquals(
            listOf<SearchScope>(
                SearchScope.Studio(slug = "barrys", label = "Barry's"),
                SearchScope.Instructor("Sarah Chen"),
            ),
            state.chips,
        )
        assertTrue(state.activeScopes.isEmpty())

        val call = api.sessionsCalls.single()
        val today = Clock.System.todayIn(ScheduleViewModel.ScheduleTimeZone)
        assertEquals(today, call.from)
        assertEquals(today.plus(ScheduleViewModel.WINDOW_DAYS - 1, DateTimeUnit.DAY), call.to)
        assertNull(call.cursor)
    }

    @Test fun `empty sessions and chips become NoResults`() = runTest {
        val api = FakeSearchApi()
        api.sessionsResult = { pageOf() }
        val vm = vm(api)

        vm.onQueryChanged("zzz")
        advanceTimeBy(SearchViewModel.SEARCH_DEBOUNCE_MS + 1)
        advanceUntilIdle()

        assertEquals(SearchUiState.NoResults("zzz", activeScopes = emptyList()), vm.uiState.value)
    }

    @Test fun `api failure maps to typed error and preserves the typed query`() = runTest {
        val api = FakeSearchApi()
        api.sessionsResult = { throw RuntimeException("boom") }
        val vm = vm(api)

        vm.onQueryChanged("yoga")
        advanceTimeBy(SearchViewModel.SEARCH_DEBOUNCE_MS + 1)
        advanceUntilIdle()

        assertEquals(SearchUiState.Error(ErrorType.CONNECTION), vm.uiState.value)
        assertEquals("yoga", vm.query.value)
    }

    @Test fun `instructor chip scopes the refetch and keeps suggesting entities`() = runTest {
        val api = FakeSearchApi()
        api.entitiesResult = {
            SearchEntitiesDto(instructors = listOf(SearchInstructorDto("Sarah Chen")))
        }
        val vm = vm(api)

        vm.onQueryChanged("sarah")
        advanceTimeBy(SearchViewModel.SEARCH_DEBOUNCE_MS + 1)
        advanceUntilIdle()
        assertEquals(1, api.entitiesCalls.size)

        vm.onScope(SearchScope.Instructor("Sarah Chen"))
        advanceTimeBy(SearchViewModel.SEARCH_DEBOUNCE_MS + 1)
        advanceUntilIdle()

        assertEquals(2, api.sessionsCalls.size)
        assertEquals("Sarah Chen", api.sessionsCalls.last().instructor)
        // Entities still ride along while scoped (chips must combine)...
        assertEquals(2, api.entitiesCalls.size)
        val state = results(vm)
        assertEquals(listOf<SearchScope>(SearchScope.Instructor("Sarah Chen")), state.activeScopes)
        // ...but an already-active scope is not re-suggested.
        assertTrue(state.chips.isEmpty())
    }

    @Test fun `scopes combine across types and removal restores the wider set`() = runTest {
        val api = FakeSearchApi()
        val vm = vm(api)

        vm.onQueryChanged("barrys")
        advanceTimeBy(SearchViewModel.SEARCH_DEBOUNCE_MS + 1)
        vm.onScope(SearchScope.Studio(slug = "barrys", label = "Barry's"))
        advanceTimeBy(SearchViewModel.SEARCH_DEBOUNCE_MS + 1)
        vm.onQueryChanged("evan")
        advanceTimeBy(SearchViewModel.SEARCH_DEBOUNCE_MS + 1)
        vm.onScope(SearchScope.Instructor("Evan P."))
        advanceTimeBy(SearchViewModel.SEARCH_DEBOUNCE_MS + 1)
        advanceUntilIdle()

        val combined = api.sessionsCalls.last()
        assertEquals("barrys", combined.studioSlug)
        assertEquals("Evan P.", combined.instructor)
        assertEquals("evan", combined.query)
        assertEquals(2, results(vm).activeScopes.size)

        vm.onRemoveScope(SearchScope.Instructor("Evan P."))
        advanceTimeBy(SearchViewModel.SEARCH_DEBOUNCE_MS + 1)
        advanceUntilIdle()

        val after = api.sessionsCalls.last()
        assertEquals("barrys", after.studioSlug)
        assertNull(after.instructor)
    }

    @Test fun `a second scope of the same type replaces the first`() = runTest {
        val api = FakeSearchApi()
        val vm = vm(api)

        vm.onQueryChanged("studio")
        advanceTimeBy(SearchViewModel.SEARCH_DEBOUNCE_MS + 1)
        vm.onScope(SearchScope.Studio(slug = "barrys", label = "Barry's"))
        advanceTimeBy(SearchViewModel.SEARCH_DEBOUNCE_MS + 1)
        vm.onScope(SearchScope.Studio(slug = "slt", label = "SLT"))
        advanceTimeBy(SearchViewModel.SEARCH_DEBOUNCE_MS + 1)
        advanceUntilIdle()

        assertEquals("slt", api.sessionsCalls.last().studioSlug)
        assertEquals(1, results(vm).activeScopes.size)
    }

    @Test fun `location chip scopes by location id distinct from the studio scope`() = runTest {
        val api = FakeSearchApi()
        api.entitiesResult = {
            SearchEntitiesDto(
                studios = listOf(SearchStudioDto(slug = "slt", name = "SLT")),
                locations = listOf(SearchLocationDto(id = 41, name = "Flatiron", studioName = "SLT")),
            )
        }
        val vm = vm(api)

        vm.onQueryChanged("flatiron")
        advanceTimeBy(SearchViewModel.SEARCH_DEBOUNCE_MS + 1)
        advanceUntilIdle()

        val locationChip = results(vm).chips.filterIsInstance<SearchScope.Location>().single()
        assertEquals("SLT Flatiron", locationChip.label)

        vm.onScope(locationChip)
        advanceTimeBy(SearchViewModel.SEARCH_DEBOUNCE_MS + 1)
        advanceUntilIdle()

        val call = api.sessionsCalls.last()
        assertEquals(41, call.locationId)
        assertNull(call.studioSlug)
        assertEquals(listOf<SearchScope>(locationChip), results(vm).activeScopes)
    }

    @Test fun `studio chip passes the slug scope`() = runTest {
        val api = FakeSearchApi()
        val vm = vm(api)

        vm.onQueryChanged("barrys")
        advanceTimeBy(SearchViewModel.SEARCH_DEBOUNCE_MS + 1)
        vm.onScope(SearchScope.Studio(slug = "barrys", label = "Barry's"))
        advanceTimeBy(SearchViewModel.SEARCH_DEBOUNCE_MS + 1)
        advanceUntilIdle()

        assertEquals("barrys", api.sessionsCalls.last().studioSlug)
    }

    @Test fun `clearing both query and scope returns to idle with fresh recents`() = runTest {
        val api = FakeSearchApi()
        val recents = inMemoryRecents()
        val vm = vm(api, recents)

        vm.onQueryChanged("yoga")
        advanceTimeBy(SearchViewModel.SEARCH_DEBOUNCE_MS + 1)
        advanceUntilIdle()
        vm.onQueryChanged("")
        advanceTimeBy(SearchViewModel.SEARCH_DEBOUNCE_MS + 1)
        advanceUntilIdle()

        val idle = assertIs<SearchUiState.Idle>(vm.uiState.value)
        assertEquals(listOf("yoga"), idle.recents)
    }

    @Test fun `clearing recents empties the idle list and the store`() = runTest {
        val api = FakeSearchApi()
        val recents = inMemoryRecents(listOf("pilates", "sarah"))
        val vm = vm(api, recents)

        vm.onClearRecents()

        assertEquals(SearchUiState.Idle(emptyList()), vm.uiState.value)
        assertEquals(emptyList(), recents.all())
    }

    @Test fun `successful search records the query in recents deduped and capped`() = runTest {
        val api = FakeSearchApi()
        val recents = inMemoryRecents()
        val vm = vm(api, recents)

        for (query in listOf("q-one", "q-two", "q-one", "q-3", "q-4", "q-5", "q-6", "q-7", "q-8", "q-9")) {
            vm.onQueryChanged(query)
            advanceTimeBy(SearchViewModel.SEARCH_DEBOUNCE_MS + 1)
            advanceUntilIdle()
        }

        val all = recents.all()
        assertEquals(RecentSearches.MAX_ENTRIES, all.size)
        assertEquals("q-9", all.first())
        assertEquals(1, all.count { it == "q-one" })
    }

    @Test fun `loadMore appends the next page using the cursor`() = runTest {
        val api = FakeSearchApi()
        api.sessionsResult = { call ->
            if (call.cursor == null) pageOf(1, 2, nextCursor = "c1") else pageOf(3)
        }
        val vm = vm(api)

        vm.onQueryChanged("yoga")
        advanceTimeBy(SearchViewModel.SEARCH_DEBOUNCE_MS + 1)
        advanceUntilIdle()
        vm.loadMore()
        advanceUntilIdle()

        val state = results(vm)
        assertEquals(listOf(1, 2, 3), state.sessions.map { it.id })
        assertNull(state.nextCursor)
        assertEquals("c1", api.sessionsCalls.last().cursor)
    }

    @Test fun `loadMore without a cursor is a no-op`() = runTest {
        val api = FakeSearchApi()
        api.sessionsResult = { pageOf(1) }
        val vm = vm(api)

        vm.onQueryChanged("yoga")
        advanceTimeBy(SearchViewModel.SEARCH_DEBOUNCE_MS + 1)
        advanceUntilIdle()
        vm.loadMore()
        advanceUntilIdle()

        assertEquals(1, api.sessionsCalls.size)
    }

    @Test fun `stale loadMore result is discarded after the query changes`() = runTest {
        val api = FakeSearchApi()
        api.sessionsResult = { call ->
            if (call.cursor == null) pageOf(1, nextCursor = "c1") else pageOf(99)
        }
        val vm = vm(api)

        vm.onQueryChanged("yoga")
        advanceTimeBy(SearchViewModel.SEARCH_DEBOUNCE_MS + 1)
        advanceUntilIdle()

        val gate = CompletableDeferred<Unit>()
        api.sessionsGate = gate
        vm.loadMore()

        // A new query lands (and completes) while the page append hangs.
        api.sessionsGate = null
        vm.onQueryChanged("boxing")
        advanceTimeBy(SearchViewModel.SEARCH_DEBOUNCE_MS + 1)
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()

        val state = results(vm)
        assertTrue(99 !in state.sessions.map { it.id })
    }

    @Test fun `retry after an error refetches the same query`() = runTest {
        val api = FakeSearchApi()
        var fail = true
        api.sessionsResult = { if (fail) throw RuntimeException("boom") else pageOf(1) }
        val vm = vm(api)

        vm.onQueryChanged("yoga")
        advanceTimeBy(SearchViewModel.SEARCH_DEBOUNCE_MS + 1)
        advanceUntilIdle()
        assertIs<SearchUiState.Error>(vm.uiState.value)

        fail = false
        vm.retry()
        advanceUntilIdle()

        assertEquals(listOf(1), results(vm).sessions.map { it.id })
        assertEquals("yoga", api.sessionsCalls.last().query)
    }

    @Test fun `search telemetry taxonomy is locked`() = runTest {
        val (telemetry, analytics, _) = fakeTelemetry()
        val api = FakeSearchApi()
        api.sessionsResult = { pageOf(1, 2) }
        val vm = SearchViewModel(api = api, recentSearches = inMemoryRecents(), telemetry = telemetry)

        vm.onQueryChanged("sarah")
        advanceTimeBy(SearchViewModel.SEARCH_DEBOUNCE_MS + 1)
        advanceUntilIdle()
        vm.onScope(SearchScope.Instructor("Sarah Chen"))
        advanceTimeBy(SearchViewModel.SEARCH_DEBOUNCE_MS + 1)
        advanceUntilIdle()
        vm.onResultTapped(position = 3)

        val names = analytics.events.map { it.name }
        assertEquals(
            listOf("search_opened", "search_performed", "search_scoped", "search_performed", "search_result_tapped"),
            names,
        )
        val first = analytics.events[1]
        assertEquals("sarah", first.properties["query"])
        assertEquals(2, first.properties["result_count"])
        assertEquals(false, first.properties["scoped"])
        val scopedEvent = analytics.events[2]
        assertEquals("instructor", scopedEvent.properties["entity_type"])
        val tap = analytics.events.last()
        assertEquals(3, tap.properties["position"])
        assertEquals(true, tap.properties["scoped"])
    }
}
