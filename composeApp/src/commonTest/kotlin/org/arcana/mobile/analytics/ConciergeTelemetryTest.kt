@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.arcana.mobile.analytics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.arcana.mobile.concierge.ConciergeRequestViewModel
import org.arcana.mobile.networking.ConciergeApi
import org.arcana.mobile.networking.ConciergeError
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConciergeTelemetryTest {
    @BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private class FakeApi(val error: Throwable? = null) : ConciergeApi {
        override suspend fun createConciergeRequest(message: String): Int {
            error?.let { throw it }; return 1
        }
    }

    @Test fun `submit success fires concierge_request_submitted`() = runTest {
        val (telemetry, analytics, _) = fakeTelemetry()
        val vm = ConciergeRequestViewModel(FakeApi(), telemetry)
        vm.updateMessage("Help"); vm.submit()
        assertTrue("concierge_request_submitted" in analytics.names())
    }

    @Test fun `submit failure fires concierge_request_failed with code`() = runTest {
        val (telemetry, analytics, _) = fakeTelemetry()
        val vm = ConciergeRequestViewModel(FakeApi(ConciergeError("concierge_failed")), telemetry)
        vm.updateMessage("Help"); vm.submit()
        assertEquals("concierge_failed", analytics.first("concierge_request_failed")!!.properties["reason"])
    }
}
