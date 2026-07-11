package org.arcana.mobile.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.client.plugins.ResponseException
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import org.arcana.mobile.analytics.Telemetry
import org.arcana.mobile.data.FavoritesDto
import org.arcana.mobile.data.LocationBriefDto
import org.arcana.mobile.data.OverviewStudioDto
import org.arcana.mobile.data.ScheduleSessionDto
import org.arcana.mobile.favorites.FavoritesRepository
import org.arcana.mobile.logWarning
import org.arcana.mobile.networking.BookingApi
import org.arcana.mobile.networking.ScheduleApi
import org.arcana.mobile.ui.studioLocationLabel

/**
 * Filter state controlled by the chip rails.
 *
 * - `studioSlugs.isEmpty()` ⇒ the brand "ALL" chip is conceptually active.
 * - `locationIds` is only meaningful when exactly one brand is soloed
 *   (the two-tier filter from `design_handoff_schedule_v2`). Whenever the
 *   brand selection changes, locations are cleared by [ScheduleViewModel].
 *
 * Since Phase 2 every filter narrows SERVER-side: a change feeds the
 * debounced refetch pipeline rather than refiltering a client cache.
 */
data class ScheduleFilters(
    val studioSlugs: Set<String> = emptySet(),
    val locationIds: Set<Int> = emptySet(),
)

/**
 * The studio/location SCOPE — a toggle, exactly one active. This is tier 1 of
 * the two-tier filter model; the Time + Modalities overlays (tier 2) AND on top
 * of whichever scope is selected.
 *
 * - [Favorites]: scoped to the member's saved favorites (the default when they
 *   have any). The panel shows favorites read-only — managed in Profile.
 * - [AllStudios]: every studio; the accordion lets the member narrow to a
 *   subset of studios/locations ([ScheduleFilters] holds those picks).
 */
enum class ScopeMode { Favorites, AllStudios }

/** One day's cursor-paged session cache. */
data class DayState(
    val sessions: List<ScheduleSessionDto> = emptyList(),
    /** Opaque keyset cursor for the next page; null ⇒ no more pages. */
    val nextCursor: String? = null,
    /** Page 1 has been fetched for the current filter set. */
    val loaded: Boolean = false,
    /** A next-page fetch is in flight (footer loader + loadMore guard). */
    val loadingMore: Boolean = false,
)

sealed interface ScheduleUiState {
    data object Loading : ScheduleUiState
    data class Success(
        /** Today through today + 14 days, oldest first ([ScheduleViewModel.ScheduleTimeZone] dates). */
        val days: List<LocalDate>,
        /** The day whose sessions are on screen — owned by the VM so it
         *  survives navigation and so refetches target the right day. */
        val selectedDate: LocalDate,
        /** Per-day paged session caches. Only days the member has visited
         *  under the current filter set have an entry. */
        val dayStates: Map<LocalDate, DayState>,
        /** True from a filter mutation until its debounced refetch settles —
         *  the screen dims the stale list instead of flashing the loader. */
        val refreshingFilters: Boolean,
        /** Every studio in the window (from the overview's `studios` block,
         *  which ignores studio/location narrowing — never vanishes) with its
         *  selectable locations — the filter accordion's catalog (Custom mode). */
        val filterStudios: List<FilterStudio>,
        /** The studio/location subset selection (meaningful under AllStudios). */
        val filters: ScheduleFilters,
        /** The active studio/location scope (Favorites vs All Studios toggle). */
        val scope: ScopeMode,
        /** The active time-of-day overlay, or null. Renders as a removable chip. */
        val timeFilter: TimeFilter? = null,
        /** Favorites loaded and non-empty — gates the Favorites pill and
         *  suppresses the "choose favorites" nudge banner. */
        val hasFavorites: Boolean,
        /** False when the favorites fetch failed (state unknown) — the nudge
         *  must not show to a member who may already have favorites. */
        val favoritesKnown: Boolean,
        /** The member's favorites as a read-only display list (studios first,
         *  then locations), shown in the filter panel under Favorites mode. */
        val favoriteEntries: List<FavoriteEntry> = emptyList(),
        /** Curated categories present in the window (from the overview's
         *  `categories` block — window-only, never vanishes), the catalog for
         *  the Modalities filter panel. */
        val availableModalities: List<ModalityOption> = emptyList(),
        /** The category slugs currently picked, driving Modalities mode's
         *  list + fetch. */
        val selectedModalitySlugs: Set<String> = emptySet(),
        /** sessionId → live booking status (requested/confirmed/…) for every
         *  upcoming booking the member holds. Lets a row show an "I'm in this
         *  one" status pill. BEST-EFFORT and STALE-TOLERANT: refreshed only on
         *  init and pull-to-refresh, and left empty if the bookings fetch
         *  fails — so a booking made elsewhere (e.g. the class-detail sheet)
         *  surfaces here only after the next refresh, never blocking or
         *  breaking the schedule itself. */
        val bookedSessions: Map<Int, String> = emptyMap(),
    ) : ScheduleUiState
    data class Error(val message: String) : ScheduleUiState
}

