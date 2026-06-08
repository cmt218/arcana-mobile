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

    /** Drives the pull-to-refresh spinner; true only during a [refresh] fetch. */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    init {
        reload()
    }

    /** Force a re-fetch, flashing the shimmer — for first load and error-retry. */
    fun reload() {
        viewModelScope.launch {
            _uiState.value = ClassDetailUiState.Loading
            fetch()
        }
    }

    /** Pull-to-refresh: re-fetch without flashing the shimmer, keeping the
     *  current content visible (and untouched on a transient failure). */
    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                fetch()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private suspend fun fetch() {
        try {
            _uiState.value = ClassDetailUiState.Success(api.fetchClassDetail(sessionId))
        } catch (e: ResponseException) {
            val code = e.response.status.value
            logWarning("ClassDetailViewModel", e.message ?: "HTTP $code")
            // On a refresh failure keep whatever's already on screen rather than
            // replacing good content with a full-screen error.
            if (_uiState.value !is ClassDetailUiState.Success) {
                _uiState.value = ClassDetailUiState.Error("server error $code")
            }
        } catch (e: Exception) {
            logWarning("ClassDetailViewModel", e.message ?: "Unknown error")
            if (_uiState.value !is ClassDetailUiState.Success) {
                _uiState.value = ClassDetailUiState.Error("server error")
            }
        }
    }
}
