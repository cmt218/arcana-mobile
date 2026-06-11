package org.arcana.mobile.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.client.plugins.ResponseException
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlinx.datetime.toLocalDateTime
import org.arcana.mobile.data.FavoritesDto
import org.arcana.mobile.data.LocationBriefDto
import org.arcana.mobile.data.ScheduleSessionDto
import org.arcana.mobile.data.StudioDto
import org.arcana.mobile.favorites.FavoritesRepository
import org.arcana.mobile.logWarning
import org.arcana.mobile.networking.FavoritesApi
import org.arcana.mobile.networking.ScheduleApi

/**
 * Filter state controlled by the chip rails.
 *
 * - `studioSlugs.isEmpty()` ⇒ the brand "ALL" chip is conceptually active.
 * - `locationIds` is only meaningful when exactly one brand is soloed
 *   (the two-tier filter from `design_handoff_schedule_v2`). Whenever the
 *   brand selection changes, locations are cleared by [ScheduleViewModel].
 */
data class ScheduleFilters(
    val studioSlugs: Set<String> = emptySet(),
    val locationIds: Set<Int> = emptySet(),
    val availableOnly: Boolean = false,
)

sealed interface ScheduleUiState {
    data object Loading : ScheduleUiState
    data class Success(
        /** Today through today + 13 days, oldest first. */
        val days: List<LocalDate>,
        /** Map from each date in [days] to the sessions on that date AFTER filters. */
        val sessionsByDay: Map<LocalDate, List<ScheduleSessionDto>>,
        /** Total session count per day BEFORE filters — drives the
         *  "5 of 10 classes" subtitle in the day banner. */
        val totalCountByDay: Map<LocalDate, Int>,
        /** Distinct studios appearing in the unfiltered 14-day fetch, for chip rendering. */
        val knownStudios: List<StudioChipData>,
        /** Distinct location count across the entire unfiltered 14-day fetch.
         *  Drives the "N STUDIOS · M SITES" header chip. */
        val siteCount: Int,
        /** Locations to surface in the tier-2 sub-row. Non-empty iff exactly
         *  one brand is soloed; otherwise the screen hides the sub-row. */
        val knownLocationsForBrand: List<LocationChipData>,
        val filters: ScheduleFilters,
        /** True while the schedule is server-filtered to the member's
         *  favorite locations (the FAVORITES chip is the active filter). */
        val favoritesMode: Boolean,
        /** Favorites loaded and non-empty — gates the FAVORITES chip and
         *  suppresses the "choose favorites" nudge banner. */
        val hasFavorites: Boolean,
        /** False when the favorites fetch failed (state unknown) — the nudge
         *  must not show to a member who may already have favorites. */
        val favoritesKnown: Boolean,
    ) : ScheduleUiState
    data class Error(val message: String) : ScheduleUiState
}

/** Minimal data the chip rail needs to render a studio chip. */
data class StudioChipData(
    val slug: String,
    val name: String,
    val primaryColor: String,
)

/** Tier-2 location chip data — only surfaced for the soloed brand. */
data class LocationChipData(
    val id: Int,
    /** The display label, e.g. "WILLIAMSBURG" — already uppercased and
     *  brand-prefix-stripped. See [shortLabel] for the derivation. */
    val shortLabel: String,
)

/**
 * Display-friendly short location label, derived from `name`. The backend
 * names locations like "YO BK Williamsburg"; we strip the studio prefix so
 * chips and row metadata can read "WILLIAMSBURG" rather than repeating the
 * brand. Shared helper so VM (chip generation) and screens (row + detail
 * meta lines) all produce the same string.
 */
internal fun LocationBriefDto.shortLabel(): String {
    val raw = name.removePrefix(studio.name).trim()
        .removePrefix("·").trim()
        .removePrefix("-").trim()
    return (raw.ifEmpty { name }).uppercase()
}

