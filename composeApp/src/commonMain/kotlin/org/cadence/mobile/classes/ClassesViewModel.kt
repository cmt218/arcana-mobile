package org.cadence.mobile.classes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.cadence.mobile.data.ClassDto
import org.cadence.mobile.logWarning
import org.cadence.mobile.networking.fetchClasses

sealed interface ClassesUiState {
    data object Loading : ClassesUiState
    data class Success(val classes: List<ClassDto>) : ClassesUiState
    data class Error(val message: String) : ClassesUiState
}

class ClassesViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<ClassesUiState>(ClassesUiState.Loading)
    val uiState: StateFlow<ClassesUiState> = _uiState

    init {
        loadClasses()
    }

    fun loadClasses() {
        viewModelScope.launch {
            _uiState.value = ClassesUiState.Loading
            try {
                _uiState.value = ClassesUiState.Success(fetchClasses())
            } catch (e: ResponseException) {
                val code = e.response.status.value
                logWarning("ClassesViewModel", e.message ?: "HTTP error $code")
                _uiState.value = ClassesUiState.Error("server error $code")
            } catch (e: Exception) {
                logWarning("ClassesViewModel", e.message ?: "Unknown error")
                _uiState.value = ClassesUiState.Error("server error")
            }
        }
    }
}
