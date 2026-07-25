package org.arcana.mobile.booking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.arcana.mobile.data.BookingDto
import org.arcana.mobile.networking.BookingApi
import org.arcana.mobile.networking.ErrorType
import org.arcana.mobile.networking.toErrorType

sealed interface MyBookingsUiState {
    data object Loading : MyBookingsUiState
    data class Success(val upcoming: List<BookingDto>, val past: List<BookingDto>) : MyBookingsUiState
    data class Error(val type: ErrorType) : MyBookingsUiState
}

class MyBookingsViewModel(private val api: BookingApi) : ViewModel() {
    private val _uiState = MutableStateFlow<MyBookingsUiState>(MyBookingsUiState.Loading)
    val uiState: StateFlow<MyBookingsUiState> = _uiState

    fun load() {
        viewModelScope.launch {
            _uiState.value = MyBookingsUiState.Loading
            try {
                val data = api.myBookings()
                _uiState.value = MyBookingsUiState.Success(data.upcoming, data.past)
            } catch (e: Exception) {
                // One catch: toErrorType() distinguishes "the server answered
                // badly" from "we never reached the server".
                _uiState.value = MyBookingsUiState.Error(e.toErrorType())
            }
        }
    }

    fun cancel(bookingId: Int) {
        viewModelScope.launch {
            runCatching { api.cancelBooking(bookingId) }
            load()
        }
    }
}
