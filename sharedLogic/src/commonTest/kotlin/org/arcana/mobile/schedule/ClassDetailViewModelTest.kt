@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.arcana.mobile.schedule

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.arcana.mobile.analytics.fakeTelemetry
import org.arcana.mobile.data.ScheduleOverviewDto
import org.arcana.mobile.data.SchedulePageDto
import org.arcana.mobile.data.ScheduleSessionDto
import org.arcana.mobile.networking.ErrorType
import org.arcana.mobile.networking.ScheduleApi
import kotlin.test.*

/**
 * Class Detail's load / error / refresh / retry paths. Until `fetchClassDetail`
 * moved onto [ScheduleApi] this ViewModel took the concrete client and none of
 * this could be covered — the branch that keeps stale-but-good content on a
 * failed refresh had only ever been checked by driving the screen by hand.
 */
class ClassDetailViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun teardown() { Dispatchers.resetMain() }

    /** Only `fetchClassDetail` matters here; the schedule reads are not this
     *  screen's concern and must not be called. */
    private open class DetailApi(
        private val result: () -> ScheduleSessionDto,
    ) : ScheduleApi {
        var calls = 0
        override suspend fun fetchClassDetail(id: Int): ScheduleSessionDto {
            calls++
            return result()
        }
        override suspend fun fetchSchedule(
            from: LocalDate, to: LocalDate, studioSlugs: List<String>?,
            locationIds: List<Int>?, categorySlugs: List<String>?, availableOnly: Boolean,
        ): List<ScheduleSessionDto> = throw AssertionError("not this screen's endpoint")
        override suspend fun fetchOverview(
            from: LocalDate, to: LocalDate, studioSlugs: List<String>?,
            locationIds: List<Int>?, categorySlugs: List<String>?,
            startTimeGte: String?, startTimeLte: String?, availableOnly: Boolean,
        ): ScheduleOverviewDto = throw AssertionError("not this screen's endpoint")
        override suspend fun fetchSessionsPage(
            date: LocalDate, studioSlugs: List<String>?, locationIds: List<Int>?,
            categorySlugs: List<String>?, startTimeGte: String?, startTimeLte: String?,
            availableOnly: Boolean, cursor: String?,
        ): SchedulePageDto = throw AssertionError("not this screen's endpoint")
    }

    private fun vm(api: ScheduleApi, sessionId: Int = 482) =
        ClassDetailViewModel(api, sessionId, fakeTelemetry().first)

    // --- load ---------------------------------------------------------------

    @Test fun `init loads the class and reports a view`() = runTest {
        val (telemetry, analytics, _) = fakeTelemetry()
        val api = DetailApi { detailSession(id = 482) }
        val vm = ClassDetailViewModel(api, 482, telemetry)

        assertEquals(ClassDetailUiState.Success(detailSession(id = 482)), vm.uiState.value)
        assertEquals(1, analytics.all("class_viewed").size)
    }

    @Test fun `a cold failure lands on Error carrying the classified type`() = runTest {
        val (telemetry, analytics, _) = fakeTelemetry()
        val api = DetailApi { throw serverException(500) }
        val vm = ClassDetailViewModel(api, 482, telemetry)

        assertEquals(ClassDetailUiState.Error(ErrorType.SERVER), vm.uiState.value)
        assertEquals(1, analytics.all("class_view_failed").size)
        assertEquals(0, analytics.all("class_viewed").size)
    }

    /** CONNECTION vs SERVER is the whole point of the error-states work; a
     *  transport failure carries no HTTP status and must not read as a 5xx. */
    @Test fun `a transport failure classifies as CONNECTION`() = runTest {
        val vm = vm(DetailApi { throw kotlinx.io.IOException("socket closed") })
        assertEquals(ClassDetailUiState.Error(ErrorType.CONNECTION), vm.uiState.value)
    }

    // --- refresh ------------------------------------------------------------

    /** The stale-but-good rule: a failed pull-to-refresh must never replace
     *  content the member is already reading with a full-screen error. */
    @Test fun `a failed refresh keeps the content already on screen`() = runTest {
        var fail = false
        val api = DetailApi { if (fail) throw serverException(500) else detailSession() }
        val vm = vm(api)
        assertTrue(vm.uiState.value is ClassDetailUiState.Success)

        fail = true
        vm.refresh()

        assertTrue(vm.uiState.value is ClassDetailUiState.Success, "a refresh blip must not blank the screen")
        assertFalse(vm.isRefreshing.value)
    }

    /** Pull-to-refresh is not a class view — counting it would inflate the metric
     *  every time someone tugs the screen. */
    @Test fun `refresh does not report a second class view`() = runTest {
        val (telemetry, analytics, _) = fakeTelemetry()
        val vm = ClassDetailViewModel(DetailApi { detailSession() }, 482, telemetry)
        assertEquals(1, analytics.all("class_viewed").size)

        vm.refresh()

        assertEquals(1, analytics.all("class_viewed").size)
    }

    // --- retry --------------------------------------------------------------

    /** retry() deliberately does NOT drop to Loading: otherwise a failing retry
     *  flashes the shimmer and lands back on the same error. */
    @Test fun `retry leaves the error on screen while it runs`() = runTest {
        val sched = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(sched)
        try {
            val gate = CompletableDeferred<Unit>()
            var first = true
            val api = object : DetailApi({ detailSession() }) {
                override suspend fun fetchClassDetail(id: Int): ScheduleSessionDto {
                    if (first) { first = false; throw serverException(500) }
                    gate.await()
                    return detailSession()
                }
            }
            val vm = ClassDetailViewModel(api, 482, fakeTelemetry().first)
            advanceUntilIdle()
            assertEquals(ClassDetailUiState.Error(ErrorType.SERVER), vm.uiState.value)

            vm.retry()
            advanceUntilIdle()
            assertTrue(vm.retrying.value)
            assertEquals(
                ClassDetailUiState.Error(ErrorType.SERVER),
                vm.uiState.value,
                "the error must stay put — the retry button carries the progress",
            )

            gate.complete(Unit)
            advanceUntilIdle()
            assertTrue(vm.uiState.value is ClassDetailUiState.Success)
            assertFalse(vm.retrying.value)
        } finally {
            Dispatchers.setMain(dispatcher)
        }
    }

    @Test fun `retry is guarded against a double tap`() = runTest {
        val sched = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(sched)
        try {
            val api = DetailApi { throw serverException(500) }
            val vm = ClassDetailViewModel(api, 482, fakeTelemetry().first)
            advanceUntilIdle()
            val afterLoad = api.calls

            vm.retry()
            vm.retry()
            advanceUntilIdle()

            assertEquals(afterLoad + 1, api.calls, "the second tap must hit the in-flight guard")
        } finally {
            Dispatchers.setMain(dispatcher)
        }
    }

    // --- reload -------------------------------------------------------------

    /** The shimmer is what distinguishes `reload` from `refresh`. Gated rather
     *  than sampled: without a real suspension the conflated StateFlow collapses
     *  Loading into the Success that follows it. */
    @Test fun `reload flashes the shimmer while refresh does not`() = runTest {
        var gate: CompletableDeferred<Unit>? = null
        val api = object : DetailApi({ detailSession() }) {
            override suspend fun fetchClassDetail(id: Int): ScheduleSessionDto {
                gate?.await()
                return detailSession()
            }
        }
        val vm = ClassDetailViewModel(api, 482, fakeTelemetry().first)
        assertTrue(vm.uiState.value is ClassDetailUiState.Success)

        gate = CompletableDeferred()
        vm.reload()
        assertEquals(ClassDetailUiState.Loading, vm.uiState.value)
        gate.complete(Unit)
        assertTrue(vm.uiState.value is ClassDetailUiState.Success)

        // refresh keeps the content visible and drives the spinner instead.
        gate = CompletableDeferred()
        vm.refresh()
        assertTrue(vm.uiState.value is ClassDetailUiState.Success, "refresh must not blank the screen")
        assertTrue(vm.isRefreshing.value)
        gate.complete(Unit)
        assertFalse(vm.isRefreshing.value)
    }
}
