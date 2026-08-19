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
import org.arcana.mobile.data.SignupProfile
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

    /** Fill every field the validator requires, so individual tests can then
     *  invalidate exactly one thing. */
    private fun SignupCompletionViewModel.fillValid() {
        updatePassword("longenough1"); updateConfirmPassword("longenough1")
        updateFirstName("Alice"); updateLastName("Smith")
        updatePhoneNumber("(555) 123-4567")
        updateGender("female")
        updateBirthday("01011990")  // MM/DD/YYYY → Jan 1, 1990
        updateAddressLine1("123 Main St"); updateCity("Brooklyn")
        updateState("NY"); updatePostalCode("11211")
    }

    @Test fun `initially editing with empty fields`() {
        val vm = SignupCompletionViewModel("tok", fakeSuccess())
        val s = vm.state.value
        assertTrue(s is SignupCompletionState.Editing)
        assertEquals("", s.password); assertEquals("", s.displayName); assertEquals("", s.phoneNumber)
        assertFalse(vm.canSubmit.value)
    }

    @Test fun `validation rejects short password`() {
        val vm = SignupCompletionViewModel("tok", fakeSuccess())
        vm.fillValid(); vm.updatePassword("short"); vm.updateConfirmPassword("short")
        assertFalse(vm.canSubmit.value)
    }

    @Test fun `validation rejects mismatched confirmation`() {
        val vm = SignupCompletionViewModel("tok", fakeSuccess())
        vm.fillValid(); vm.updateConfirmPassword("DIFFERENT12")
        assertFalse(vm.canSubmit.value)
    }

    @Test fun `validation rejects a missing name part`() {
        val vm = SignupCompletionViewModel("tok", fakeSuccess())
        vm.fillValid(); vm.updateLastName("")  // last name missing
        assertFalse(vm.canSubmit.value)
        vm.updateFirstName(""); vm.updateLastName("Smith")  // first name missing
        assertFalse(vm.canSubmit.value)
    }

    @Test fun `validation rejects display name over 60 chars`() {
        val vm = SignupCompletionViewModel("tok", fakeSuccess())
        vm.fillValid(); vm.updateFirstName("x".repeat(61)); vm.updateLastName("y")
        assertFalse(vm.canSubmit.value)
    }

    @Test fun `validation rejects a phone with too few digits`() {
        val vm = SignupCompletionViewModel("tok", fakeSuccess())
        vm.fillValid(); vm.updatePhoneNumber("555-123")  // only 6 digits
        assertFalse(vm.canSubmit.value)
    }

    @Test fun `validation rejects an empty phone`() {
        val vm = SignupCompletionViewModel("tok", fakeSuccess())
        vm.fillValid(); vm.updatePhoneNumber("")
        assertFalse(vm.canSubmit.value)
    }

    @Test fun `formatted 10-digit phone is accepted`() {
        val vm = SignupCompletionViewModel("tok", fakeSuccess())
        vm.fillValid(); vm.updatePhoneNumber("(555) 123-4567")
        assertTrue(vm.canSubmit.value)
    }

    @Test fun `valid form allows submission`() {
        val vm = SignupCompletionViewModel("tok", fakeSuccess())
        vm.fillValid()
        assertTrue(vm.canSubmit.value)
    }

    @Test fun `validation rejects a missing gender`() {
        val vm = SignupCompletionViewModel("tok", fakeSuccess())
        vm.fillValid(); vm.updateGender("")
        assertFalse(vm.canSubmit.value)
    }

    @Test fun `validation rejects a missing birthday`() {
        val vm = SignupCompletionViewModel("tok", fakeSuccess())
        vm.fillValid(); vm.updateBirthday("")
        assertFalse(vm.canSubmit.value)
    }

    @Test fun `validation rejects a partial birthday`() {
        val vm = SignupCompletionViewModel("tok", fakeSuccess())
        vm.fillValid(); vm.updateBirthday("0101")  // incomplete
        assertFalse(vm.canSubmit.value)
        // No nagging error while still typing a partial date.
        assertEquals(null, (vm.state.value as SignupCompletionState.Editing).birthdayError)
    }

    @Test fun `validation rejects an impossible date`() {
        val vm = SignupCompletionViewModel("tok", fakeSuccess())
        vm.fillValid(); vm.updateBirthday("02301990")  // Feb 30
        assertFalse(vm.canSubmit.value)
        assertEquals(
            SignupCompletionViewModel.BIRTHDAY_INVALID_MESSAGE,
            (vm.state.value as SignupCompletionState.Editing).birthdayError,
        )
    }

    @Test fun `validation rejects an under-18 birthday`() {
        val vm = SignupCompletionViewModel("tok", fakeSuccess())
        vm.fillValid(); vm.updateBirthday("01012020")  // a small child
        assertFalse(vm.canSubmit.value)
        assertEquals(
            SignupCompletionViewModel.BIRTHDAY_UNDERAGE_MESSAGE,
            (vm.state.value as SignupCompletionState.Editing).birthdayError,
        )
    }

    @Test fun `birthday input keeps only digits and caps at 8`() {
        val vm = SignupCompletionViewModel("tok", fakeSuccess())
        vm.updateBirthday("01/01/1990xyz9")
        assertEquals("01011990", (vm.state.value as SignupCompletionState.Editing).birthday)
    }

    @Test fun `validation rejects missing address parts`() {
        val vm = SignupCompletionViewModel("tok", fakeSuccess())
        vm.fillValid(); vm.updateAddressLine1("")
        assertFalse(vm.canSubmit.value)
        vm.updateAddressLine1("123 Main St"); vm.updateCity("")
        assertFalse(vm.canSubmit.value)
        vm.updateCity("Brooklyn"); vm.updateState("")
        assertFalse(vm.canSubmit.value)
    }

    @Test fun `address fields are lenient — any non-blank value is accepted`() {
        val vm = SignupCompletionViewModel("tok", fakeSuccess())
        // Non-US-shaped state + postal code must still be accepted verbatim.
        vm.fillValid()
        vm.updateState("Ontario"); vm.updatePostalCode("M5V 2T6")
        assertTrue(vm.canSubmit.value)
        val s = vm.state.value as SignupCompletionState.Editing
        assertEquals("Ontario", s.state)
        assertEquals("M5V 2T6", s.postalCode)  // stored verbatim, no filtering
    }

    @Test fun `validation rejects a blank zip`() {
        val vm = SignupCompletionViewModel("tok", fakeSuccess())
        vm.fillValid(); vm.updatePostalCode("")
        assertFalse(vm.canSubmit.value)
    }

    @Test fun `address line 2 is optional`() {
        val vm = SignupCompletionViewModel("tok", fakeSuccess())
        vm.fillValid()  // no apt set
        assertTrue(vm.canSubmit.value)
    }

    @Test fun `submit forwards the collected profile`() = runTest {
        val phones = mutableListOf<String>()
        val profiles = mutableListOf<SignupProfile>()
        val vm = SignupCompletionViewModel("tok", CapturingApi(phones, profiles) {
            CompleteSignupResult.TokenExpiredOrConsumed
        })
        vm.fillValid(); vm.updateAddressLine2("  Apt 4B  "); vm.submit()
        val p = profiles.single()
        assertEquals("female", p.gender)
        assertEquals("1990-01-01", p.birthday)
        assertEquals("123 Main St", p.addressLine1)
        assertEquals("Apt 4B", p.addressLine2)  // trimmed
        assertEquals("Brooklyn", p.city)
        assertEquals("NY", p.state)
        assertEquals("11211", p.postalCode)
    }

    @Test fun `submit success transitions to success state`() = runTest {
        val vm = SignupCompletionViewModel("tok", fakeSuccess())
        vm.fillValid(); vm.submit()
        assertTrue(vm.state.value is SignupCompletionState.Success)
    }

    @Test fun `submit forwards the trimmed phone number`() = runTest {
        val captured = mutableListOf<String>()
        val vm = SignupCompletionViewModel("tok", CapturingApi(captured) {
            CompleteSignupResult.TokenExpiredOrConsumed
        })
        vm.fillValid(); vm.updatePhoneNumber("  (555) 123-4567  "); vm.submit()
        assertEquals("(555) 123-4567", captured.single())
    }

    @Test fun `410 token expired transitions to TokenExpired error`() = runTest {
        val vm = SignupCompletionViewModel("tok", FakeApi { CompleteSignupResult.TokenExpiredOrConsumed })
        vm.fillValid(); vm.submit()
        val s = vm.state.value
        assertTrue(s is SignupCompletionState.Error); assertEquals(SignupErrorKind.TokenExpired, s.kind)
    }

    @Test fun `password_invalid stays on form with an inline password error`() = runTest {
        val body = """{"error":"password_invalid","detail":["This password is too common.","This password is entirely numeric."]}"""
        val vm = SignupCompletionViewModel("tok", FakeApi { CompleteSignupResult.Other(400, body) })
        vm.fillValid(); vm.submit()
        val s = vm.state.value
        assertTrue(s is SignupCompletionState.Editing)
        assertEquals("This password is too common. This password is entirely numeric.", s.passwordError)
        assertEquals(null, s.formError)
        assertFalse(s.isSubmitting)
    }

    @Test fun `DRF field error maps to the phone field inline`() = runTest {
        val body = """{"phone_number":["Ensure this field has no more than 20 characters."]}"""
        val vm = SignupCompletionViewModel("tok", FakeApi { CompleteSignupResult.Other(400, body) })
        vm.fillValid(); vm.submit()
        val s = vm.state.value
        assertTrue(s is SignupCompletionState.Editing)
        assertEquals("Ensure this field has no more than 20 characters.", s.phoneError)
    }

    @Test fun `unrecognized 4xx stays on form with a generic banner`() = runTest {
        val vm = SignupCompletionViewModel("tok", FakeApi { CompleteSignupResult.Other(400, "bad") })
        vm.fillValid(); vm.submit()
        val s = vm.state.value
        assertTrue(s is SignupCompletionState.Editing)
        assertEquals(SignupCompletionViewModel.GENERIC_MESSAGE, s.formError)
    }

    @Test fun `5xx stays on form with a server banner`() = runTest {
        val vm = SignupCompletionViewModel("tok", FakeApi { CompleteSignupResult.Other(500, "boom") })
        vm.fillValid(); vm.submit()
        val s = vm.state.value
        assertTrue(s is SignupCompletionState.Editing)
        assertEquals(SignupCompletionViewModel.SERVER_MESSAGE, s.formError)
    }

    @Test fun `network failure stays on form with a network banner`() = runTest {
        val vm = SignupCompletionViewModel("tok", FakeApi { CompleteSignupResult.NetworkError(RuntimeException("offline")) })
        vm.fillValid(); vm.submit()
        val s = vm.state.value
        assertTrue(s is SignupCompletionState.Editing)
        assertEquals(SignupCompletionViewModel.NETWORK_MESSAGE, s.formError)
    }

    @Test fun `account_exists routes to the AlreadyHasAccount screen`() = runTest {
        val body = """{"error":"account_exists","email":"a@b.c"}"""
        val vm = SignupCompletionViewModel("tok", FakeApi { CompleteSignupResult.Other(409, body) })
        vm.fillValid(); vm.submit()
        val s = vm.state.value
        assertTrue(s is SignupCompletionState.Error); assertEquals(SignupErrorKind.AlreadyHasAccount, s.kind)
    }

    @Test fun `a failed submit preserves the typed values`() = runTest {
        val vm = SignupCompletionViewModel("tok", FakeApi { CompleteSignupResult.NetworkError(RuntimeException("x")) })
        vm.fillValid(); vm.submit()
        val s = vm.state.value as SignupCompletionState.Editing
        assertEquals("Alice", s.firstName); assertEquals("Smith", s.lastName)
        assertEquals("(555) 123-4567", s.phoneNumber); assertEquals("longenough1", s.password)
    }

    @Test fun `editing the password clears its inline error`() = runTest {
        val body = """{"error":"password_invalid","detail":["This password is too common."]}"""
        val vm = SignupCompletionViewModel("tok", FakeApi { CompleteSignupResult.Other(400, body) })
        vm.fillValid(); vm.submit()
        assertEquals("This password is too common.", (vm.state.value as SignupCompletionState.Editing).passwordError)
        vm.updatePassword("a-better-1")
        assertEquals(null, (vm.state.value as SignupCompletionState.Editing).passwordError)
    }

    @Test fun `phone input is capped at the server max length`() {
        val vm = SignupCompletionViewModel("tok", fakeSuccess())
        vm.updatePhoneNumber("1".repeat(40))
        val s = vm.state.value as SignupCompletionState.Editing
        assertEquals(SignupCompletionViewModel.PHONE_MAX_LENGTH, s.phoneNumber.length)
    }

    @Test fun `submit is a no-op when form invalid`() = runTest {
        val vm = SignupCompletionViewModel("tok", fakeSuccess())
        vm.updatePassword("short")
        vm.submit()
        assertTrue(vm.state.value is SignupCompletionState.Editing)
    }

    @Test fun `in-flight submit sets isSubmitting and blocks re-entry then resolves`() = runTest {
        val gate = CompletableDeferred<CompleteSignupResult>()
        val vm = SignupCompletionViewModel("tok", GatedApi(gate))
        vm.fillValid()
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
    override suspend fun complete(
        token: String, password: String, displayName: String, phoneNumber: String, profile: SignupProfile,
    ) = behavior()
}

private class CapturingApi(
    private val phones: MutableList<String>,
    private val profiles: MutableList<SignupProfile> = mutableListOf(),
    private val behavior: () -> CompleteSignupResult,
) : CompleteSignupCallable {
    override suspend fun complete(
        token: String, password: String, displayName: String, phoneNumber: String, profile: SignupProfile,
    ): CompleteSignupResult {
        phones.add(phoneNumber)
        profiles.add(profile)
        return behavior()
    }
}

private class GatedApi(private val gate: CompletableDeferred<CompleteSignupResult>) : CompleteSignupCallable {
    override suspend fun complete(
        token: String, password: String, displayName: String, phoneNumber: String, profile: SignupProfile,
    ): CompleteSignupResult = gate.await()
}
