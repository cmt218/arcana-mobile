package org.arcana.mobile.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.arcana.mobile.networking.ConciergeApi
import org.arcana.mobile.networking.ConciergeError

sealed interface DeleteAccountState {
    data object Idle : DeleteAccountState
    data object Submitting : DeleteAccountState
    data object Sent : DeleteAccountState
    data class Failed(val code: String) : DeleteAccountState
}

/**
 * Account-deletion request.
 *
 * Apple's Guideline 5.1.1(v) requires members to be able to *initiate* account
 * deletion from within the app; it does NOT require instant deletion. We reuse
 * the concierge pipeline rather than add a dedicated endpoint: the request lands
 * server-side, fires a Telegram ops alert (exactly like a new concierge request),
 * and the founders complete the deletion manually within 30 days. The async path
 * also means a reviewer tapping "Delete account" on the demo account won't
 * destroy it mid-review.
 */
class DeleteAccountViewModel(
    private val conciergeApi: ConciergeApi,
) : ViewModel() {

    private val _state = MutableStateFlow<DeleteAccountState>(DeleteAccountState.Idle)
    val state: StateFlow<DeleteAccountState> = _state

    fun submit() {
        if (_state.value is DeleteAccountState.Submitting) return
        _state.value = DeleteAccountState.Submitting
        viewModelScope.launch {
            try {
                conciergeApi.createConciergeRequest(DELETION_MESSAGE)
                _state.value = DeleteAccountState.Sent
            } catch (e: ConciergeError) {
                _state.value = DeleteAccountState.Failed(e.code)
            } catch (_: Exception) {
                _state.value = DeleteAccountState.Failed("delete_failed")
            }
        }
    }

    /** Dismiss the success/failure dialog and return to idle. */
    fun reset() {
        _state.value = DeleteAccountState.Idle
    }

    companion object {
        const val DELETION_MESSAGE =
            "ACCOUNT DELETION REQUEST — Member tapped Delete Account in the app and " +
                "confirmed. Please permanently delete this account and its associated data " +
                "within 30 days."
    }
}
