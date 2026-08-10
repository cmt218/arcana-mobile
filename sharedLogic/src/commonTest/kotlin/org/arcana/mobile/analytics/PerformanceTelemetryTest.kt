package org.arcana.mobile.analytics

import kotlin.test.Test
import kotlin.test.assertEquals

/** Locks the performance-observability taxonomy (event names + property keys). */
class PerformanceTelemetryTest {
    @Test fun `api_request carries transport split`() {
        val (telemetry, analytics, _) = fakeTelemetry()
        telemetry.apiRequest(
            endpoint = "schedule_page", method = "GET", statusCode = 200,
            outcome = "success", totalMs = 200, serverMs = 80, networkMs = 120,
            responseBytes = 4096,
        )
        val ev = analytics.first(Telemetry.Events.API_REQUEST)!!
        assertEquals("schedule_page", ev.properties["endpoint"])
        assertEquals(200, ev.properties["status_code"])
        assertEquals("success", ev.properties["outcome"])
        assertEquals(200L, ev.properties["total_ms"])
        assertEquals(80L, ev.properties["server_ms"])
        assertEquals(120L, ev.properties["network_ms"])
        assertEquals(4096L, ev.properties["response_bytes"])
    }

    @Test fun `app_start_completed carries duration and start type`() {
        val (telemetry, analytics, _) = fakeTelemetry()
        telemetry.appStartCompleted(durationMs = 1400, startType = "cold", authenticated = true, splashMs = null)
        val ev = analytics.first(Telemetry.Events.APP_START_COMPLETED)!!
        assertEquals(1400L, ev.properties["duration_ms"])
        assertEquals("cold", ev.properties["start_type"])
        assertEquals(true, ev.properties["authenticated"])
    }

    @Test fun `screen_load_completed carries screen and source`() {
        val (telemetry, analytics, _) = fakeTelemetry()
        telemetry.screenLoadCompleted(screen = "Schedule", source = "day_switch", durationMs = 12, outcome = "success", sessionCount = 30)
        val ev = analytics.first(Telemetry.Events.SCREEN_LOAD_COMPLETED)!!
        assertEquals("Schedule", ev.properties["screen"])
        assertEquals("day_switch", ev.properties["source"])
        assertEquals(12L, ev.properties["duration_ms"])
        assertEquals("success", ev.properties["outcome"])
        assertEquals(30, ev.properties["session_count"])
    }

    @Test fun `schedule_page_loaded carries page index and day`() {
        val (telemetry, analytics, _) = fakeTelemetry()
        telemetry.schedulePageLoaded(durationMs = 90, pageIndex = 2, sessionCount = 50, outcome = "success", day = "2026-07-20")
        val ev = analytics.first(Telemetry.Events.SCHEDULE_PAGE_LOADED)!!
        assertEquals(90L, ev.properties["duration_ms"])
        assertEquals(2, ev.properties["page_index"])
        assertEquals(50, ev.properties["session_count"])
        assertEquals("2026-07-20", ev.properties["day"])
    }
}
