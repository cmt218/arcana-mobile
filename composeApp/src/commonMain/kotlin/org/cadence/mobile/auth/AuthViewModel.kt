package org.cadence.mobile.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.cadence.mobile.networking.CadenceApiClient

sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object Loading : AuthUiState
    data class Error(val message: String) : AuthUiState
    data object Success : AuthUiState
}

class AuthViewModel(private val api: CadenceApiClient) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                api.login(email.trim(), password)
                _uiState.value = AuthUiState.Success
            } catch (e: ResponseException) {
                val code = e.response.status.value
                _uiState.value = AuthUiState.Error(
                    if (code == 401) "Invalid email or password." else "Server error ($code)."
                )
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error("Could not connect to server.")
            }
        }
    }

    fun register(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                api.register(email.trim(), password)
                _uiState.value = AuthUiState.Success
            } catch (e: ResponseException) {
                val code = e.response.status.value
                _uiState.value = AuthUiState.Error(
                    if (code == 400) "An account with this email already exists."
                    else "Server error ($code)."
                )
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error("Could not connect to server.")
            }
        }
    }

    fun resetState() {
        _uiState.value = AuthUiState.Idle
    }
}
