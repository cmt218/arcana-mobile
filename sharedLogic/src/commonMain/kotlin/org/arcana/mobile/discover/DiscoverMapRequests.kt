package org.arcana.mobile.discover

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * "Show on the map" from elsewhere in the app (a class page's address sheet).
 * A route argument cannot cross tabs on iOS (each tab owns its NavHost), so the
 * caller posts the location here and the Discover ViewModel takes it whenever
 * it arrives; the same hand-off the Book tab uses for a studio page's Book button.
 */
class DiscoverMapRequests {
    private val _pending = MutableStateFlow<Int?>(null)
    val pending: StateFlow<Int?> = _pending

    /** Counts requests and is never reset: the Discover tab returns to its root
     *  on each new value, whoever consumed [pending] first. */
    private val _count = MutableStateFlow(0)
    val count: StateFlow<Int> = _count

    fun request(locationId: Int) {
        _pending.value = locationId
        _count.value += 1
    }

    fun consume(): Int? = _pending.value.also { _pending.value = null }
}
