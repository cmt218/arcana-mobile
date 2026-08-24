package org.arcana.mobile.analytics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TelemetryGateTest {

    @Test
    fun `release build on prod is the only combination that reports`() {
        assertTrue(TelemetryGate.shouldReportAnalytics(isDebugBuild = false, environment = ENV_PROD))
    }

    @Test
    fun `a debug build never reports whatever it points at`() {
        listOf(ENV_PROD, ENV_LOCAL, ENV_TUNNEL, ENV_OTHER).forEach { env ->
            assertFalse(
                TelemetryGate.shouldReportAnalytics(isDebugBuild = true, environment = env),
                "debug build reported from $env",
            )
        }
    }

    @Test
    fun `a release build pointed off prod never reports`() {
        listOf(ENV_LOCAL, ENV_TUNNEL, ENV_OTHER).forEach { env ->
            assertFalse(
                TelemetryGate.shouldReportAnalytics(isDebugBuild = false, environment = env),
                "release build reported from $env",
            )
        }
    }

    @Test
    fun `an unrecognized environment string is not treated as prod`() {
        assertFalse(TelemetryGate.shouldReportAnalytics(isDebugBuild = false, environment = ""))
        assertFalse(TelemetryGate.shouldReportAnalytics(isDebugBuild = false, environment = "Prod"))
        assertFalse(TelemetryGate.shouldReportAnalytics(isDebugBuild = false, environment = "production"))
    }

    @Test
    fun `the developer settings override decides the gate rather than the bundled default`() {
        // A release build whose base URL was overridden to localhost must go
        // quiet: this is the regression-run leak the gate exists to stop.
        val overridden = classifyEnvironment("http://10.0.2.2:8000")
        assertEquals(ENV_LOCAL, overridden)
        assertFalse(TelemetryGate.shouldReportAnalytics(isDebugBuild = false, environment = overridden))
    }

    @Test
    fun `sentry keeps reporting everywhere and is labelled by build and environment`() {
        assertEquals("prod", TelemetryGate.sentryEnvironment(isDebugBuild = false, environment = ENV_PROD))
        assertEquals("prod-debug", TelemetryGate.sentryEnvironment(isDebugBuild = true, environment = ENV_PROD))
        assertEquals("local-debug", TelemetryGate.sentryEnvironment(isDebugBuild = true, environment = ENV_LOCAL))
        assertEquals("tunnel", TelemetryGate.sentryEnvironment(isDebugBuild = false, environment = ENV_TUNNEL))
    }
}
