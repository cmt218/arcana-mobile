package org.arcana.mobile.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.arcana.mobile.analytics.Telemetry
import org.arcana.mobile.data.FavoritesDto
import org.arcana.mobile.favorites.FavoritesRepository
import org.arcana.mobile.networking.ErrorType
import org.arcana.mobile.networking.MembershipApi
import org.arcana.mobile.networking.toErrorType

sealed interface ProfileUiState {
    data object Loading : ProfileUiState
    data class Success(
        val fullName: String,
        val initials: String,
        val memberNumber: String?,
        val memberSince: String?,
        val status: String,
        val tierName: String,
        val creditsRemaining: Int?,
        val creditsGranted: Int?,
        // The next-month wallet (month + credits), set only when a member holds
        // next month's wallet while still in the current month. Null otherwise.
        val upcomingMonth: String?,
        val upcomingCredits: Int?,
        val lifetimeSessions: Int,
        val weekStreak: Int,
    ) : ProfileUiState
    data class Error(val type: ErrorType) : ProfileUiState
}

class ProfileViewModel(
    private val api: MembershipApi,
    private val favoritesRepository: FavoritesRepository,
    private val telemetry: Telemetry = Telemetry.Noop,
) : ViewModel() {
    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState: StateFlow<ProfileUiState> = _uiState

    /** Member favorites for the "Your favorites" section. `null` = not loaded
     *  yet; an empty [FavoritesDto] = loaded, member has none. */
    val favorites: StateFlow<FavoritesDto?> = favoritesRepository.favorites

    /** Drives the pull-to-refresh spinner; true only during a [refresh] fetch. */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    /** True when a background refresh failed while [ProfileUiState.Success] was
     *  already on screen — mirrors HomeViewModel. A cold-load failure is the
     *  Error state and a full-screen takeover instead; the snackbar is only for
     *  "your content is still good, the refresh wasn't". */
    private val _refreshFailed = MutableStateFlow(false)
    val refreshFailed: StateFlow<Boolean> = _refreshFailed

    /** True while [retry] is in flight; the error stays on screen and the retry
     *  button carries the progress. */
    private val _retrying = MutableStateFlow(false)
    val retrying: StateFlow<Boolean> = _retrying

    /** Set only when the favorites fetch failed AND there is nothing cached to
     *  show. With a cached value we keep showing it, which is the whole point of
     *  the repository swallowing failures. Without this the section had no way
     *  to say it failed and shimmered as though still loading. */
    private val _favoritesError = MutableStateFlow<ErrorType?>(null)
    val favoritesError: StateFlow<ErrorType?> = _favoritesError

    private val _favoritesRetrying = MutableStateFlow(false)
    val favoritesRetrying: StateFlow<Boolean> = _favoritesRetrying

    /** Retry just the favorites section, leaving the rest of the profile alone. */
    fun retryFavorites() {
        if (_favoritesRetrying.value) return
        _favoritesRetrying.value = true
        viewModelScope.launch {
            try {
                fetchFavorites()
            } finally {
                _favoritesRetrying.value = false
            }
        }
    }

    private var fetchJob: Job? = null

    fun load() {
        launchFetch()
    }

    /** One fetch in flight at a time, as in [org.arcana.mobile.home.HomeViewModel]:
     *  a later call cancels its predecessor so the newest response is the one
     *  that lands. Both the session warm-load and the screen fire this. */
    private fun launchFetch(onSettled: () -> Unit = {}) {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            try {
                fetch()
            } finally {
                onSettled()
            }
        }
    }

    /** Retry from the full-screen error. Never resets to Loading: the error
     *  stays put and the button shows the dot-matrix loader. */
    fun retry() {
        if (_retrying.value) return
        _retrying.value = true
        launchFetch { _retrying.value = false }
    }

    fun dismissRefreshFailed() {
        _refreshFailed.value = false
    }

    /** Runs alongside the membership fetch and never blocks it: a favorites
     *  hiccup must not take down the profile. */
    private suspend fun fetchFavorites() {
        val outcome = favoritesRepository.refreshCatching()
        _favoritesError.value = outcome.exceptionOrNull()
            ?.takeIf { favorites.value == null }
            ?.toErrorType()
    }

    /** Pull-to-refresh: re-fetch without flashing the shimmer, keeping the
     *  current content visible (and untouched on a transient failure). */
    fun refresh() {
        _isRefreshing.value = true
        launchFetch { _isRefreshing.value = false }
    }

    private suspend fun fetch() {
        fetchFavorites()
        try {
            val me = api.membershipMe()
            // Identify on the first /me of every authenticated launch. This VM's
            // load() runs from MainScaffold for the avatar, so it covers returning
            // already-logged-in users and account switches — not just post-login.
            // Telemetry dedupes per session, so calling it on each refresh is safe.
            telemetry.identify(me.member.id.toString(), me.member.email, me.member.displayName)
            _refreshFailed.value = false
            _uiState.value = ProfileUiState.Success(
                fullName = me.member.displayName ?: me.member.email,
                initials = me.member.avatarInitials,
                memberNumber = me.member.memberNumber,
                memberSince = me.member.memberSince,
                status = me.membership.status,
                tierName = me.membership.tier.name,
                creditsRemaining = me.currentPeriod?.creditsRemaining,
                creditsGranted = me.currentPeriod?.creditsGranted,
                upcomingMonth = me.upcomingPeriod?.monthName,
                upcomingCredits = me.upcomingPeriod?.creditsRemaining,
                lifetimeSessions = me.member.lifetimeSessions,
                weekStreak = me.member.weekStreak,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Keep good content on a refresh failure and flag it for the
            // snackbar; only a cold load with nothing on screen takes over.
            if (_uiState.value is ProfileUiState.Success) {
                _refreshFailed.value = true
            } else {
                _uiState.value = ProfileUiState.Error(e.toErrorType())
            }
        }
    }
}
