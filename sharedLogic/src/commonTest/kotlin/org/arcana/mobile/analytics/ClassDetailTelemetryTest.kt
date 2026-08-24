package org.arcana.mobile.analytics

import org.arcana.mobile.networking.ApiHttpError
import org.arcana.mobile.networking.telemetryReasonFor
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Locks `class_view_failed`'s reason values. Zero taxonomy coverage existed
 * for this event before this test: the error-states migration had quietly
 * flattened `reason` from `"server_$code"` to a bare `"server"`, discarding
 * the HTTP status with nothing to catch a repeat.
 *
 * This pins the pure mapping — [telemetryReasonFor] feeding
 * `Telemetry.classViewFailed`. `ClassDetailViewModelTest` drives the ViewModel
 * that wires them together.
 */
class ClassDetailTelemetryTest {
    @Test
    fun `class_view_failed reason keeps the HTTP status`() {
        val (telemetry, analytics, _) = fakeTelemetry()
        telemetry.classViewFailed(482, ApiHttpError(500).telemetryReasonFor())
        assertEquals("server_500", analytics.first("class_view_failed")!!.properties["reason"])
    }
}
