@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.arcana.mobile.profile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.arcana.mobile.data.MeProfileDto
import org.arcana.mobile.data.UpdateProfileRequest
import org.arcana.mobile.networking.ProfileApi
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditProfileViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun teardown() { Dispatchers.resetMain() }

    // 1990-05-07 → 35 in 2026, well over 18.
    private fun profile() = MeProfileDto(
        id = 1, email = "c@x.com",
        firstName = "Cole", lastName = "Tomlinson", phoneNumber = "5551234567", gender = "male",
        birthday = "1990-05-07",
        addressLine1 = "123 Main St", addressLine2 = "", city = "Brooklyn",
        state = "NY", postalCode = "11211",
    )

    private class FakeApi(
        val getResult: () -> MeProfileDto,
        val patchFails: Boolean = false,
    ) : ProfileApi {
        var patched: UpdateProfileRequest? = null
        override suspend fun fetchProfile(): MeProfileDto = getResult()
        override suspend fun updateProfile(body: UpdateProfileRequest): MeProfileDto {
            if (patchFails) throw RuntimeException("boom")
            patched = body
            return getResult()
        }
    }

    private fun editing(vm: EditProfileViewModel) =
        vm.state.value as EditProfileViewModel.State.Editing

    @Test fun `loads and prefills from the server - birthday becomes digits`() = runTest {
        val vm = EditProfileViewModel(FakeApi(::profile))
        val s = editing(vm)
        assertEquals("Cole", s.fields.firstName)
        assertEquals("Tomlinson", s.fields.lastName)
        assertEquals("5551234567", s.fields.phoneNumber)
        assertEquals("male", s.fields.gender)
        assertEquals("05071990", s.fields.birthday)
        assertEquals("123 Main St", s.fields.addressLine1)
        assertEquals("11211", s.fields.postalCode)
    }

    @Test fun `unchanged profile cannot be saved`() = runTest {
        val vm = EditProfileViewModel(FakeApi(::profile))
        assertFalse(vm.canSave.value)
    }

    @Test fun `changing a field enables save`() = runTest {
        val vm = EditProfileViewModel(FakeApi(::profile))
        vm.updateFirstName("Coley")
        assertTrue(vm.canSave.value)
    }

    @Test fun `editing back to the original value disables save again`() = runTest {
        val vm = EditProfileViewModel(FakeApi(::profile))
        vm.updateCity("Queens")
        assertTrue(vm.canSave.value)
        vm.updateCity("Brooklyn")
        assertFalse(vm.canSave.value)
    }

    @Test fun `blanking a required field disables save`() = runTest {
        val vm = EditProfileViewModel(FakeApi(::profile))
        vm.updateFirstName("")
        assertFalse(vm.canSave.value)
        vm.updateFirstName("Cole")
        vm.updateState("")
        assertFalse(vm.canSave.value)
    }

    @Test fun `an invalid phone blocks save - same 10-digit rule as signup`() = runTest {
        val vm = EditProfileViewModel(FakeApi(::profile))
        vm.updatePhoneNumber("555123")   // only 6 digits
        assertFalse(vm.canSave.value)
        vm.updatePhoneNumber("5559876543")  // 10 digits, valid + changed
        assertTrue(vm.canSave.value)
    }

    @Test fun `clearing the birthday blocks save without nagging`() = runTest {
        val vm = EditProfileViewModel(FakeApi(::profile))
        vm.updateBirthday("")
        assertFalse(vm.canSave.value)
        // Partial/empty input shows no inline error.
        assertNull(editing(vm).birthdayError)
    }

    @Test fun `underage birthday surfaces an error and blocks save`() = runTest {
        val vm = EditProfileViewModel(FakeApi(::profile))
        vm.updateBirthday("01012020")  // a 6-year-old in 2026
        assertFalse(vm.canSave.value)
        assertEquals(
            org.arcana.mobile.signup.SignupCompletionViewModel.BIRTHDAY_UNDERAGE_MESSAGE,
            editing(vm).birthdayError,
        )
    }

    // Accounts predating the phone/gender/birthday requirement. Every field the
    // claim-your-name form collects is required, so these members must backfill
    // before any edit of theirs saves — the forced backfill is the point, not a
    // defect (PROFILE-22, adjudicated 2026-08-22). If this test ever starts
    // failing, the gate has been relaxed; re-read the card before "fixing" it.
    @Test fun `a pre-requirement account must backfill before an unrelated edit saves`() = runTest {
        val legacy = { profile().copy(phoneNumber = "", gender = "", birthday = null) }
        val vm = EditProfileViewModel(FakeApi(getResult = legacy))

        vm.updateCity("Queens")
        assertFalse(vm.canSave.value, "an unrelated edit must not save around missing required fields")

        vm.updatePhoneNumber("5559876543")
        assertFalse(vm.canSave.value)
        vm.updateGender("female")
        assertFalse(vm.canSave.value)
        vm.updateBirthday("05071990")
        assertTrue(vm.canSave.value, "save should enable once every required field is filled in")
    }

    @Test fun `save sends a trimmed PATCH with ISO birthday and transitions to Saved`() = runTest {
        val api = FakeApi(::profile)
        val vm = EditProfileViewModel(api)
        vm.updateFirstName("  Coley  ")
        vm.updateBirthday("12251988")
        vm.save()
        advanceUntilIdle()
        val body = api.patched!!
        assertEquals("Coley", body.firstName)       // trimmed
        assertEquals("Tomlinson", body.lastName)
        assertEquals("5551234567", body.phoneNumber)
        assertEquals("1988-12-25", body.birthday)    // digits → ISO
        assertEquals("male", body.gender)
        assertEquals("11211", body.postalCode)
        assertTrue(vm.state.value is EditProfileViewModel.State.Saved)
    }

    @Test fun `a failed save keeps the form with an error`() = runTest {
        val vm = EditProfileViewModel(FakeApi(::profile, patchFails = true))
        vm.updateFirstName("Coley")
        vm.save()
        advanceUntilIdle()
        val s = editing(vm)
        assertFalse(s.isSaving)
        assertEquals(EditProfileViewModel.SAVE_ERROR_MESSAGE, s.formError)
    }

    @Test fun `load failure surfaces a retryable error`() = runTest {
        val vm = EditProfileViewModel(FakeApi({ throw RuntimeException("down") }))
        assertTrue(vm.state.value is EditProfileViewModel.State.LoadError)
    }

    @Test fun `isoToDigits handles ISO dates nulls and junk`() {
        assertEquals("05071990", EditProfileViewModel.isoToDigits("1990-05-07"))
        assertEquals("12251988", EditProfileViewModel.isoToDigits("1988-12-25"))
        assertEquals("", EditProfileViewModel.isoToDigits(null))
        assertEquals("", EditProfileViewModel.isoToDigits(""))
        assertEquals("", EditProfileViewModel.isoToDigits("not-a-date"))
    }
}