/** A studio in the filter accordion: the studio plus its selectable locations. */
data class FilterStudio(
    val slug: String,
    val name: String,
    val primaryColor: String,
    val locations: List<FilterLocation>,
)

/** A selectable location row in the accordion. [label] is Title-Case,
 *  studio-prefix-stripped (see [org.arcana.mobile.ui.studioLocationLabel]). */
data class FilterLocation(val id: Int, val label: String)

/** A favorited studio or location, shown read-only in the schedule filter panel
 *  when Favorites mode is active. Whole-studio favorites read "All locations";
 *  location favorites read the specific location. */
data class FavoriteEntry(val name: String, val detail: String)

/** One selectable modality-filter option (a server category): [slug] is sent
 *  to the API, [label] is shown to the member. */
data class ModalityOption(val slug: String, val label: String)

/**
 * Display-friendly short location label. The backend names locations like
 * "YO BK Williamsburg"; we strip the studio prefix so chips and row metadata
 * can read "WILLIAMSBURG" rather than repeating the brand. Shared string core
 * so the VM (overview-fed chip generation) and the screens (row + detail meta
 * lines) all produce the same label.
 */
internal fun locationShortLabel(studioName: String, locationName: String): String {
    val raw = locationName.removePrefix(studioName).trim()
        .removePrefix("·").trim()
        .removePrefix("-").trim()
    return (raw.ifEmpty { locationName }).uppercase()
}

internal fun LocationBriefDto.shortLabel(): String = locationShortLabel(studio.name, name)

