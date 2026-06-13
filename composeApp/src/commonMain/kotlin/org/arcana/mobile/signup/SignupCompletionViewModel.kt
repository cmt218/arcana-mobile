package org.arcana.mobile.signup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import org.arcana.mobile.data.CompleteSignupResponse

sealed interface SignupCompletionState {
    data class Editing(
        val firstName: String = "",
        val lastName: String = "",
        val phoneNumber: String = "",
        val password: String = "",
        val confirmPassword: String = "",
        val isSubmitting: Boolean = false,
        // Server-driven validation surfaced inline. Cleared when the user edits
        // the relevant field or re-submits, so stale messages never linger.
        val passwordError: String? = null,
        val phoneError: String? = null,
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
) : ViewModel() {

    private val _state = MutableStateFlow<SignupCompletionState>(SignupCompletionState.Editing())
    val state: StateFlow<SignupCompletionState> = _state.asStateFlow()

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

    fun submit() {
        val current = _state.value as? SignupCompletionState.Editing ?: return
        if (!isValidEditing(current) || current.isSubmitting) return
        setState(current.copy(isSubmitting = true, passwordError = null, phoneError = null, formError = null))
        viewModelScope.launch {
            val result = api.complete(token, current.password, current.displayName, current.phoneNumber.trim())
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
        is CompleteSignupResult.Success -> SignupCompletionState.Success(result.response)
        CompleteSignupResult.TokenExpiredOrConsumed ->
            SignupCompletionState.Error(SignupErrorKind.TokenExpired)
        is CompleteSignupResult.NetworkError ->
            editing.copy(isSubmitting = false, formError = NETWORK_MESSAGE)
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
            return SignupCompletionState.Error(SignupErrorKind.AlreadyHasAccount)
        }
        val hasFieldError = parsed.password != null || parsed.phone != null
        val formError = when {
            hasFieldError -> null
            result.statusCode >= 500 -> SERVER_MESSAGE
            else -> GENERIC_MESSAGE
        }
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
    }
}