class ScheduleViewModel(
    private val api: ScheduleApi,
    private val favoritesRepository: FavoritesRepository,
    private val favoritesApi: FavoritesApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ScheduleUiState>(ScheduleUiState.Loading)
    val uiState: StateFlow<ScheduleUiState> = _uiState

    /** Drives the pull-to-refresh spinner; true only during a [refresh] fetch. */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

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

    /** True while the schedule is server-filtered to the member's favorite
     *  locations. Defaults on at startup when the member has favorites;
     *  toggling any explicit studio filter exits the mode. */
    private var favoritesMode: Boolean = false

    /** Full active-Partner directory (`GET /studios/`) so the chip rail shows
     *  every Partner — not just those with sessions in the current (possibly
     *  favorites-narrowed) fetch. Empty if the fetch failed; publish() falls
     *  back to the cache-derived list. */
    private var studioDirectory: List<StudioDto> = emptyList()

    /** The favorites value this VM last acted on — lets the repository
     *  collector below ignore the init-time value and only react to changes
     *  made elsewhere (the favorites manager saving a new set). */
    private var lastAppliedFavorites: FavoritesDto? = null

    init {
        viewModelScope.launch {
            _uiState.value = ScheduleUiState.Loading
            // Favorites first — they decide whether the first fetch is
            // narrowed to the member's locations.
            val favorites = favoritesRepository.refresh()
            if (favorites != null && !favorites.isEmpty()) favoritesMode = true
            lastAppliedFavorites = favorites
            try {
                studioDirectory = favoritesApi.fetchStudios()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Tolerated: chips fall back to the cache-derived list.
                logWarning("ScheduleViewModel", e.message ?: "studio directory fetch failed")
            }
            fetch()
            // This VM outlives navigation (session-scoped store), so react to
            // favorites saved in the manager: re-enter (or exit) favorites
            // mode and re-fetch with the new scope. The collector replays the
            // current StateFlow value first; `lastAppliedFavorites` makes that
            // initial replay a no-op.
            favoritesRepository.favorites.collect { favs ->
                if (favs == null) return@collect // logout clear; VM is being torn down
                if (favs == lastAppliedFavorites) return@collect
                lastAppliedFavorites = favs
                favoritesMode = !favs.isEmpty()
                filters = filters.copy(studioSlugs = emptySet(), locationIds = emptySet())
                reload()
            }
        }
    }

    /** Force a network re-fetch using the current filter state. Flashes the
     *  shimmer placeholder — for first load, filter changes, and error-retry. */
    fun reload() {
        viewModelScope.launch {
            _uiState.value = ScheduleUiState.Loading
            fetch()
        }
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
        try {
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
            days = (0 until WINDOW_DAYS).map { today.plus(it, DateTimeUnit.DAY) }
            // In favorites mode the fetch narrows server-side to the favorite
            // locations. The takeIf guard matters: a studio-grain favorite with
            // zero active locations must NOT become an empty `location_id=`
            // param — the server treats empty as no-filter anyway, so we simply
            // don't send it (show-all is correct: the favorite matches nothing).
            val favoriteLocationIds: List<Int>? = if (favoritesMode) {
                favoritesRepository.favorites.value
                    ?.expandedLocationIds()
                    ?.takeIf { it.isNotEmpty() }
            } else {
                null
            }
            val sessions = api.fetchSchedule(
                from = today,
                to = today.plus(WINDOW_DAYS - 1, DateTimeUnit.DAY),
                locationIds = favoriteLocationIds,
                availableOnly = filters.availableOnly,
            )
            unfilteredCache = sessions
            publish()
        } catch (e: ResponseException) {
            val code = e.response.status.value
            logWarning("ScheduleViewModel", e.message ?: "HTTP $code")
            // On a refresh failure keep whatever's already on screen rather than
            // replacing good content with a full-screen error.
            if (_uiState.value !is ScheduleUiState.Success) {
                _uiState.value = ScheduleUiState.Error("server error $code")
            }
        } catch (e: Exception) {
            logWarning("ScheduleViewModel", e.message ?: "Unknown error")
            if (_uiState.value !is ScheduleUiState.Success) {
                _uiState.value = ScheduleUiState.Error("server error")
            }
        }
    }

    /** Re-enter favorites mode (the FAVORITES chip). No-op unless the member
     *  actually has favorites. Clears explicit studio/location filters — the
     *  favorites narrow happens server-side, so this re-fetches. */
    fun enterFavoritesMode() {
        if (favoritesMode) return // already active — don't flash a pointless reload
        val favorites = favoritesRepository.favorites.value
        if (favorites == null || favorites.isEmpty()) return
        favoritesMode = true
        filters = filters.copy(studioSlugs = emptySet(), locationIds = emptySet())
        reload()
    }

    /** Toggle a studio in/out of the chip filter. Re-renders client-side; no network.
     *  Always clears any tier-2 location selection — locations only make sense
     *  when exactly one brand is soloed, and the user's brand-toggle has just
     *  invalidated that assumption.
     *
     *  From favorites mode, tapping a studio chip exits the mode and solos that
     *  studio: the cache only holds favorite locations, so this re-fetches the
     *  full window rather than refiltering client-side. */
    fun toggleStudio(slug: String) {
        if (favoritesMode) {
            favoritesMode = false
            filters = ScheduleFilters(studioSlugs = setOf(slug), availableOnly = filters.availableOnly)
            reload()
            return
        }
        val next = filters.studioSlugs.toMutableSet().apply {
            if (!add(slug)) remove(slug)
        }
        filters = filters.copy(studioSlugs = next, locationIds = emptySet())
        publish()
    }

    /** Clear all studio + location selections (the "ALL" chip behavior).
     *  From favorites mode this exits the mode — the cache only holds the
     *  favorites-narrowed window, so showing "all" needs a re-fetch. */
    fun clearStudios() {
        if (favoritesMode) {
            favoritesMode = false
            filters = filters.copy(studioSlugs = emptySet(), locationIds = emptySet())
            reload()
            return
        }
        if (filters.studioSlugs.isEmpty() && filters.locationIds.isEmpty()) return
        filters = filters.copy(studioSlugs = emptySet(), locationIds = emptySet())
        publish()
    }

    /** Toggle a location in/out of the tier-2 sub-filter. No-op unless exactly
     *  one brand is soloed — the sub-row isn't on screen otherwise. */
    fun toggleLocation(id: Int) {
        if (filters.studioSlugs.size != 1) return
        val next = filters.locationIds.toMutableSet().apply {
            if (!add(id)) remove(id)
        }
        filters = filters.copy(locationIds = next)
        publish()
    }

    /** Clear all location selections (the tier-2 "ALL" chip). */
    fun clearLocations() {
        if (filters.locationIds.isEmpty()) return
        filters = filters.copy(locationIds = emptySet())
        publish()
    }

    /** Toggle `available_only`. Re-fetches because the server applies this filter. */
    fun toggleAvailableOnly() {
        filters = filters.copy(availableOnly = !filters.availableOnly)
        reload()
    }

    /**
     * Build the Success state from the cached fetch + current filters.
     *
     * - Studio + location chips filter client-side from `unfilteredCache`.
     * - `availableOnly` is already applied server-side at fetch time.
     * - `totalCountByDay` is computed from the unfiltered cache so the day
     *   banner can show "N of M classes" even when filters are active.
     * - Tier-2 location chips are only populated when exactly one brand is
     *   soloed — otherwise the screen hides the sub-row entirely.
     */
    private fun publish() {
        val tz = TimeZone.currentSystemDefault()

        // Chip rail studios: prefer the full Partner directory so every active
        // Partner gets a chip even when the current fetch is favorites-narrowed
        // (or a Partner simply has no sessions in the window). Fall back to the
        // cache-derived list if the directory fetch failed.
        val knownStudios = if (studioDirectory.isNotEmpty()) {
            studioDirectory
                .sortedBy { it.name }
                .map { StudioChipData(slug = it.slug, name = it.name, primaryColor = it.primaryColor) }
        } else {
            unfilteredCache
                .map { it.location.studio }
                .distinctBy { it.slug }
                .sortedBy { it.name }
                .map { StudioChipData(slug = it.slug, name = it.name, primaryColor = it.primaryColor) }
        }

        // Tier-2 location chips: only when one brand is soloed.
        val knownLocationsForBrand: List<LocationChipData> = if (filters.studioSlugs.size == 1) {
            val soloed = filters.studioSlugs.single()
            unfilteredCache
                .map { it.location }
                .filter { it.studio.slug == soloed }
                .distinctBy { it.id }
                .sortedBy { it.name }
                .map { LocationChipData(id = it.id, shortLabel = it.shortLabel()) }
        } else {
            emptyList()
        }

        val filtered = unfilteredCache.filter { s ->
            (filters.studioSlugs.isEmpty() || s.location.studio.slug in filters.studioSlugs) &&
                (filters.locationIds.isEmpty() || s.location.id in filters.locationIds)
        }

        // Hide sessions that have already started. On the "today" column a class
        // whose start time is in the past is non-actionable — there's no use case
        // for opening it, and the detail fetch errors for a session that's already
        // begun. Applied to both the count and the list so the day banner's
        // "N classes" and the rail's dots reflect only what's still bookable.
        // Future days are unaffected: the 14-day window starts at today, so no
        // session on a later day can be before `now`.
        val now = Clock.System.now()
        fun ScheduleSessionDto.isUpcoming(): Boolean = Instant.parse(startAt) >= now

        // Pre-bucket both unfiltered and filtered sessions by day so the
        // screen can read counts in O(1) for the banner.
        val totalByDay: Map<LocalDate, Int> = days.associateWith { date ->
            unfilteredCache.count { it.isUpcoming() && Instant.parse(it.startAt).toLocalDateTime(tz).date == date }
        }
        val byDay: Map<LocalDate, List<ScheduleSessionDto>> = days.associateWith { date ->
            filtered.filter { it.isUpcoming() && Instant.parse(it.startAt).toLocalDateTime(tz).date == date }
        }

        val siteCount = unfilteredCache.map { it.location.id }.toSet().size

        _uiState.value = ScheduleUiState.Success(
            days = days,
            sessionsByDay = byDay,
            totalCountByDay = totalByDay,
            knownStudios = knownStudios,
            siteCount = siteCount,
            knownLocationsForBrand = knownLocationsForBrand,
            filters = filters,
            favoritesMode = favoritesMode,
            hasFavorites = favoritesRepository.favorites.value?.isEmpty() == false,
            favoritesKnown = favoritesRepository.favorites.value != null,
        )
    }

    companion object {
        /** Matches the server's 14-day max window (spec §3.1). */
        const val WINDOW_DAYS: Int = 14
    }
}
