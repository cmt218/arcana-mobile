@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.arcana.mobile.analytics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.arcana.mobile.data.CompleteSignupResponse
import org.arcana.mobile.data.SignupMembership
import org.arcana.mobile.data.SignupUser
import org.arcana.mobile.signup.CompleteSignupCallable
import org.arcana.mobile.signup.CompleteSignupResult
import org.arcana.mobile.signup.SignupCompletionViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Locks the signup taxonomy so future edits can't silently drop events. */
class SignupTelemetryTest {
    @BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private class FakeApi(val behavior: () -> CompleteSignupResult) : CompleteSignupCallable {
        override suspend fun complete(token: String, password: String, displayName: String, phoneNumber: String) = behavior()
    }

    private fun SignupCompletionViewModel.fillValid() {
        updatePassword("longenough1"); updateConfirmPassword("longenough1")
        updateFirstName("Alice"); updateLastName("Smith"); updatePhoneNumber("(555) 123-4567")
    }

    private fun success() = CompleteSignupResult.Success(
        CompleteSignupResponse("a", "r", SignupUser(1, "a@b.c", "Alice"), SignupMembership(1, "active", "tier", "0042")),
    )

    @Test fun `started fires on init`() {
        val (telemetry, analytics, _) = fakeTelemetry()
        SignupCompletionViewModel("tok", FakeApi { success() }, telemetry)
        assertTrue("signup_started" in analytics.names())
    }

    @Test fun `happy path fires submitted then completed`() = runTest {
        val (telemetry, analytics, _) = fakeTelemetry()
        val vm = SignupCompletionViewModel("tok", FakeApi { success() }, telemetry)
        vm.fillValid(); vm.submit()
        assertEquals(listOf("signup_started", "signup_submitted", "signup_completed"), analytics.names())
    }

    @Test fun `network failure fires submitted then failed with reason`() = runTest {
        val (telemetry, analytics, _) = fakeTelemetry()
        val vm = SignupCompletionViewModel("tok", FakeApi { CompleteSignupResult.NetworkError(RuntimeException("x")) }, telemetry)
        vm.fillValid(); vm.submit()
        assertTrue("signup_submitted" in analytics.names())
        assertEquals("network", analytics.first("signup_failed")!!.properties["reason"])
    }

    @Test fun `server 5xx fires failed with server_5xx`() = runTest {
        val (telemetry, analytics, _) = fakeTelemetry()
        val vm = SignupCompletionViewModel("tok", FakeApi { CompleteSignupResult.Other(500, "boom") }, telemetry)
        vm.fillValid(); vm.submit()
        assertEquals("server_5xx", analytics.first("signup_failed")!!.properties["reason"])
    }
}
