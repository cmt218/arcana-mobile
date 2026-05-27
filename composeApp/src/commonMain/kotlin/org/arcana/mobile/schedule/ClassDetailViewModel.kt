package org.arcana.mobile.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.arcana.mobile.data.ScheduleSessionDto
import org.arcana.mobile.logWarning
import org.arcana.mobile.networking.ArcanaApiClient

sealed interface ClassDetailUiState {
    data object Loading : ClassDetailUiState
    data class Success(val session: ScheduleSessionDto) : ClassDetailUiState
    data class Error(val message: String) : ClassDetailUiState
}

class ClassDetailViewModel(
    private val api: ArcanaApiClient,
    private val sessionId: Int,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ClassDetailUiState>(ClassDetailUiState.Loading)
    val uiState: StateFlow<ClassDetailUiState> = _uiState

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            _uiState.value = ClassDetailUiState.Loading
            try {
                _uiState.value = ClassDetailUiState.Success(api.fetchClassDetail(sessionId))
            } catch (e: ResponseException) {
                val code = e.response.status.value
                logWarning("ClassDetailViewModel", e.message ?: "HTTP $code")
                _uiState.value = ClassDetailUiState.Error("server error $code")
            } catch (e: Exception) {
                logWarning("ClassDetailViewModel", e.message ?: "Unknown error")
                _uiState.value = ClassDetailUiState.Error("server error")
            }
        }
    }
}
