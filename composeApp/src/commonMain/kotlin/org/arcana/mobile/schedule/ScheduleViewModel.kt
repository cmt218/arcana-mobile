package org.arcana.mobile.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.client.plugins.ResponseException
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlinx.datetime.toLocalDateTime
import org.arcana.mobile.data.ScheduleSessionDto
import org.arcana.mobile.logWarning
import org.arcana.mobile.networking.ArcanaApiClient

/**
 * Filter state controlled by the chip rail. `studioSlugs.isEmpty()` means the
 * "ALL" chip is conceptually active.
 */
data class ScheduleFilters(
    val studioSlugs: Set<String> = emptySet(),
    val availableOnly: Boolean = false,
)

sealed interface ScheduleUiState {
    data object Loading : ScheduleUiState
    data class Success(
        /** Today through today + 13 days, oldest first. */
        val days: List<LocalDate>,
        /** Map from each date in [days] to the sessions on that date. Empty list when no sessions. */
        val sessionsByDay: Map<LocalDate, List<ScheduleSessionDto>>,
        /** Distinct studios appearing in the unfiltered 14-day fetch, for chip rendering. */
        val knownStudios: List<StudioChipData>,
        val filters: ScheduleFilters,
    ) : ScheduleUiState
    data class Error(val message: String) : ScheduleUiState
}

/** Minimal data the chip rail needs to render a studio chip. */
data class StudioChipData(
    val slug: String,
    val name: String,
    val primaryColor: String,
)

class ScheduleViewModel(
    private val api: ArcanaApiClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ScheduleUiState>(ScheduleUiState.Loading)
    val uiState: StateFlow<ScheduleUiState> = _uiState

    /**
     * Unfiltered 14-day result, kept around so toggling chips refilters
     * client-side for studio chips (cheap, instant) while `availableOnly`
     * still drives a re-fetch (because visibility-strip is a server contract).
     *
     * Studios chips that the user has *enabled* are applied client-side;
     * the `availableOnly` toggle is sent up to the server because it changes
     * which rows the server even returns.
     */
    private var unfilteredCache: List<ScheduleSessionDto> = emptyList()
    private var days: List<LocalDate> = emptyList()
    private var filters: ScheduleFilters = ScheduleFilters()

    init {
        reload()
    }

    /** Force a network re-fetch using the current filter state. */
    fun reload() {
        viewModelScope.launch {
            _uiState.value = ScheduleUiState.Loading
            try {
                val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
                days = (0 until WINDOW_DAYS).map { today.plus(it, DateTimeUnit.DAY) }
                val sessions = api.fetchSchedule(
                    from = today,
                    to = today.plus(WINDOW_DAYS - 1, DateTimeUnit.DAY),
                    availableOnly = filters.availableOnly,
                )
                unfilteredCache = sessions
                publish()
            } catch (e: ResponseException) {
                val code = e.response.status.value
                logWarning("ScheduleViewModel", e.message ?: "HTTP $code")
                _uiState.value = ScheduleUiState.Error("server error $code")
            } catch (e: Exception) {
                logWarning("ScheduleViewModel", e.message ?: "Unknown error")
                _uiState.value = ScheduleUiState.Error("server error")
            }
        }
    }

    /** Toggle a studio in/out of the chip filter. Re-renders client-side; no network. */
    fun toggleStudio(slug: String) {
        val next = filters.studioSlugs.toMutableSet().apply {
            if (!add(slug)) remove(slug)
        }
        filters = filters.copy(studioSlugs = next)
        publish()
    }

    /** Clear all studio selections (the "ALL" chip behavior). Re-renders client-side. */
    fun clearStudios() {
        if (filters.studioSlugs.isEmpty()) return
        filters = filters.copy(studioSlugs = emptySet())
        publish()
    }

    /** Toggle `available_only`. Re-fetches because the server applies this filter. */
    fun toggleAvailableOnly() {
        filters = filters.copy(availableOnly = !filters.availableOnly)
        reload()
    }

    /**
     * Build the Success state from the cached fetch + current filters.
     * Studio chips filter client-side; availableOnly is already applied server-side.
     */
    private fun publish() {
        val tz = TimeZone.currentSystemDefault()

        // Distinct studios from the unfiltered fetch — chips reflect the
        // 14-day fleet, not just today's slice.
        val knownStudios = unfilteredCache
            .map { it.location.studio }
            .distinctBy { it.slug }
            .sortedBy { it.name }
            .map { StudioChipData(slug = it.slug, name = it.name, primaryColor = it.primaryColor) }

        val filtered = if (filters.studioSlugs.isEmpty()) {
            unfilteredCache
        } else {
            unfilteredCache.filter { it.location.studio.slug in filters.studioSlugs }
        }

        val byDay: Map<LocalDate, List<ScheduleSessionDto>> = days.associateWith { date ->
            filtered.filter { Instant.parse(it.startAt).toLocalDateTime(tz).date == date }
        }

        _uiState.value = ScheduleUiState.Success(
            days = days,
            sessionsByDay = byDay,
            knownStudios = knownStudios,
            filters = filters,
        )
    }

    companion object {
        /** Matches the server's 14-day max window (spec §3.1). */
        const val WINDOW_DAYS: Int = 14
    }
}
