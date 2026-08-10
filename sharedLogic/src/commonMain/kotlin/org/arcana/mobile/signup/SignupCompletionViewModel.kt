package org.arcana.mobile.signup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import org.arcana.mobile.analytics.Telemetry
import org.arcana.mobile.data.CompleteSignupResponse
import org.arcana.mobile.data.SignupProfile

sealed interface SignupCompletionState {
    data class Editing(
        val firstName: String = "",
        val lastName: String = "",
        val phoneNumber: String = "",
        // Gender stored as the server's choice code ("male"/"female"/"other"),
        // empty until the member picks. Birthday is the raw digit string
        // (MMDDYYYY) behind the MM/DD/YYYY mask; parsed to a real date at submit.
        val gender: String = "",
        val birthday: String = "",
        val addressLine1: String = "",
        val addressLine2: String = "",
        val city: String = "",
        val state: String = "",
        val postalCode: String = "",
        val password: String = "",
        val confirmPassword: String = "",
        val isSubmitting: Boolean = false,
        // Server-driven validation surfaced inline. Cleared when the user edits
        // the relevant field or re-submits, so stale messages never linger.
        val passwordError: String? = null,
        val phoneError: String? = null,
        // Shown once the member has typed a full 8-digit date that's either not a
        // real calendar date or under the minimum age. Null while still typing.
        val birthdayError: String? = null,
        // Non-field failures (network, server, unrecognized 400) shown as a
        // banner above the form so the member keeps everything they typed.
        val formError: String? = null,
    ) : SignupCompletionState {
        /** First + last collapsed into the single name the server still stores
         *  as `display_name`. Trimmed so trailing spaces never leak through. */
        val displayName: String get() = "${firstName.trim()} ${lastName.trim()}".trim()
    }

    data class Success(val response: CompleteSignupResponse) : SignupCompletionState

    /** Terminal states that leave the form — both route the member to log in. */
    data class Error(val kind: SignupErrorKind) : SignupCompletionState
}

enum class SignupErrorKind { TokenExpired, AlreadyHasAccount }

