@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.arcana.mobile.signup

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.arcana.mobile.data.CompleteSignupResponse
import org.arcana.mobile.data.SignupMembership
import org.arcana.mobile.data.SignupUser
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SignupCompletionViewModelTest {

    @BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private fun fakeSuccess() = FakeApi {
        CompleteSignupResult.Success(
            CompleteSignupResponse(
                access = "a", refresh = "r",
                user = SignupUser(1, "a@b.c", "Alice"),
                membership = SignupMembership(1, "active", "founding-explorer", "0042"),
            )
        )
    }

    @Test fun `initially editing with empty fields`() {
        val vm = SignupCompletionViewModel("tok", fakeSuccess())
        val s = vm.state.value
        assertTrue(s is SignupCompletionState.Editing)
        assertEquals("", s.password); assertEquals("", s.displayName)
        assertFalse(vm.canSubmit.value)
    }

    @Test fun `validation rejects short password`() {
        val vm = SignupCompletionViewModel("tok", fakeSuccess())
        vm.updatePassword("short"); vm.updateConfirmPassword("short"); vm.updateFirstName("Alice"); vm.updateLastName("Smith")
        assertFalse(vm.canSubmit.value)
    }

    @Test fun `validation rejects mismatched confirmation`() {
        val vm = SignupCompletionViewModel("tok", fakeSuccess())
        vm.updatePassword("longenough1"); vm.updateConfirmPassword("DIFFERENT12"); vm.updateFirstName("Alice"); vm.updateLastName("Smith")
        assertFalse(vm.canSubmit.value)
    }

    @Test fun `validation rejects a missing name part`() {
        val vm = SignupCompletionViewModel("tok", fakeSuccess())
        vm.updatePassword("longenough1"); vm.updateConfirmPassword("longenough1")
        vm.updateFirstName("Alice"); vm.updateLastName("")  // last name missing
        assertFalse(vm.canSubmit.value)
        vm.updateFirstName(""); vm.updateLastName("Smith")  // first name missing
        assertFalse(vm.canSubmit.value)
    }

    @Test fun `validation rejects display name over 60 chars`() {
        val vm = SignupCompletionViewModel("tok", fakeSuccess())
        vm.updatePassword("longenough1"); vm.updateConfirmPassword("longenough1")
        vm.updateFirstName("x".repeat(61)); vm.updateLastName("y")
        assertFalse(vm.canSubmit.value)
    }

    @Test fun `valid form allows submission`() {
        val vm = SignupCompletionViewModel("tok", fakeSuccess())
        vm.updatePassword("longenough1"); vm.updateConfirmPassword("longenough1"); vm.updateFirstName("Alice"); vm.updateLastName("Smith")
        assertTrue(vm.canSubmit.value)
    }

    @Test fun `submit success transitions to success state`() = runTest {
        val vm = SignupCompletionViewModel("tok", fakeSuccess())
        vm.updatePassword("longenough1"); vm.updateConfirmPassword("longenough1"); vm.updateFirstName("Alice"); vm.updateLastName("Smith")
        vm.submit()
        assertTrue(vm.state.value is SignupCompletionState.Success)
    }

    @Test fun `410 token expired transitions to TokenExpired error`() = runTest {
        val vm = SignupCompletionViewModel("tok", FakeApi { CompleteSignupResult.TokenExpiredOrConsumed })
        vm.updatePassword("longenough1"); vm.updateConfirmPassword("longenough1"); vm.updateFirstName("Alice"); vm.updateLastName("Smith")
        vm.submit()
        val s = vm.state.value
        assertTrue(s is SignupCompletionState.Error); assertEquals(SignupErrorKind.TokenExpired, s.kind)
    }

    @Test fun `4xx Other transitions to BadRequest error`() = runTest {
        val vm = SignupCompletionViewModel("tok", FakeApi { CompleteSignupResult.Other(400, "bad") })
        vm.updatePassword("longenough1"); vm.updateConfirmPassword("longenough1"); vm.updateFirstName("Alice"); vm.updateLastName("Smith")
        vm.submit()
        val s = vm.state.value
        assertTrue(s is SignupCompletionState.Error); assertEquals(SignupErrorKind.BadRequest, s.kind)
    }

    @Test fun `5xx Other transitions to Server error`() = runTest {
        val vm = SignupCompletionViewModel("tok", FakeApi { CompleteSignupResult.Other(500, "boom") })
        vm.updatePassword("longenough1"); vm.updateConfirmPassword("longenough1"); vm.updateFirstName("Alice"); vm.updateLastName("Smith")
        vm.submit()
        val s = vm.state.value
        assertTrue(s is SignupCompletionState.Error); assertEquals(SignupErrorKind.Server, s.kind)
    }

    @Test fun `network failure transitions to Network error`() = runTest {
        val vm = SignupCompletionViewModel("tok", FakeApi { CompleteSignupResult.NetworkError(RuntimeException("offline")) })
        vm.updatePassword("longenough1"); vm.updateConfirmPassword("longenough1"); vm.updateFirstName("Alice"); vm.updateLastName("Smith")
        vm.submit()
        val s = vm.state.value
        assertTrue(s is SignupCompletionState.Error); assertEquals(SignupErrorKind.Network, s.kind)
    }

    @Test fun `reset returns to fresh editing`() = runTest {
        val vm = SignupCompletionViewModel("tok", FakeApi { CompleteSignupResult.Other(500, "x") })
        vm.updatePassword("longenough1"); vm.updateConfirmPassword("longenough1"); vm.updateFirstName("Alice"); vm.updateLastName("Smith"); vm.submit()
        assertTrue(vm.state.value is SignupCompletionState.Error)
        vm.reset()
        val s = vm.state.value
        assertTrue(s is SignupCompletionState.Editing); assertEquals("", s.password); assertFalse(vm.canSubmit.value)
    }

    @Test fun `submit is a no-op when form invalid`() = runTest {
        val vm = SignupCompletionViewModel("tok", fakeSuccess())
        vm.updatePassword("short")
        vm.submit()
        assertTrue(vm.state.value is SignupCompletionState.Editing)
    }

    @Test fun `in-flight submit sets isSubmitting and blocks re-entry, then resolves`() = runTest {
        val gate = CompletableDeferred<CompleteSignupResult>()
        val vm = SignupCompletionViewModel("tok", GatedApi(gate))
        vm.updatePassword("longenough1"); vm.updateConfirmPassword("longenough1"); vm.updateFirstName("Alice"); vm.updateLastName("Smith")
        assertTrue(vm.canSubmit.value)

        vm.submit() // launches, suspends inside the gated fake
        val midState = vm.state.value
        assertTrue(midState is SignupCompletionState.Editing)
        assertTrue(midState.isSubmitting)
        assertFalse(vm.canSubmit.value) // disabled while in flight

        vm.submit() // re-entrancy: must be a no-op while submitting
        assertTrue((vm.state.value as SignupCompletionState.Editing).isSubmitting)

        gate.complete(CompleteSignupResult.TokenExpiredOrConsumed) // release the in-flight call
        assertTrue(vm.state.value is SignupCompletionState.Error)
        assertEquals(SignupErrorKind.TokenExpired, (vm.state.value as SignupCompletionState.Error).kind)
    }
}

private class FakeApi(private val behavior: () -> CompleteSignupResult) : CompleteSignupCallable {
    override suspend fun complete(token: String, password: String, displayName: String) = behavior()
}

private class GatedApi(private val gate: CompletableDeferred<CompleteSignupResult>) : CompleteSignupCallable {
    override suspend fun complete(token: String, password: String, displayName: String): CompleteSignupResult = gate.await()
}
