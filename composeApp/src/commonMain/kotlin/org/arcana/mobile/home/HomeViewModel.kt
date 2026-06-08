package org.arcana.mobile.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.arcana.mobile.data.BookingDto
import org.arcana.mobile.networking.BookingApi
import org.arcana.mobile.networking.MembershipApi

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Success(
        val displayName: String,
        val creditsRemaining: Int?,
        val upcoming: List<BookingDto>,
        val weekStreak: Int,
    ) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

class HomeViewModel(
    private val bookingApi: BookingApi,
    private val membershipApi: MembershipApi,
) : ViewModel() {
    /** Single-arg constructor used by the test's FakeApi (which implements both interfaces). */
    constructor(api: Any) : this(api as BookingApi, api as MembershipApi)

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState

    /** Drives the pull-to-refresh spinner; true only during a [refresh] fetch. */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    fun load() {
        viewModelScope.launch { fetch() }
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
            val me = membershipApi.membershipMe()
            val upcoming = runCatching { bookingApi.myBookings().upcoming }.getOrDefault(emptyList())
            _uiState.value = HomeUiState.Success(
                displayName = me.member.displayName ?: me.member.email.substringBefore("@"),
                creditsRemaining = me.currentPeriod?.creditsRemaining,
                upcoming = upcoming,
                weekStreak = me.member.weekStreak,
            )
        } catch (e: Exception) {
            // On a refresh failure keep whatever's already on screen rather than
            // replacing good content with a full-screen error.
            if (_uiState.value !is HomeUiState.Success) {
                _uiState.value = HomeUiState.Error("server error")
            }
        }
    }
}
