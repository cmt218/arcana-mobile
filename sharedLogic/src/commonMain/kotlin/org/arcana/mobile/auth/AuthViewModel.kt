package org.arcana.mobile.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.arcana.mobile.analytics.Telemetry
import org.arcana.mobile.networking.ArcanaApiClient
import org.arcana.mobile.networking.LoginError

sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object Loading : AuthUiState
    /** [isCredentialError] true → the message belongs under the password field
     *  (wrong email/password); false → a general network/server failure. */
    data class Error(val message: String, val isCredentialError: Boolean = false) : AuthUiState
    data object Success : AuthUiState
}

class AuthViewModel(
    private val api: ArcanaApiClient,
    private val telemetry: Telemetry = Telemetry.Noop,
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            telemetry.loginSubmitted()
            try {
                api.login(email.trim(), password)
                telemetry.loginSucceeded()
                _uiState.value = AuthUiState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: LoginError) {
                when {
                    e.statusCode == 401 -> {
                        telemetry.loginFailed("invalid_credentials", e.statusCode)
                        _uiState.value = AuthUiState.Error(
                            "That email and password don't match. Double-check and try again.",
                            isCredentialError = true,
                        )
                    }
                    e.statusCode in 500..599 -> {
                        telemetry.loginFailed("server_5xx", e.statusCode)
                        _uiState.value = AuthUiState.Error(
                            "Something went wrong on our end. Please try again in a moment.",
                        )
                    }
                    else -> {
                        telemetry.loginFailed("other", e.statusCode)
                        _uiState.value = AuthUiState.Error("Couldn't sign you in (error ${e.statusCode}).")
                    }
                }
            } catch (e: Exception) {
                telemetry.loginFailed("network")
                _uiState.value = AuthUiState.Error(
                    "Couldn't reach the server. Check your connection and try again.",
                )
            }
        }
    }

    fun resetState() {
        _uiState.value = AuthUiState.Idle
    }
}
