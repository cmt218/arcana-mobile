package org.arcana.mobile.analytics

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AppStartTrackerTest {
    @BeforeTest fun setUp() = AppStartTracker.resetForTest()
    @AfterTest fun tearDown() = AppStartTracker.resetForTest()

    @Test fun `fires app_start_completed once with cold start type`() {
        val (telemetry, analytics, _) = fakeTelemetry()
        AppStartTracker.markStart()
        AppStartTracker.onFirstContent(telemetry, authenticated = true)
        AppStartTracker.onFirstContent(telemetry, authenticated = true) // must not double-fire

        val starts = analytics.all(Telemetry.Events.APP_START_COMPLETED)
        assertEquals(1, starts.size)
        assertEquals("cold", starts.first().properties["start_type"])
        assertEquals(true, starts.first().properties["authenticated"])
    }

    @Test fun `does nothing when start was never marked`() {
        val (telemetry, analytics, _) = fakeTelemetry()
        AppStartTracker.onFirstContent(telemetry, authenticated = false)
        assertEquals(0, analytics.all(Telemetry.Events.APP_START_COMPLETED).size)
    }
}
