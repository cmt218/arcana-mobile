package org.arcana.mobile.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.arcana.mobile.data.MeProfileDto
import org.arcana.mobile.data.UpdateProfileRequest
import org.arcana.mobile.networking.ProfileApi
import org.arcana.mobile.signup.SignupCompletionViewModel

/**
 * Backs the "Edit your profile" screen. Loads the member's current profile,
 * pre-fills the same fields the claim-your-name flow collects (minus password),
 * and PATCHes the changes. Save is gated on BOTH "something changed"
 * and "everything still valid" — mirroring the signup form's rules, so a member
 * can never blank out a required field. Reuses [SignupCompletionViewModel]'s pure
 * birthday/validation helpers verbatim so the two screens never drift.
 */
class EditProfileViewModel(
    private val api: ProfileApi,
) : ViewModel() {

    /** The 9 editable fields, snapshotted to detect "dirty" vs the loaded values. */
    data class Fields(
        val firstName: String = "",
        val lastName: String = "",
        val phoneNumber: String = "",
        val gender: String = "",
        // Raw MMDDYYYY digits behind the MM/DD/YYYY mask (parsed to a date at save).
        val birthday: String = "",
        val addressLine1: String = "",
        val addressLine2: String = "",
        val city: String = "",
        val state: String = "",
        val postalCode: String = "",
    )

    sealed interface State {
        data object Loading : State
        data class LoadError(val message: String) : State
        data class Editing(
            val fields: Fields,
            val birthdayError: String? = null,
            val isSaving: Boolean = false,
            val formError: String? = null,
        ) : State
        /** Save succeeded — the screen pops back to Profile. */
        data object Saved : State
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    // Maintained synchronously (not a derived flow) so the Save button reflects
    // each keystroke immediately, with no dispatcher timing.
    private val _canSave = MutableStateFlow(false)
    val canSave: StateFlow<Boolean> = _canSave.asStateFlow()

    // The values as loaded — Save is enabled only once the current fields differ.
    private var original: Fields? = null

    init { load() }

    fun load() {
        _state.value = State.Loading
        viewModelScope.launch {
            try {
                val profile = api.fetchProfile()
                val fields = profile.toFields()
                original = fields
                setEditing(State.Editing(fields = fields))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                _state.value = State.LoadError(LOAD_ERROR_MESSAGE)
            }
        }
    }

    fun updateFirstName(v: String) = mutate { it.copy(firstName = v) }
    fun updateLastName(v: String) = mutate { it.copy(lastName = v) }
    // Cap at the server's max_length so an over-long number can't reach the
    // server (same as signup); validity is "10+ digits" via isValidPhone.
    fun updatePhoneNumber(v: String) =
        mutate { it.copy(phoneNumber = v.take(SignupCompletionViewModel.PHONE_MAX_LENGTH)) }
    fun updateGender(v: String) = mutate { it.copy(gender = v) }
    // Keep only digits, cap at 8 (MMDDYYYY), and recompute the inline error: it
    // appears once a full date is typed that's invalid or under-age, and clears
    // the moment the date is valid again (or partial). Same rule as signup.
    fun updateBirthday(v: String) {
        val editing = _state.value as? State.Editing ?: return
        val digits = v.filter { c -> c.isDigit() }.take(SignupCompletionViewModel.BIRTHDAY_DIGITS)
        setEditing(
            editing.copy(
                fields = editing.fields.copy(birthday = digits),
                birthdayError = SignupCompletionViewModel.birthdayErrorFor(digits),
                formError = null,
            ),
        )
    }
    fun updateAddressLine1(v: String) = mutate { it.copy(addressLine1 = v) }
    fun updateAddressLine2(v: String) = mutate { it.copy(addressLine2 = v) }
    fun updateCity(v: String) = mutate { it.copy(city = v) }
    fun updateState(v: String) = mutate { it.copy(state = v) }
    fun updatePostalCode(v: String) = mutate { it.copy(postalCode = v) }

    // Non-birthday edits keep any existing birthday error (copy preserves it).
    private fun mutate(transform: (Fields) -> Fields) {
        val editing = _state.value as? State.Editing ?: return
        setEditing(editing.copy(fields = transform(editing.fields), formError = null))
    }

    fun save() {
        val editing = _state.value as? State.Editing ?: return
        if (editing.isSaving || !isValid(editing.fields) || !isDirty(editing.fields)) return
        setEditing(editing.copy(isSaving = true, formError = null))
        val f = editing.fields
        val body = UpdateProfileRequest(
            firstName = f.firstName.trim(),
            lastName = f.lastName.trim(),
            phoneNumber = f.phoneNumber.trim(),
            gender = f.gender,
            birthday = SignupCompletionViewModel.parseBirthday(f.birthday)?.toString() ?: "",
            addressLine1 = f.addressLine1.trim(),
            addressLine2 = f.addressLine2.trim(),
            city = f.city.trim(),
            state = f.state.trim(),
            postalCode = f.postalCode.trim(),
        )
        viewModelScope.launch {
            try {
                api.updateProfile(body)
                _state.value = State.Saved
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                val latest = _state.value as? State.Editing ?: editing
                setEditing(latest.copy(isSaving = false, formError = SAVE_ERROR_MESSAGE))
            }
        }
    }

    private fun setEditing(s: State) {
        _state.value = s
        _canSave.value = s is State.Editing && !s.isSaving && isValid(s.fields) && isDirty(s.fields)
    }

    private fun isDirty(fields: Fields): Boolean = fields != original

    /** Same rules as the signup form, minus password: every field it collects is
     *  required, so none may be blanked and the birthday must be a real date for
     *  a member 18 or older. Accounts predating a field (phone, gender, birthday)
     *  must fill it in before any edit saves; that backfill is intended. */
    private fun isValid(f: Fields): Boolean {
        if (f.firstName.isBlank() || f.lastName.isBlank()) return false
        val displayLen = "${f.firstName.trim()} ${f.lastName.trim()}".trim().length
        if (displayLen > SignupCompletionViewModel.MAX_DISPLAY_NAME_LENGTH) return false
        if (!SignupCompletionViewModel.isValidPhone(f.phoneNumber)) return false
        if (f.gender.isBlank()) return false
        val birthday = SignupCompletionViewModel.parseBirthday(f.birthday) ?: return false
        if (!SignupCompletionViewModel.isAtLeastMinAge(birthday)) return false
        if (f.addressLine1.isBlank()) return false
        if (f.city.isBlank()) return false
        if (f.state.isBlank()) return false
        if (f.postalCode.isBlank()) return false
        return true
    }

    private fun MeProfileDto.toFields() = Fields(
        firstName = firstName,
        lastName = lastName,
        phoneNumber = phoneNumber,
        gender = gender,
        birthday = isoToDigits(birthday),
        addressLine1 = addressLine1,
        addressLine2 = addressLine2,
        city = city,
        state = state,
        postalCode = postalCode,
    )

    companion object {
        const val LOAD_ERROR_MESSAGE = "Couldn't load your profile. Pull to retry."
        const val SAVE_ERROR_MESSAGE =
            "Couldn't save your changes. Check your connection and try again."

        /** "yyyy-MM-dd" (server DateField) → "MMDDYYYY" digits for the masked
         *  field. Returns "" for null/blank/malformed so the placeholder shows. */
        fun isoToDigits(iso: String?): String {
            if (iso.isNullOrBlank()) return ""
            val parts = iso.split("-")
            if (parts.size != 3) return ""
            val (y, m, d) = parts
            if (y.length != 4 || m.isEmpty() || d.isEmpty()) return ""
            return "${m.padStart(2, '0')}${d.padStart(2, '0')}$y".take(8)
        }
    }
}
