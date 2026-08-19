package org.arcana.mobile.concierge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.arcana.mobile.analytics.Telemetry
import org.arcana.mobile.networking.ConciergeApi
import org.arcana.mobile.networking.ConciergeError
import org.arcana.mobile.networking.transportFailureCode

sealed interface ConciergeSubmit {
    data object Idle : ConciergeSubmit
    data object Submitting : ConciergeSubmit
    data object Sent : ConciergeSubmit
    data class Failed(val code: String) : ConciergeSubmit
}

class ConciergeRequestViewModel(
    private val conciergeApi: ConciergeApi,
    private val telemetry: Telemetry = Telemetry.Noop,
) : ViewModel() {

    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message

    private val _submitState = MutableStateFlow<ConciergeSubmit>(ConciergeSubmit.Idle)
    val submitState: StateFlow<ConciergeSubmit> = _submitState

    fun updateMessage(value: String) {
        // Truncate (don't reject) so a long paste keeps its first chars.
        _message.value = value.take(MESSAGE_MAX_LENGTH)
        // A fresh edit clears a prior failure so the CTA re-enables.
        if (_submitState.value is ConciergeSubmit.Failed) _submitState.value = ConciergeSubmit.Idle
    }

    val canSubmit: Boolean
        get() = _message.value.isNotBlank() && _submitState.value !is ConciergeSubmit.Submitting

    fun submit() {
        if (!canSubmit) return
        _submitState.value = ConciergeSubmit.Submitting
        viewModelScope.launch {
            try {
                conciergeApi.createConciergeRequest(_message.value.trim())
                telemetry.conciergeSubmitted()
                _submitState.value = ConciergeSubmit.Sent
            } catch (e: CancellationException) {
                throw e
            } catch (e: ConciergeError) {
                telemetry.conciergeFailed(e.code)
                _submitState.value = ConciergeSubmit.Failed(e.code)
            } catch (e: Exception) {
                val code = e.transportFailureCode()
                telemetry.conciergeFailed(code)
                _submitState.value = ConciergeSubmit.Failed(code)
            }
        }
    }

    companion object {
        const val MESSAGE_MAX_LENGTH = 1000
    }
}
