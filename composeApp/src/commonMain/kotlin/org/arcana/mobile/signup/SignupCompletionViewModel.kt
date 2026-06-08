package org.arcana.mobile.signup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.arcana.mobile.data.CompleteSignupResponse

sealed interface SignupCompletionState {
    data class Editing(
        val firstName: String = "",
        val lastName: String = "",
        val password: String = "",
        val confirmPassword: String = "",
        val isSubmitting: Boolean = false,
    ) : SignupCompletionState {
        /** First + last collapsed into the single name the server still stores
         *  as `display_name`. Trimmed so trailing spaces never leak through. */
        val displayName: String get() = "${firstName.trim()} ${lastName.trim()}".trim()
    }

    data class Success(val response: CompleteSignupResponse) : SignupCompletionState

    data class Error(val kind: SignupErrorKind, val message: String? = null) : SignupCompletionState
}

enum class SignupErrorKind { TokenExpired, BadRequest, Server, Network }

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

    fun updatePassword(value: String) = mutateEditing { it.copy(password = value) }
    fun updateConfirmPassword(value: String) = mutateEditing { it.copy(confirmPassword = value) }
    // Don't trim on every keystroke — that would strip the trailing space the
    // moment you type it. Trimming happens in Editing.displayName at read time;
    // isValidEditing's isBlank() still rejects all-whitespace input.
    fun updateFirstName(value: String) = mutateEditing { it.copy(firstName = value) }
    fun updateLastName(value: String) = mutateEditing { it.copy(lastName = value) }

    fun submit() {
        val current = _state.value as? SignupCompletionState.Editing ?: return
        if (!isValidEditing(current) || current.isSubmitting) return
        setState(current.copy(isSubmitting = true))
        viewModelScope.launch {
            val result = api.complete(token, current.password, current.displayName)
            setState(
                when (result) {
                    is CompleteSignupResult.Success ->
                        SignupCompletionState.Success(result.response)
                    CompleteSignupResult.TokenExpiredOrConsumed ->
                        SignupCompletionState.Error(SignupErrorKind.TokenExpired)
                    is CompleteSignupResult.Other ->
                        SignupCompletionState.Error(
                            if (result.statusCode in 400..499) SignupErrorKind.BadRequest
                            else SignupErrorKind.Server,
                            message = result.body.take(200),
                        )
                    is CompleteSignupResult.NetworkError ->
                        SignupCompletionState.Error(
                            SignupErrorKind.Network, message = result.cause.message,
                        )
                }
            )
        }
    }

    /** Returns to a fresh [SignupCompletionState.Editing] — used by the error-state retry path. */
    fun reset() = setState(SignupCompletionState.Editing())

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
        return true
    }

    companion object {
        const val MIN_PASSWORD_LENGTH = 8
        const val MAX_DISPLAY_NAME_LENGTH = 60
    }
}
