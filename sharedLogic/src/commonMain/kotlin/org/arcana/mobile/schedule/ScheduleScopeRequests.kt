package org.arcana.mobile.schedule

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A studio page's Book button: the brand and the location ids the Book
 * tab should scope to. Every location of the brand, or the one the member came
 * to the page from (a map pin). [label] is the chip's words when the Book tab's
 * own catalog cannot supply them.
 */
data class ScheduleScopeRequest(
    val brandSlug: String,
    val brandName: String,
    val locationIds: List<Int>,
    val label: String = brandName,
)

/**
 * Hand-off from the Discover tab to the Book tab. A route argument cannot cross
 * tabs on iOS (each tab owns its NavHost), so the studio page posts here and
 * the schedule ViewModel consumes the request whenever it arrives.
 */
class ScheduleScopeRequests {
    private val _pending = MutableStateFlow<ScheduleScopeRequest?>(null)
    val pending: StateFlow<ScheduleScopeRequest?> = _pending

    fun request(request: ScheduleScopeRequest) {
        _pending.value = request
    }

    fun consume(): ScheduleScopeRequest? = _pending.value.also { _pending.value = null }
}
