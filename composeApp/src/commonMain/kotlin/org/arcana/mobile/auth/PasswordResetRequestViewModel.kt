package org.arcana.mobile.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.arcana.mobile.networking.PasswordResetApi

sealed interface PasswordResetSubmit {
    data object Idle : PasswordResetSubmit
    data object Submitting : PasswordResetSubmit
    data object Sent : PasswordResetSubmit
    data object Failed : PasswordResetSubmit
}

class PasswordResetRequestViewModel(
    private val api: PasswordResetApi,
    initialEmail: String = "",
) : ViewModel() {

    private val _email = MutableStateFlow(initialEmail)
    val email: StateFlow<String> = _email

    private val _submitState = MutableStateFlow<PasswordResetSubmit>(PasswordResetSubmit.Idle)
    val submitState: StateFlow<PasswordResetSubmit> = _submitState

    fun updateEmail(value: String) {
        _email.value = value
        if (_submitState.value is PasswordResetSubmit.Failed) {
            _submitState.value = PasswordResetSubmit.Idle
        }
    }

    val canSubmit: Boolean
        get() = isValidEmail(_email.value.trim()) &&
            _submitState.value !is PasswordResetSubmit.Submitting

    fun submit() {
        if (!canSubmit) return
        _submitState.value = PasswordResetSubmit.Submitting
        val email = _email.value.trim()
        viewModelScope.launch {
            try {
                api.requestPasswordReset(email)
                _submitState.value = PasswordResetSubmit.Sent
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _submitState.value = PasswordResetSubmit.Failed
            }
        }
    }

    companion object {
        private val EMAIL_RE = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
        fun isValidEmail(value: String): Boolean = EMAIL_RE.matches(value)
    }
}
