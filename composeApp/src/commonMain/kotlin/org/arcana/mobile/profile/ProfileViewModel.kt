package org.arcana.mobile.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
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
        // Refresh favorites alongside the membership fetch. The repository
        // swallows failures (keeps the prior cached value), so a favorites
        // hiccup never blocks the profile itself.
        favoritesRepository.refresh()
        try {
            val me = api.membershipMe()
            // Identify on the first /me of every authenticated launch. This VM's
            // load() runs from MainScaffold for the avatar, so it covers returning
            // already-logged-in users and account switches — not just post-login.
            // Telemetry dedupes per session, so calling it on each refresh is safe.
            telemetry.identify(me.member.id.toString(), me.member.email, me.member.displayName)
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
            // On a refresh failure keep whatever's already on screen rather than
            // replacing good content with a full-screen error.
            if (_uiState.value !is ProfileUiState.Success) {
                _uiState.value = ProfileUiState.Error(e.toErrorType())
            }
        }
    }
}