@OptIn(FlowPreview::class)
class ScheduleViewModel(
    private val api: ScheduleApi,
    private val favoritesRepository: FavoritesRepository,
    private val bookingApi: BookingApi,
    private val telemetry: Telemetry = Telemetry.Noop,
) : ViewModel() {

    /** Per-day count of load-more pages fetched, for the `schedule_load_more`
     *  scroll-depth proxy. Reset whenever the day's cache is rebuilt. */
    private val loadMorePageByDay = mutableMapOf<LocalDate, Int>()

    private val _uiState = MutableStateFlow<ScheduleUiState>(ScheduleUiState.Loading)
    val uiState: StateFlow<ScheduleUiState> = _uiState

    /** Drives the pull-to-refresh spinner; true only during a [refresh] fetch. */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    // ── Source-of-truth fields. publish() snapshots them into ONE Success
    //    assignment; refetchForFilters() only mutates them after both network
    //    results are in (atomic apply — a cancelled refetch leaves no
    //    half-applied state).
    private var days: List<LocalDate> = emptyList()
    private var selectedDate: LocalDate = Clock.System.todayIn(ScheduleTimeZone)
    private var dayStates: Map<LocalDate, DayState> = emptyMap()
    private var overviewStudios: List<OverviewStudioDto> = emptyList()
    private var availableModalities: List<ModalityOption> = emptyList()
    private var filters: ScheduleFilters = ScheduleFilters()
    private var selectedModalitySlugs: Set<String> = emptySet()
    private var timeFilter: TimeFilter? = null
    private var refreshingFilters: Boolean = false

    /** The active studio/location scope. Defaults to [ScopeMode.Favorites] at
     *  startup when the member has favorites, else [ScopeMode.AllStudios]. The
     *  Time + Modalities overlays are independent and persist across scope
     *  changes. */
    private var scope: ScopeMode = ScopeMode.AllStudios

    /** sessionId → live booking status for the member's upcoming bookings.
     *  Source-of-truth for the row "already booked" pill; snapshotted into
     *  Success by [publish]. Best-effort — see [refreshBookedSessions]. */
    private var bookedSessions: Map<Int, String> = emptyMap()

    /** Bumped by every settled-filter refetch and pull-to-refresh. In-flight
     *  page fetches snapshot it and drop their result if it moved — a stale
     *  loadMore must never append old-filter rows to a new-filter list. */
    private var fetchGeneration: Int = 0

    /** Days with a page-1 fetch in flight — dedupes rapid re-taps of an
     *  unloaded day chip. */
    private val loadingDays = mutableSetOf<LocalDate>()

    /** Monotonic filter-change counter feeding the debounced refetch
     *  pipeline. A StateFlow (not SharedFlow) so rapid bumps conflate —
     *  debounce only ever needs the latest epoch. */
    private val filterEpoch = MutableStateFlow(0)

    /** The favorites value this VM last acted on — lets the repository
     *  collector below ignore the init-time value and only react to changes
     *  made elsewhere (the favorites manager saving a new set). */
    private var lastAppliedFavorites: FavoritesDto? = null

    init {
        // Debounced filter pipeline. drop(1) skips the StateFlow's replay of
        // the initial epoch — the cold-start fetch below runs immediately
        // rather than waiting out the debounce. collectLatest cancels an
        // in-flight refetch when another settles in behind it; that's safe
        // because refetchForFilters applies its result atomically.
        viewModelScope.launch {
            filterEpoch
                .drop(1)
                .debounce(FILTER_DEBOUNCE_MS)
                .collectLatest { refetchForFilters() }
        }
        // Bookings ride their OWN job, independent of the schedule fetch: a
        // slow or failing /bookings/me call must never block (or break) the
        // list from rendering. When it lands, republish over whatever Success
        // is already on screen so the "already booked" pills appear.
        viewModelScope.launch {
            refreshBookedSessions()
            if (_uiState.value is ScheduleUiState.Success) publish()
        }
        viewModelScope.launch {
            // Favorites first — they decide whether the first fetch is scoped
            // to the member's locations.
            val favorites = favoritesRepository.refresh()
            if (favorites != null && !favorites.isEmpty()) scope = ScopeMode.Favorites
            lastAppliedFavorites = favorites
            refetchForFilters()
            // React to favorites saved/cleared in the manager while this VM is
            // on the back stack. Re-evaluate only when NOT in Custom mode —
            // a member actively building a manual filter must not be disrupted.
            // The collector replays the current value first; `lastAppliedFavorites`
            // makes that replay a no-op.
            favoritesRepository.favorites.collect { favs ->
                if (favs == null) return@collect // logout clear; VM is being torn down
                if (favs == lastAppliedFavorites) return@collect
                lastAppliedFavorites = favs
                // Reflect a favorites change (saved/cleared in Profile) into the
                // scope — but only when the member isn't actively narrowing to a
                // studio subset. Overlays (time/modalities) are always preserved.
                if (scope == ScopeMode.Favorites || filters == ScheduleFilters()) {
                    scope = if (favs.isEmpty()) ScopeMode.AllStudios else ScopeMode.Favorites
                    filters = ScheduleFilters()
                    onFiltersChanged()
                }
            }
        }
    }

    /** Full re-fetch with the shimmer placeholder — error-retry path. */
    fun reload() {
        viewModelScope.launch {
            _uiState.value = ScheduleUiState.Loading
            refetchForFilters()
        }
    }

    /** Pull-to-refresh: re-fetch the overview + the selected day's first page
     *  without flashing the shimmer, keeping the current content visible (and
     *  untouched on a transient failure). Other days' caches are dropped and
     *  the generation bumps, so any in-flight loadMore result is discarded. */
    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                // Keep the "already booked" pills fresh: a booking made on the
                // class-detail sheet (or cancelled elsewhere) shows after a
                // pull-to-refresh. Best-effort — failure leaves the map intact.
                refreshBookedSessions()
                refetchForFilters()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /** Refresh just the "already booked" pills — called when the member lands
     *  back on the Schedule (e.g. popping back from ClassDetail after booking
     *  or cancelling). Best-effort and non-blocking: republishes over the
     *  existing Success so a stale pill clears without flashing the loader, and
     *  a bookings outage leaves the list untouched. */
    fun refreshBookings() {
        viewModelScope.launch {
            refreshBookedSessions()
            if (_uiState.value is ScheduleUiState.Success) publish()
        }
    }

    /** Switch the visible day. The chip rail is already populated from the
     *  overview, so a day tap NEVER refetches the overview — it only pulls
     *  page 1 of that day if it isn't cached for the current filter set. */
    fun selectDay(date: LocalDate, method: String = "chip_tap") {
        if (date != selectedDate) {
            val previous = selectedDate
            val today = Clock.System.todayIn(ScheduleTimeZone)
            telemetry.scheduleDayChanged(
                method = method,
                direction = if (date > previous) "forward" else "backward",
                dayOffsetFromToday = (date.toEpochDays() - today.toEpochDays()).toInt(),
            )
            selectedDate = date
            publish()
        }
        // Falls through on a same-day re-tap too: that's the natural retry
        // gesture after a failed page-1 fetch. ensureSelectedDayLoaded
        // no-ops when the day is already cached or in flight.
        ensureSelectedDayLoaded()
    }

    /** Fetch the next page of the selected day. Guarded: needs a loaded page 1,
     *  a non-null cursor, and no page already in flight — the screen may call
     *  this freely from its scroll trigger. */
    fun loadMore() {
        val date = selectedDate
        val day = dayStates[date] ?: return
        if (!day.loaded || day.nextCursor == null || day.loadingMore) return
        dayStates = dayStates + (date to day.copy(loadingMore = true))
        publish()
        val generation = fetchGeneration
        viewModelScope.launch {
            try {
                val page = api.fetchSessionsPage(
                    date = date,
                    studioSlugs = null,
                    locationIds = effectiveLocationIds(),
                    categorySlugs = effectiveCategorySlugs(),
                    startTimeGte = timeFilter?.startGte,
                    startTimeLte = timeFilter?.startLte,
                    cursor = day.nextCursor,
                )
                // Stale guard: a filter refetch or refresh landed while this
                // page was in flight — its rows belong to a dead filter set.
                if (generation != fetchGeneration) return@launch
                val current = dayStates[date] ?: return@launch
                dayStates = dayStates + (date to current.copy(
                    // distinctBy: the screen keys rows on session id and
                    // LazyColumn CRASHES on duplicate keys — one line of
                    // insurance against a server-side cursor-overlap bug.
                    sessions = (current.sessions + page.toSessions()).distinctBy { it.id },
                    nextCursor = page.nextCursor,
                    loadingMore = false,
                ))
                val pageIndex = (loadMorePageByDay[date] ?: 1) + 1
                loadMorePageByDay[date] = pageIndex
                telemetry.scheduleLoadMore(pageIndex = pageIndex, day = date.toString())
                publish()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logWarning("ScheduleViewModel", e.message ?: "loadMore failed")
                if (generation == fetchGeneration) {
                    dayStates[date]?.let {
                        dayStates = dayStates + (date to it.copy(loadingMore = false))
                        publish()
                    }
                }
            }
        }
    }

    /** Scope toggle → Favorites. Clears the studio subset; KEEPS the time +
     *  modality overlays. No-op without favorites or when already on Favorites. */
    fun useMyFavorites() {
        if (scope == ScopeMode.Favorites) return
        val favorites = favoritesRepository.favorites.value
        if (favorites == null || favorites.isEmpty()) return
        scope = ScopeMode.Favorites
        filters = ScheduleFilters()
        onFiltersChanged()
    }

    /** Scope toggle → All Studios (reset to the whole fleet: clears the studio
     *  subset). KEEPS the time + modality overlays. */
    fun showAllStudios() {
        if (scope == ScopeMode.AllStudios && filters == ScheduleFilters()) return
        scope = ScopeMode.AllStudios
        filters = ScheduleFilters()
        onFiltersChanged()
    }

    /** Toggle a modality category (by slug) in the overlay selection. Does NOT
     *  touch the studio/location scope — modalities AND on top of it. */
    fun toggleModality(slug: String) {
        selectedModalitySlugs = if (slug in selectedModalitySlugs) {
            selectedModalitySlugs - slug
        } else {
            selectedModalitySlugs + slug
        }
        onFiltersChanged()
    }

    /** Remove a modality overlay (chip ×). */
    fun removeModality(slug: String) {
        if (slug !in selectedModalitySlugs) return
        selectedModalitySlugs = selectedModalitySlugs - slug
        onFiltersChanged()
    }

    /** Set the time-of-day overlay (preset or custom range). AND's onto the
     *  scope + modalities. */
    fun setTimeFilter(filter: TimeFilter) {
        if (timeFilter == filter) return
        timeFilter = filter
        onFiltersChanged()
    }

    /** Remove the time overlay (chip ×). */
    fun clearTimeFilter() {
        if (timeFilter == null) return
        timeFilter = null
        onFiltersChanged()
    }

    /** Toggle a whole studio in the All-Studios subset. Selecting it drops its
     *  individual location picks (redundant). Implies All-Studios scope; keeps
     *  the overlays. */
    fun toggleStudioWhole(slug: String) {
        scope = ScopeMode.AllStudios
        val locationIdsForStudio = catalog()[slug].orEmpty().toSet()
        filters = if (slug in filters.studioSlugs) {
            filters.copy(studioSlugs = filters.studioSlugs - slug)
        } else {
            filters.copy(
                studioSlugs = filters.studioSlugs + slug,
                locationIds = filters.locationIds - locationIdsForStudio,
            )
        }
        onFiltersChanged()
    }

    /** Toggle an individual location in the Custom selection.
     *  - With the whole studio selected → narrow: studio off, this location on.
     *  - Selecting the last unselected location PROMOTES to a whole-studio pick. */
    fun toggleLocation(slug: String, id: Int) {
        scope = ScopeMode.AllStudios
        val allLocationIds = catalog()[slug].orEmpty().toSet()
        filters = when {
            slug in filters.studioSlugs -> filters.copy(
                studioSlugs = filters.studioSlugs - slug,
                locationIds = filters.locationIds + id,
            )
            id in filters.locationIds -> filters.copy(locationIds = filters.locationIds - id)
            else -> {
                val withAdded = filters.locationIds + id
                if (allLocationIds.isNotEmpty() && allLocationIds.all { it in withAdded }) {
                    filters.copy(
                        studioSlugs = filters.studioSlugs + slug,
                        locationIds = withAdded - allLocationIds,
                    )
                } else {
                    filters.copy(locationIds = withAdded)
                }
            }
        }
        onFiltersChanged()
    }

    /** slug → its location ids, from the loaded overview catalog. */
    private fun catalog(): Map<String, List<Int>> =
        overviewStudios.associate { studio -> studio.slug to studio.locations.map { it.id } }

    /** Every filter mutation funnels here: the chips (and the dim) update
     *  instantly, while the actual refetch rides the debounced pipeline so
     *  rapid toggling coalesces into one settled overview + page-1 pair. */
    private fun onFiltersChanged() {
        refreshingFilters = true
        telemetry.scheduleFilterChanged(
            mode = when (scope) {
                ScopeMode.Favorites -> "favorites"
                ScopeMode.AllStudios -> "all"
            },
            studioCount = filters.studioSlugs.size,
            locationCount = filters.locationIds.size,
            modalityCount = selectedModalitySlugs.size,
        )
        // Only republish over existing content — on a cold start (or from the
        // Error screen) there is nothing to dim; the pipeline's refetch will
        // establish the state.
        if (_uiState.value is ScheduleUiState.Success) publish()
        filterEpoch.value += 1
    }

    /**
     * The settled refetch: overview + page 1 of the selected day, in parallel,
     * with the current filter scope. On success the WHOLE Success is rebuilt
     * and assigned once — collectLatest may cancel this mid-flight, and a
     * half-applied state must be impossible. All other days' caches drop
     * (they were fetched under the old filters) and the generation bumps so
     * stale in-flight page fetches discard themselves.
     */
    private suspend fun refetchForFilters() {
        val generation = ++fetchGeneration
        try {
            val today = Clock.System.todayIn(ScheduleTimeZone)
            val newDays = (0 until WINDOW_DAYS).map { today.plus(it, DateTimeUnit.DAY) }
            val targetDate = if (selectedDate in newDays) selectedDate else today
            val locationIds = effectiveLocationIds()
            val categorySlugs = effectiveCategorySlugs()
            val timeGte = timeFilter?.startGte
            val timeLte = timeFilter?.startLte
            val (overview, page) = coroutineScope {
                val overviewDeferred = async {
                    api.fetchOverview(
                        from = newDays.first(),
                        to = newDays.last(),
                        studioSlugs = null,
                        locationIds = locationIds,
                        categorySlugs = categorySlugs,
                        startTimeGte = timeGte,
                        startTimeLte = timeLte,
                    )
                }
                val pageDeferred = async {
                    api.fetchSessionsPage(
                        date = targetDate,
                        studioSlugs = null,
                        locationIds = locationIds,
                        categorySlugs = categorySlugs,
                        startTimeGte = timeGte,
                        startTimeLte = timeLte,
                    )
                }
                overviewDeferred.await() to pageDeferred.await()
            }
            // Stale-result guard: refresh() invokes this OUTSIDE the
            // collectLatest pipeline, so a chip tap during a slow
            // pull-to-refresh can run a newer refetch to completion first.
            // If the generation moved while we awaited, these results are
            // stale — drop them rather than overwriting the newer state.
            if (generation != fetchGeneration) return
            // ── Atomic apply: no suspension below this line. ──
            days = newDays
            if (selectedDate !in newDays) selectedDate = today
            overviewStudios = overview.studios
            availableModalities = overview.categories.map { ModalityOption(it.slug, it.name) }
            dayStates = mapOf(
                targetDate to DayState(
                    sessions = page.toSessions(),
                    nextCursor = page.nextCursor,
                    loaded = true,
                ),
            )
            refreshingFilters = false
            publish()
            // If the member switched days while this refetch was in flight,
            // the page we fetched isn't the selected one — pull it now (under
            // the new generation).
            if (dayStates[selectedDate]?.loaded != true) ensureSelectedDayLoaded()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ResponseException) {
            val code = e.response.status.value
            logWarning("ScheduleViewModel", e.message ?: "HTTP $code")
            // Same staleness rule as the success path: a stale refetch's
            // failure must not clear a newer mutation's dim or emit Error
            // over newer state.
            if (generation == fetchGeneration) applyRefetchFailure("server error $code")
        } catch (e: Exception) {
            logWarning("ScheduleViewModel", e.message ?: "Unknown error")
            if (generation == fetchGeneration) applyRefetchFailure("server error")
        }
    }

    /** Cold-start (or error-retry) failure → full-screen Error; failure with
     *  content already on screen → keep the content, just stop the dim. */
    private fun applyRefetchFailure(message: String) {
        if (_uiState.value is ScheduleUiState.Success) {
            refreshingFilters = false
            publish()
        } else {
            _uiState.value = ScheduleUiState.Error(message)
        }
    }

    /** Page-1 fetch for the selected day if it isn't cached (or already in
     *  flight) under the current filter set. */
    private fun ensureSelectedDayLoaded() {
        val date = selectedDate
        if (dayStates[date]?.loaded == true) return
        if (!loadingDays.add(date)) return
        val generation = fetchGeneration
        viewModelScope.launch {
            try {
                val page = api.fetchSessionsPage(
                    date = date,
                    studioSlugs = null,
                    locationIds = effectiveLocationIds(),
                    categorySlugs = effectiveCategorySlugs(),
                    startTimeGte = timeFilter?.startGte,
                    startTimeLte = timeFilter?.startLte,
                )
                // Filters/refresh moved on while this was in flight — the
                // settled refetch owns the day caches now.
                if (generation != fetchGeneration) return@launch
                dayStates = dayStates + (date to DayState(
                    sessions = page.toSessions(),
                    nextCursor = page.nextCursor,
                    loaded = true,
                ))
                publish()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Keep whatever's on screen; the tapped day simply stays in
                // its loading placeholder until a retry path (refresh/filter).
                logWarning("ScheduleViewModel", e.message ?: "day page fetch failed")
            } finally {
                loadingDays.remove(date)
            }
        }
    }

    /** The flat `location_id` list to send. In favorites scope this is the
     *  member's expanded favorite locations; otherwise the manual selection
     *  expanded to locations (whole studios → their catalog location ids).
     *  Null ⇒ send no location filter (show all). `studio_slug` is never sent
     *  (the server ANDs the two params), so a mixed multi-studio set must be
     *  expressed as locations only. */
    private fun effectiveLocationIds(): List<Int>? = when (scope) {
        ScopeMode.Favorites ->
            favoritesRepository.favorites.value
                ?.expandedLocationIds()
                ?.takeIf { it.isNotEmpty() }
        ScopeMode.AllStudios ->
            expandSelectionToLocationIds(filters.studioSlugs, filters.locationIds, catalog())
                .takeIf { it.isNotEmpty() }
    }

    /** The category-slug whitelist to send (repeated `category` param). Applies
     *  as an overlay regardless of scope; null ⇒ no category narrowing. */
    private fun effectiveCategorySlugs(): List<String>? =
        selectedModalitySlugs.takeIf { it.isNotEmpty() }?.toList()

    /** Best-effort fetch of the member's live bookings into [bookedSessions]
     *  (sessionId → status), for the row "already booked" pill. TOLERATES
     *  FAILURE: on any non-cancellation error we log and KEEP the prior map —
     *  a bookings outage must never break or empty the schedule. The schedule
     *  fetch never awaits this (it runs in its own init job + on refresh), so
     *  a slow bookings call can't delay the list from rendering. */
    private suspend fun refreshBookedSessions() {
        try {
            val bookings = bookingApi.myBookings()
            bookedSessions = bookings.upcoming.associate { it.session.id to it.status }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Keep whatever map we had; the pill simply stays as-is until a
            // later refresh succeeds.
            logWarning("ScheduleViewModel", e.message ?: "myBookings fetch failed")
        }
    }

    /** Snapshot the source-of-truth fields into one Success assignment. */
    private fun publish() {
        // The accordion catalog: the overview's `studios` block covers every
        // studio with a location in the window regardless of the active
        // narrowing — studios never vanish as filters change.
        val filterStudios = overviewStudios
            .sortedBy { it.name }
            .map { studio ->
                FilterStudio(
                    slug = studio.slug,
                    name = studio.name,
                    primaryColor = studio.primaryColor,
                    locations = studio.locations
                        .sortedBy { it.name }
                        .map { loc ->
                            FilterLocation(
                                id = loc.id,
                                label = studioLocationLabel(studio.name, loc.name),
                            )
                        },
                )
            }

        val favorites = favoritesRepository.favorites.value
        // Read-only favorites list for Favorites mode: whole-studio favorites
        // (every location) first, then specific location favorites.
        val favoriteEntries = favorites?.let { f ->
            f.studios.sortedBy { it.name }
                .map { FavoriteEntry(name = it.name, detail = "All locations") } +
                f.locations.sortedBy { it.studioName }
                    .map { FavoriteEntry(name = it.studioName, detail = studioLocationLabel(it.studioName, it.name)) }
        } ?: emptyList()
        _uiState.value = ScheduleUiState.Success(
            days = days,
            selectedDate = selectedDate,
            dayStates = dayStates,
            refreshingFilters = refreshingFilters,
            filterStudios = filterStudios,
            filters = filters,
            scope = scope,
            timeFilter = timeFilter,
            hasFavorites = favorites?.isEmpty() == false,
            favoritesKnown = favorites != null,
            favoriteEntries = favoriteEntries,
            availableModalities = availableModalities,
            selectedModalitySlugs = selectedModalitySlugs,
            bookedSessions = bookedSessions,
        )
    }

    /** Fire-once when the favorites list is revealed in the filter panel. */
    fun onFavoritesDropdownShown() {
        val favorites = favoritesRepository.favorites.value ?: return
        telemetry.favoritesDropdownOpened(
            studioCount = favorites.studios.size,
            locationCount = favorites.locations.size,
        )
    }

    /** Member tapped "manage in Profile" from the favorites list. */
    fun onManageFavoritesTapped() = telemetry.favoritesManageTapped()

    companion object {
        /** Matches the server's 15-day max window (today + 14): studios that
         *  open booking "two weeks ahead" release the 14-days-out date, so the
         *  schedule must show it. Server `MAX_RANGE_DAYS = 15` + 16-day sync. */
        const val WINDOW_DAYS: Int = 15

        /** Quiet window after the last chip tap before the settled refetch fires. */
        const val FILTER_DEBOUNCE_MS: Long = 250L

        /** Mirrors `arcana-server/classes/filters.py`'s `DEFAULT_TZ`: the beta
         *  is NYC-only, so the `today` anchor (and the server's day-window
         *  boundaries) is America/New_York regardless of device timezone —
         *  keeps the mobile day rail agreeing with the server's window. */
        val ScheduleTimeZone: TimeZone = TimeZone.of("America/New_York")
    }
}
