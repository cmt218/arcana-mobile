package org.arcana.mobile.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.arcana.mobile.data.FavoritesDto
import org.arcana.mobile.favorites.FavoritesRepository
import org.arcana.mobile.networking.MembershipApi

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
        val lifetimeSessions: Int,
        val weekStreak: Int,
    ) : ProfileUiState
    data class Error(val message: String) : ProfileUiState
}

class ProfileViewModel(
    private val api: MembershipApi,
    private val favoritesRepository: FavoritesRepository,
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
            _uiState.value = ProfileUiState.Success(
                fullName = me.member.displayName ?: me.member.email,
                initials = me.member.avatarInitials,
                memberNumber = me.member.memberNumber,
                memberSince = me.member.memberSince,
                status = me.membership.status,
                tierName = me.membership.tier.name,
                creditsRemaining = me.currentPeriod?.creditsRemaining,
                creditsGranted = me.currentPeriod?.creditsGranted,
                lifetimeSessions = me.member.lifetimeSessions,
                weekStreak = me.member.weekStreak,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // On a refresh failure keep whatever's already on screen rather than
            // replacing good content with a full-screen error.
            if (_uiState.value !is ProfileUiState.Success) {
                _uiState.value = ProfileUiState.Error("server error")
            }
        }
    }
}
