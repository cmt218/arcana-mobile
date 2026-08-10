package org.arcana.mobile.booking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.arcana.mobile.data.BookingDto
import org.arcana.mobile.networking.BookingApi

sealed interface MyBookingsUiState {
    data object Loading : MyBookingsUiState
    data class Success(val upcoming: List<BookingDto>, val past: List<BookingDto>) : MyBookingsUiState
    data class Error(val message: String) : MyBookingsUiState
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
            } catch (e: ResponseException) {
                _uiState.value = MyBookingsUiState.Error("server error ${e.response.status.value}")
            } catch (e: Exception) {
                _uiState.value = MyBookingsUiState.Error("server error")
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
