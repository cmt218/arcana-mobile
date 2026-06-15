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

    @Test fun `entering filter mode fires schedule_filter_changed`() = runTest {
        val (telemetry, analytics, _) = fakeTelemetry()
        val v = vm(telemetry)
        advanceUntilIdle()
        v.enterFilterMode()
        assertEquals("custom", analytics.first("schedule_filter_changed")!!.properties["mode"])
    }
}
