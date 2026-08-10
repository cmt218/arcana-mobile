@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.arcana.mobile.analytics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import org.arcana.mobile.favorites.FavoritesRepository
import org.arcana.mobile.schedule.FakeBookingApi
import org.arcana.mobile.schedule.FakeFavoritesApi
import org.arcana.mobile.schedule.FakeScheduleApi
import org.arcana.mobile.schedule.ScheduleViewModel
import org.arcana.mobile.schedule.overviewOf
import org.arcana.mobile.schedule.overviewStudio
import org.arcana.mobile.schedule.pageOf
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Locks the schedule-usage taxonomy (day changes + filter changes). */
class ScheduleTelemetryTest {
    @BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private fun vm(telemetry: org.arcana.mobile.analytics.Telemetry) = ScheduleViewModel(
        api = FakeScheduleApi(),
        favoritesRepository = FavoritesRepository(FakeFavoritesApi()),
        bookingApi = FakeBookingApi(),
        telemetry = telemetry,
    )

    @Test fun `changing day fires schedule_day_changed with method and direction`() = runTest {
        val (telemetry, analytics, _) = fakeTelemetry()
        val v = vm(telemetry)
        advanceUntilIdle()
        val today = Clock.System.todayIn(ScheduleViewModel.ScheduleTimeZone)
        v.selectDay(today.plus(2, DateTimeUnit.DAY))

        val ev = analytics.first("schedule_day_changed")
        assertTrue(ev != null)
        assertEquals("chip_tap", ev.properties["method"])
        assertEquals("forward", ev.properties["direction"])
    }

    @Test fun `changing a filter fires schedule_filter_changed with the scope mode`() = runTest {
        val (telemetry, analytics, _) = fakeTelemetry()
        val v = vm(telemetry)
        advanceUntilIdle()
        v.toggleModality("cycle")  // an overlay change on the default (All Studios) scope
        assertEquals("all", analytics.first("schedule_filter_changed")!!.properties["mode"])
    }

    @Test fun `cold start fires screen_load_completed with source cold_start`() = runTest {
        val (telemetry, analytics, _) = fakeTelemetry()
        vm(telemetry)
        advanceUntilIdle()
        val ev = analytics.all(Telemetry.Events.SCREEN_LOAD_COMPLETED)
            .firstOrNull { it.properties["source"] == "cold_start" }
        assertTrue(ev != null)
        assertEquals("Schedule", ev.properties["screen"])
        assertEquals("success", ev.properties["outcome"])
    }

    @Test fun `switching to a cached day fires screen_load_completed with source day_switch`() = runTest {
        val (telemetry, analytics, _) = fakeTelemetry()
        val v = vm(telemetry)
        advanceUntilIdle()
        val today = Clock.System.todayIn(ScheduleViewModel.ScheduleTimeZone)
        v.selectDay(today.plus(2, DateTimeUnit.DAY))
        advanceUntilIdle()
        val ev = analytics.all(Telemetry.Events.SCREEN_LOAD_COMPLETED)
            .firstOrNull { it.properties["source"] == "day_switch" }
        assertTrue(ev != null)
        assertEquals("Schedule", ev.properties["screen"])
    }

    @Test fun `changing a filter fires screen_load_completed with source filter`() = runTest {
        val (telemetry, analytics, _) = fakeTelemetry()
        val v = vm(telemetry)
        advanceUntilIdle()
        v.toggleModality("cycle")
        advanceUntilIdle()
        val ev = analytics.all(Telemetry.Events.SCREEN_LOAD_COMPLETED)
            .firstOrNull { it.properties["source"] == "filter" }
        assertTrue(ev != null)
    }

    @Test fun `loadMore fires schedule_page_loaded`() = runTest {
        val (telemetry, analytics, _) = fakeTelemetry()
        val api = FakeScheduleApi().apply {
            overviewResult = { overviewOf(overviewStudio("solidcore")) }
            // Page 1 offers a cursor so loadMore proceeds; page 2 ends the list.
            pageResult = { call -> if (call.cursor == null) pageOf(1, nextCursor = "c2") else pageOf(2) }
        }
        val v = ScheduleViewModel(
            api = api,
            favoritesRepository = FavoritesRepository(FakeFavoritesApi()),
            bookingApi = FakeBookingApi(),
            telemetry = telemetry,
        )
        advanceUntilIdle()
        v.loadMore()
        advanceUntilIdle()
        assertTrue(analytics.first(Telemetry.Events.SCHEDULE_PAGE_LOADED) != null)
    }
}