class SignupCompletionViewModel(
    private val token: String,
    private val api: CompleteSignupCallable,
    private val telemetry: Telemetry = Telemetry.Noop,
) : ViewModel() {

    private val _state = MutableStateFlow<SignupCompletionState>(SignupCompletionState.Editing())
    val state: StateFlow<SignupCompletionState> = _state.asStateFlow()

    init {
        // One per token: the member landed on the claim screen with a valid link.
        telemetry.signupStarted()
    }

    // Synchronously maintained (NOT a stateIn-derived flow) so validation is
    // observable immediately after each field update, with no dispatcher timing.
    private val _canSubmit = MutableStateFlow(false)
    val canSubmit: StateFlow<Boolean> = _canSubmit.asStateFlow()

    fun updatePassword(value: String) =
        mutateEditing { it.copy(password = value, passwordError = null, formError = null) }
    fun updateConfirmPassword(value: String) =
        mutateEditing { it.copy(confirmPassword = value, passwordError = null, formError = null) }
    // Don't trim on every keystroke — that would strip the trailing space the
    // moment you type it. Trimming happens in Editing.displayName at read time;
    // isValidEditing's isBlank() still rejects all-whitespace input.
    fun updateFirstName(value: String) = mutateEditing { it.copy(firstName = value, formError = null) }
    fun updateLastName(value: String) = mutateEditing { it.copy(lastName = value, formError = null) }
    // Cap at the server's max_length so an over-long number can never reach the
    // server (it would otherwise bounce as a generic validation failure).
    fun updatePhoneNumber(value: String) =
        mutateEditing { it.copy(phoneNumber = value.take(PHONE_MAX_LENGTH), phoneError = null, formError = null) }
    fun updateGender(value: String) = mutateEditing { it.copy(gender = value, formError = null) }
    // Birthday is typed as digits behind a MM/DD/YYYY mask. Keep only digits, cap
    // at 8, and surface an inline error once a full date is entered that's invalid
    // or under-age. Partial input shows no error (don't nag mid-type).
    fun updateBirthday(value: String) = mutateEditing {
        val digits = value.filter { c -> c.isDigit() }.take(BIRTHDAY_DIGITS)
        it.copy(birthday = digits, birthdayError = birthdayErrorFor(digits), formError = null)
    }
    fun updateAddressLine1(value: String) = mutateEditing { it.copy(addressLine1 = value, formError = null) }
    fun updateAddressLine2(value: String) = mutateEditing { it.copy(addressLine2 = value, formError = null) }
    fun updateCity(value: String) = mutateEditing { it.copy(city = value, formError = null) }
    // Lenient: store exactly what the member types. We trust them on address data
    // and never reject on shape — the only rule is "not blank".
    fun updateState(value: String) = mutateEditing { it.copy(state = value, formError = null) }
    fun updatePostalCode(value: String) = mutateEditing { it.copy(postalCode = value, formError = null) }

    fun submit() {
        val current = _state.value as? SignupCompletionState.Editing ?: return
        if (!isValidEditing(current) || current.isSubmitting) return
        setState(current.copy(isSubmitting = true, passwordError = null, phoneError = null, formError = null))
        telemetry.signupSubmitted()
        val profile = SignupProfile(
            gender = current.gender,
            // parseBirthday returns a real LocalDate; toString() is ISO-8601
            // (yyyy-MM-dd), exactly what the server's DateField expects. Validation
            // guarantees this parses, so the fallback is just defensive.
            birthday = parseBirthday(current.birthday)?.toString() ?: "",
            addressLine1 = current.addressLine1.trim(),
            addressLine2 = current.addressLine2.trim(),
            city = current.city.trim(),
            state = current.state.trim(),
            postalCode = current.postalCode.trim(),
        )
        viewModelScope.launch {
            val result = api.complete(token, current.password, current.displayName, current.phoneNumber.trim(), profile)
            // Re-read the latest editing snapshot so any keystrokes made while the
            // request was in flight are preserved when we apply errors.
            val latest = _state.value as? SignupCompletionState.Editing ?: current
            setState(resultToState(latest, result))
        }
    }

    private fun resultToState(
        editing: SignupCompletionState.Editing,
        result: CompleteSignupResult,
    ): SignupCompletionState = when (result) {
        is CompleteSignupResult.Success -> {
            telemetry.signupCompleted()
            SignupCompletionState.Success(result.response)
        }
        CompleteSignupResult.TokenExpiredOrConsumed -> {
            telemetry.signupFailed("token_expired")
            SignupCompletionState.Error(SignupErrorKind.TokenExpired)
        }
        is CompleteSignupResult.NetworkError -> {
            telemetry.signupFailed("network")
            editing.copy(isSubmitting = false, formError = NETWORK_MESSAGE)
        }
        is CompleteSignupResult.Other -> otherToState(editing, result)
    }

    private fun otherToState(
        editing: SignupCompletionState.Editing,
        result: CompleteSignupResult.Other,
    ): SignupCompletionState {
        val parsed = parseServerErrors(result.body)
        // A 409 because the email is already registered: the form is futile, so
        // route to the "log in instead" screen.
        if (result.statusCode == 409 && parsed.errorCode == "account_exists") {
            telemetry.signupFailed("account_exists", result.statusCode)
            return SignupCompletionState.Error(SignupErrorKind.AlreadyHasAccount)
        }
        val hasFieldError = parsed.password != null || parsed.phone != null
        val formError = when {
            hasFieldError -> null
            result.statusCode >= 500 -> SERVER_MESSAGE
            else -> GENERIC_MESSAGE
        }
        telemetry.signupFailed(
            reason = when {
                parsed.password != null -> "field_password"
                parsed.phone != null -> "field_phone"
                result.statusCode >= 500 -> "server_5xx"
                else -> "generic"
            },
            statusCode = result.statusCode,
        )
        return editing.copy(
            isSubmitting = false,
            passwordError = parsed.password,
            phoneError = parsed.phone,
            formError = formError,
        )
    }

    private fun mutateEditing(transform: (SignupCompletionState.Editing) -> SignupCompletionState.Editing) {
        val current = _state.value as? SignupCompletionState.Editing ?: return
        setState(transform(current))
    }

    // Single choke point: every state change also recomputes canSubmit synchronously.
    private fun setState(s: SignupCompletionState) {
        _state.value = s
        _canSubmit.value = isValidEditing(s)
    }

    private fun isValidEditing(state: SignupCompletionState): Boolean {
        if (state !is SignupCompletionState.Editing) return false
        if (state.isSubmitting) return false
        if (state.password.length < MIN_PASSWORD_LENGTH) return false
        if (state.password != state.confirmPassword) return false
        if (state.firstName.isBlank()) return false
        if (state.lastName.isBlank()) return false
        if (state.displayName.length > MAX_DISPLAY_NAME_LENGTH) return false
        if (!isValidPhone(state.phoneNumber)) return false
        if (state.gender.isBlank()) return false
        val birthday = parseBirthday(state.birthday) ?: return false
        if (!isAtLeastMinAge(birthday)) return false
        // Address: lenient — require only that each part the form asks for is
        // non-blank (apt is optional). No shape/format checks; trust the member.
        if (state.addressLine1.isBlank()) return false
        if (state.city.isBlank()) return false
        if (state.state.isBlank()) return false
        if (state.postalCode.isBlank()) return false
        return true
    }

    private data class ServerErrors(val errorCode: String?, val password: String?, val phone: String?)

    /** Best-effort parse of the server's error body into inline field messages.
     *  Handles both the custom `{error, detail}` shape and DRF field-error maps;
     *  any parse failure degrades to "no specific field error" (→ form banner). */
    private fun parseServerErrors(body: String): ServerErrors {
        return try {
            val obj = Json.parseToJsonElement(body).jsonObject
            val errorCode = (obj["error"] as? JsonPrimitive)?.contentOrNull
            val password = when {
                // Custom shape from CompleteSignupView: {error: password_invalid, detail: [...]}
                errorCode == "password_invalid" -> joinMessages(obj["detail"])
                // DRF field-error shape: {password: [...]}
                else -> joinMessages(obj["password"])
            }
            val phone = joinMessages(obj["phone_number"])
            ServerErrors(errorCode, password, phone)
        } catch (_: Exception) {
            ServerErrors(null, null, null)
        }
    }

    private fun joinMessages(element: Any?): String? {
        val arr = element as? JsonArray ?: return null
        val msg = arr.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            .joinToString(" ")
            .trim()
        return msg.ifEmpty { null }
    }

    companion object {
        const val MIN_PASSWORD_LENGTH = 8
        const val MAX_DISPLAY_NAME_LENGTH = 60
        const val MIN_PHONE_DIGITS = 10
        // Matches the server User.phone_number max_length.
        const val PHONE_MAX_LENGTH = 20
        // ToS requires members to be 18+. Mirrored by the server's validate_birthday.
        const val MIN_AGE_YEARS = 18
        const val BIRTHDAY_DIGITS = 8  // MMDDYYYY behind the MM/DD/YYYY mask
        const val BIRTHDAY_INVALID_MESSAGE = "Enter a valid date as MM/DD/YYYY."
        const val BIRTHDAY_UNDERAGE_MESSAGE = "You must be 18 or older to use Arcana."

        const val NETWORK_MESSAGE =
            "Couldn't reach the server. Check your connection and try again."
        const val SERVER_MESSAGE =
            "Something went wrong on our end. Please try again in a moment."
        const val GENERIC_MESSAGE =
            "We couldn't complete your signup. Please review your details and try again."

        /** Lightweight format check: at least [MIN_PHONE_DIGITS] digits once
         *  punctuation/spaces are stripped. Generous enough for US + intl
         *  numbers; the founders confirm the real number out-of-band. */
        fun isValidPhone(raw: String): Boolean =
            raw.count { it.isDigit() } >= MIN_PHONE_DIGITS

        /** Parse MMDDYYYY digits into a real calendar date, or null if it isn't a
         *  valid date (wrong length, month/day out of range, Feb 30, etc.). */
        fun parseBirthday(digits: String): LocalDate? {
            if (digits.length != BIRTHDAY_DIGITS) return null
            val month = digits.substring(0, 2).toIntOrNull() ?: return null
            val day = digits.substring(2, 4).toIntOrNull() ?: return null
            val year = digits.substring(4, 8).toIntOrNull() ?: return null
            if (month !in 1..12 || year < 1900) return null
            return try {
                LocalDate(year, month, day)  // throws on an impossible day (e.g. Feb 30)
            } catch (_: IllegalArgumentException) {
                null
            }
        }

        /** Inline error for a fully-typed (8-digit) birthday, or null while the
         *  member is still typing or the date is valid + of age. */
        fun birthdayErrorFor(digits: String): String? {
            if (digits.length < BIRTHDAY_DIGITS) return null
            val date = parseBirthday(digits) ?: return BIRTHDAY_INVALID_MESSAGE
            return if (isAtLeastMinAge(date)) null else BIRTHDAY_UNDERAGE_MESSAGE
        }

        /** True when [birthday] is at least [MIN_AGE_YEARS] years before today
         *  (member's local date). */
        fun isAtLeastMinAge(birthday: LocalDate): Boolean {
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
            var age = today.year - birthday.year
            if (today.monthNumber < birthday.monthNumber ||
                (today.monthNumber == birthday.monthNumber && today.dayOfMonth < birthday.dayOfMonth)
            ) {
                age--
            }
            return age >= MIN_AGE_YEARS
        }
    }
}
