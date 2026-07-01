@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.arcana.mobile.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.arcana.mobile.networking.PasswordResetApi
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PasswordResetRequestViewModelTest {

    @BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    @Test fun `prefills email from login screen`() {
        val vm = PasswordResetRequestViewModel(FakeApi(), initialEmail = "member@example.com")
        assertEquals("member@example.com", vm.email.value)
    }

    @Test fun `cannot submit without a valid email shape`() {
        val vm = PasswordResetRequestViewModel(FakeApi())
        assertFalse(vm.canSubmit)
        vm.updateEmail("not-an-email")
        assertFalse(vm.canSubmit)
        vm.updateEmail("member@example.com")
        assertTrue(vm.canSubmit)
    }

    @Test fun `successful submit trims email and transitions to sent`() = runTest {
        val api = FakeApi()
        val vm = PasswordResetRequestViewModel(api)
        vm.updateEmail("  member@example.com  ")

        vm.submit()

        assertTrue(vm.submitState.value is PasswordResetSubmit.Sent)
        assertEquals("member@example.com", api.lastEmail)
    }

    @Test fun `network failure transitions to failed`() = runTest {
        val vm = PasswordResetRequestViewModel(FakeApi(error = RuntimeException("offline")))
        vm.updateEmail("member@example.com")

        vm.submit()

        assertTrue(vm.submitState.value is PasswordResetSubmit.Failed)
    }

    @Test fun `editing after a failure clears the error`() = runTest {
        val vm = PasswordResetRequestViewModel(FakeApi(error = RuntimeException("offline")))
        vm.updateEmail("member@example.com")
        vm.submit()
        assertTrue(vm.submitState.value is PasswordResetSubmit.Failed)

        vm.updateEmail("member2@example.com")

        assertTrue(vm.submitState.value is PasswordResetSubmit.Idle)
    }

    private class FakeApi(private val error: Throwable? = null) : PasswordResetApi {
        var lastEmail: String? = null

        override suspend fun requestPasswordReset(email: String) {
            lastEmail = email
            error?.let { throw it }
        }
    }
}
