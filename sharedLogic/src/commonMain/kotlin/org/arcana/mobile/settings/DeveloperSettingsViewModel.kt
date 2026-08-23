package org.arcana.mobile.settings

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.arcana.mobile.networking.BaseUrlProvider

/**
 * Backs the Developer Settings screen — currently just the API base URL
 * override (paired with Cloudflare quick-tunnel for cofounder testing).
 *
 * Editing flow:
 *   1. Screen reads [currentUrl] from the provider on entry.
 *   2. User edits the input; [draft] tracks the working value.
 *   3. [save] validates and persists; emits [Status.Saved] on success or
 *      [Status.Error] with a message on invalid input.
 *   4. [reset] clears the override; the provider reverts to its default.
 *
 * `ArcanaApiClient` re-reads the URL per request, so changes take effect
 * on the very next outbound call — no client recreation, no app restart.
 */
class DeveloperSettingsViewModel(
    private val baseUrlProvider: BaseUrlProvider,
) : ViewModel() {

    sealed interface Status {
        data object Idle : Status
        data object Saved : Status
        data class Error(val message: String) : Status
    }

    private val _draft = MutableStateFlow(baseUrlProvider.get())
    val draft: StateFlow<String> = _draft

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status

    val currentUrl: StateFlow<String> = baseUrlProvider.current
    val defaultUrl: String = baseUrlProvider.defaultUrl
    val isOverridden: Boolean
        get() = baseUrlProvider.isOverridden

    /** Re-sync the draft with what is actually persisted. The screen is a
     *  composition toggle, not a nav destination, so this ViewModel outlives it
     *  and a discarded edit would otherwise reappear as if it were live. */
    fun resetState() {
        _draft.value = baseUrlProvider.get()
        _status.value = Status.Idle
    }

    fun onDraftChange(value: String) {
        _draft.value = value
        if (_status.value !is Status.Idle) _status.value = Status.Idle
    }

    fun save() {
        try {
            baseUrlProvider.set(_draft.value)
            _status.value = Status.Saved
        } catch (e: IllegalArgumentException) {
            _status.value = Status.Error(e.message ?: "Invalid URL")
        }
    }

    fun reset() {
        baseUrlProvider.reset()
        _draft.value = baseUrlProvider.get()
        _status.value = Status.Saved
    }
}
