package org.arcana.mobile.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock
import org.arcana.mobile.analytics.Telemetry
import org.arcana.mobile.data.ScheduleSessionDto
import org.arcana.mobile.logWarning
import org.arcana.mobile.networking.ScheduleApi
import org.arcana.mobile.networking.ErrorType
import org.arcana.mobile.networking.telemetryReasonFor
import org.arcana.mobile.networking.toErrorType

sealed interface ClassDetailUiState {
    data object Loading : ClassDetailUiState
    data class Success(val session: ScheduleSessionDto) : ClassDetailUiState
    data class Error(val type: ErrorType) : ClassDetailUiState
}

class ClassDetailViewModel(
    private val api: ScheduleApi,
    private val sessionId: Int,
    private val telemetry: Telemetry = Telemetry.Noop,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ClassDetailUiState>(ClassDetailUiState.Loading)
    val uiState: StateFlow<ClassDetailUiState> = _uiState

    /** Drives the pull-to-refresh spinner; true only during a [refresh] fetch. */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    /** True while [retry] is in flight. The error stays on screen and the retry
     *  button carries the progress. */
    private val _retrying = MutableStateFlow(false)
    val retrying: StateFlow<Boolean> = _retrying

    init {
        reload()
    }

    /** Retry from the error state. Unlike [reload] this does NOT drop to
     *  Loading: the error stays put so a failed retry can't flash the shimmer
     *  and land back on the same error. */
    fun retry() {
        // Claimed synchronously so taps arriving before the coroutine starts
        // don't each queue their own fetch.
        if (_retrying.value) return
        _retrying.value = true
        viewModelScope.launch {
            try {
                fetch(isView = true)
            } finally {
                _retrying.value = false
            }
        }
    }

    /** Force a re-fetch, flashing the shimmer. First load only — an error-retry
     *  goes through [retry] so it doesn't wipe the error off screen. */
    fun reload() {
        viewModelScope.launch {
            _uiState.value = ClassDetailUiState.Loading
            // `isView = true` so the first load (and an error-retry) counts as a
            // class view; pull-to-refresh below does not.
            fetch(isView = true)
        }
    }

    /** Pull-to-refresh: re-fetch without flashing the shimmer, keeping the
     *  current content visible (and untouched on a transient failure). */
    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                fetch(isView = false)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private suspend fun fetch(isView: Boolean) {
        val startedAt = Clock.System.now()
        try {
            val session = api.fetchClassDetail(sessionId)
            _uiState.value = ClassDetailUiState.Success(session)
            if (isView) {
                val studio = session.location.studio
                telemetry.classViewed(
                    sessionId = sessionId,
                    studioId = studio.id,
                    studioName = studio.name,
                    locationId = session.location.id,
                    locationName = session.location.name,
                    modality = session.template.modality,
                    spotsAvailable = session.arcanaSpotsAvailable,
                    requiresSpot = session.template.spotSelectionMode != "none",
                    isFull = session.arcanaSpotsAvailable <= 0,
                    loadMs = (Clock.System.now() - startedAt).inWholeMilliseconds,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val type = e.toErrorType()
            logWarning("ClassDetailViewModel", e.message ?: "class load failed")
            // On a refresh failure keep whatever's already on screen rather than
            // replacing good content with a full-screen error.
            if (_uiState.value !is ClassDetailUiState.Success) {
                _uiState.value = ClassDetailUiState.Error(type)
            }
            if (isView) {
                telemetry.classViewFailed(sessionId, e.telemetryReasonFor())
            }
        }
    }
}
