package org.arcana.mobile.booking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
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

    /** True while [retry] is in flight; the error stays on screen and the retry
     *  link carries the progress. */
    private val _retrying = MutableStateFlow(false)
    val retrying: StateFlow<Boolean> = _retrying

    fun load() {
        viewModelScope.launch {
            _uiState.value = MyBookingsUiState.Loading
            fetch()
        }
    }

    /** Retry from the error state. Deliberately does NOT set Loading: dropping
     *  to the loading caption and back flashes on every failed retry. */
    fun retry() {
        if (_retrying.value) return
        _retrying.value = true
        viewModelScope.launch {
            try {
                fetch()
            } finally {
                _retrying.value = false
            }
        }
    }

    private suspend fun fetch() {
        try {
            val data = api.myBookings()
            _uiState.value = MyBookingsUiState.Success(data.upcoming, data.past)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiState.value = MyBookingsUiState.Error(e.toErrorType())
        }
    }

    /** Cold load, shimmer and all. [retry] is what the error state uses. */
    fun reload() = load()

    fun cancel(bookingId: Int) {
        viewModelScope.launch {
            runCatching { api.cancelBooking(bookingId) }
            load()
        }
    }
}
