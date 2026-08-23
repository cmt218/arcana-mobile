package org.arcana.mobile.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import org.arcana.mobile.data.BookingDto
import org.arcana.mobile.networking.BookingApi
import org.arcana.mobile.networking.ErrorType
import org.arcana.mobile.networking.MembershipApi
import org.arcana.mobile.networking.toErrorType

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Success(
        val displayName: String,
        val creditsRemaining: Int?,
        // The next-month wallet (month name + credits) — set only when a member
        // has bought next month while still in the current month. Drives the
        // "Next: August" chip; null for everyone else.
        val upcomingMonth: String?,
        val upcomingCredits: Int?,
        val upcoming: List<BookingDto>,
        val weekStreak: Int,
    ) : HomeUiState
    data class Error(val type: ErrorType) : HomeUiState
}

class HomeViewModel(
    private val bookingApi: BookingApi,
    private val membershipApi: MembershipApi,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : ViewModel() {
    /** Single-arg constructor used by the test's FakeApi (which implements both interfaces). */
    constructor(api: Any) : this(api as BookingApi, api as MembershipApi)

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState

    /** True while a retry from the error state is in flight. The error stays on
     *  screen throughout and the retry button carries the progress, so a failed
     *  retry never flashes a loading skeleton on its way back to the error. */
    private val _retrying = MutableStateFlow(false)
    val retrying: StateFlow<Boolean> = _retrying

    /** Drives the pull-to-refresh spinner; true only during a [refresh] fetch. */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    /** True when a background refresh failed while [HomeUiState.Success] content
     *  was already on screen. Cleared by a successful load or [dismissRefreshFailed]. */
    private val _refreshFailed = MutableStateFlow(false)
    val refreshFailed: StateFlow<Boolean> = _refreshFailed

    private var fetchJob: Job? = null
    private var lastFetchStarted: TimeMark? = null

    /** Cold load, and the automatic refresh HomeScreen fires from a
     *  LifecycleResumeEffect on every return to the foreground. Skipped when
     *  Home already fetched within [MIN_AUTO_REFRESH_INTERVAL], so flipping
     *  between tabs cannot spray requests. The window is deliberately short:
     *  a member who just booked must still see the new credit count. */
    fun load() {
        val sinceLast = lastFetchStarted?.elapsedNow()
        if (sinceLast != null && sinceLast < MIN_AUTO_REFRESH_INTERVAL) return
        launchFetch()
    }

    /** Pull-to-refresh: re-fetch without flashing the shimmer, keeping the
     *  current content visible (and untouched on a transient failure). Never
     *  throttled — the member asked for this one. */
    fun refresh() {
        _isRefreshing.value = true
        launchFetch { _isRefreshing.value = false }
    }

    /** One fetch in flight at a time: a new one cancels its predecessor so the
     *  newest response is always the one that lands. Without this, two fetches
     *  racing could resolve out of order and briefly show the older data. */
    private fun launchFetch(onSettled: () -> Unit = {}) {
        fetchJob?.cancel()
        lastFetchStarted = timeSource.markNow()
        fetchJob = viewModelScope.launch {
            try {
                fetch()
            } finally {
                onSettled()
            }
        }
    }

    /** Error-state retry: clears back to Loading (so the full-screen error's
     *  retry button can reflect an in-flight attempt) then re-runs the cold load. */
    fun retry() {
        // Claim the flag SYNCHRONOUSLY: setting it inside the coroutine leaves a
        // window between the tap and the coroutine starting, and every tap in
        // that window queues its own fetch.
        if (_retrying.value) return
        _retrying.value = true
        launchFetch { _retrying.value = false }
    }

    fun dismissRefreshFailed() {
        _refreshFailed.value = false
    }

    private companion object {
        /** Long enough to swallow a burst of tab switches, short enough that it
         *  cannot hide a credit change the member just caused. */
        val MIN_AUTO_REFRESH_INTERVAL = 2.seconds
    }

    private suspend fun fetch() {
        try {
            val me = membershipApi.membershipMe()
            val upcoming = runCatching { bookingApi.myBookings().upcoming }.getOrDefault(emptyList())
            _uiState.value = HomeUiState.Success(
                displayName = me.member.displayName ?: me.member.email.substringBefore("@"),
                creditsRemaining = me.currentPeriod?.creditsRemaining,
                upcomingMonth = me.upcomingPeriod?.monthName,
                upcomingCredits = me.upcomingPeriod?.creditsRemaining,
                upcoming = upcoming,
                weekStreak = me.member.weekStreak,
            )
            _refreshFailed.value = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // On a refresh failure keep whatever's already on screen rather than
            // replacing good content with a full-screen error.
            if (_uiState.value !is HomeUiState.Success) {
                _uiState.value = HomeUiState.Error(e.toErrorType())
            } else {
                // Content is already good: a failed refresh must not wipe it.
                // Surface a dismissible notice instead of a takeover.
                _refreshFailed.value = true
            }
        }
    }
}
