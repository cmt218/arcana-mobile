@file:OptIn(FlowPreview::class)

package org.arcana.mobile.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import org.arcana.mobile.analytics.Telemetry
import org.arcana.mobile.data.ScheduleSessionDto
import org.arcana.mobile.networking.ErrorType
import org.arcana.mobile.networking.SearchApi
import org.arcana.mobile.networking.toErrorType
import org.arcana.mobile.schedule.ScheduleViewModel
import org.arcana.mobile.schedule.toSessions

/** A tappable narrowing target surfaced by search: chips above the results.
 *  Studio scopes a whole brand row; Location scopes ONE of its sites — the
 *  chip a neighborhood match produces, so "flatiron" narrows to SLT Flatiron
 *  rather than every SLT. */
sealed interface SearchScope {
    val label: String

    data class Studio(val slug: String, override val label: String) : SearchScope

    data class Location(val id: Int, override val label: String) : SearchScope

    data class Instructor(val name: String) : SearchScope {
        override val label: String get() = name
    }
}

/** At most one active scope per TYPE — chips combine across types (a studio
 *  AND an instructor), while tapping a second studio replaces the first. */
data class ActiveScopes(
    val studio: SearchScope.Studio? = null,
    val location: SearchScope.Location? = null,
    val instructor: SearchScope.Instructor? = null,
) {
    val all: List<SearchScope> get() = listOfNotNull(studio, location, instructor)
    val isEmpty: Boolean get() = studio == null && location == null && instructor == null

    fun with(scope: SearchScope): ActiveScopes = when (scope) {
        is SearchScope.Studio -> copy(studio = scope)
        is SearchScope.Location -> copy(location = scope)
        is SearchScope.Instructor -> copy(instructor = scope)
    }

    fun without(scope: SearchScope): ActiveScopes = when (scope) {
        is SearchScope.Studio -> copy(studio = null)
        is SearchScope.Location -> copy(location = null)
        is SearchScope.Instructor -> copy(instructor = null)
    }
}

sealed interface SearchUiState {
    /** Nothing searched yet (or the query dropped under 2 chars). */
    data class Idle(val recents: List<String>) : SearchUiState

    /** First fetch for a query in flight with nothing to show yet. */
    data object Searching : SearchUiState

    /** [sessions] is chronological across the full window; an empty list with
     *  non-empty [chips] renders the no-classes copy under the chips. */
    data class Results(
        val chips: List<SearchScope>,
        val sessions: List<ScheduleSessionDto>,
        val nextCursor: String?,
        val activeScopes: List<SearchScope>,
        val refreshing: Boolean = false,
        val loadingMore: Boolean = false,
    ) : SearchUiState

    data class NoResults(val query: String, val activeScopes: List<SearchScope>) : SearchUiState

    data class Error(val type: ErrorType) : SearchUiState
}

class SearchViewModel(
    private val api: SearchApi,
    private val recentSearches: RecentSearches,
    private val telemetry: Telemetry = Telemetry.Noop,
) : ViewModel() {

    private val _query = MutableStateFlow("")

    /** The input field's text — deliberately separate from [uiState] so an
     *  error or refetch never wipes what the member typed. */
    val query: StateFlow<String> = _query

    private val _scopes = MutableStateFlow(ActiveScopes())

    private val _uiState =
        MutableStateFlow<SearchUiState>(SearchUiState.Idle(recentSearches.all()))
    val uiState: StateFlow<SearchUiState> = _uiState

    /** Bumped by every debounced execution; loadMore captures it at fetch and
     *  re-checks at apply so a page for an older query/scope is discarded
     *  (same idiom as ScheduleViewModel's generation counter). */
    private var searchEpoch = 0

    init {
        // One VM per screen entry (navigation-scoped), so init IS "opened".
        telemetry.searchOpened()
        // Debounced pipeline. drop(1) skips the StateFlows' initial replay —
        // the screen starts Idle without waiting out a debounce. collectLatest
        // cancels the in-flight execution when a newer (query, scope) settles
        // behind it, so a stale response can never publish.
        viewModelScope.launch {
            combine(_query, _scopes) { query, scopes -> query.trim() to scopes }
                .drop(1)
                .debounce(SEARCH_DEBOUNCE_MS)
                .collectLatest { (query, scopes) -> execute(query, scopes) }
        }
    }

    fun onQueryChanged(value: String) {
        _query.value = value
    }

    fun onScope(scope: SearchScope) {
        telemetry.searchScoped(
            when (scope) {
                is SearchScope.Studio -> "studio"
                is SearchScope.Location -> "location"
                is SearchScope.Instructor -> "instructor"
            },
        )
        _scopes.value = _scopes.value.with(scope)
    }

    fun onRemoveScope(scope: SearchScope) {
        _scopes.value = _scopes.value.without(scope)
    }

    fun onRecentTapped(recent: String) {
        _query.value = recent
    }

    fun onClearRecents() {
        recentSearches.clear()
        if (_uiState.value is SearchUiState.Idle) {
            _uiState.value = SearchUiState.Idle(emptyList())
        }
    }

    fun onResultTapped(position: Int) {
        telemetry.searchResultTapped(position, scoped = !_scopes.value.isEmpty)
    }

    /** Error-state retry: StateFlow dedups identical values, so re-setting the
     *  query can't re-trigger the pipeline — run the execution directly. */
    fun retry() {
        viewModelScope.launch { execute(_query.value.trim(), _scopes.value) }
    }

    fun loadMore() {
        val current = _uiState.value
        if (current !is SearchUiState.Results) return
        val cursor = current.nextCursor ?: return
        if (current.loadingMore) return

        val epoch = searchEpoch
        _uiState.value = current.copy(loadingMore = true)
        viewModelScope.launch {
            try {
                val page = api.searchSessions(
                    from = today(),
                    to = windowEnd(),
                    query = effectiveQuery(),
                    instructor = _scopes.value.instructor?.name,
                    studioSlug = _scopes.value.studio?.slug,
                    locationId = _scopes.value.location?.id,
                    cursor = cursor,
                )
                val latest = _uiState.value
                if (epoch != searchEpoch || latest !is SearchUiState.Results) return@launch
                _uiState.value = latest.copy(
                    // The list is keyed on session id and LazyColumn crashes on a
                    // duplicate, so a cursor-overlapping page must not reach it.
                    sessions = (latest.sessions + page.toSessions()).distinctBy { it.id },
                    nextCursor = page.nextCursor,
                    loadingMore = false,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // A failed page append is silent: the member still has every
                // row loaded so far, and scrolling again retries.
                val latest = _uiState.value
                if (epoch == searchEpoch && latest is SearchUiState.Results) {
                    _uiState.value = latest.copy(loadingMore = false)
                }
            }
        }
    }

    private suspend fun execute(query: String, scopes: ActiveScopes) {
        searchEpoch += 1
        val effectiveQuery = query.takeIf { it.length >= MIN_QUERY_LENGTH }
        if (effectiveQuery == null && scopes.isEmpty) {
            _uiState.value = SearchUiState.Idle(recentSearches.all())
            return
        }

        val previous = _uiState.value
        _uiState.value = when (previous) {
            is SearchUiState.Results -> previous.copy(refreshing = true)
            else -> SearchUiState.Searching
        }
        try {
            coroutineScope {
                // Entities always ride along with a query — typing "evan"
                // while a studio chip is active should still suggest the
                // instructor, so chips can combine across types.
                val entities = if (effectiveQuery != null) {
                    async { api.searchEntities(effectiveQuery) }
                } else {
                    null
                }
                val page = async {
                    api.searchSessions(
                        from = today(),
                        to = windowEnd(),
                        query = effectiveQuery,
                        instructor = scopes.instructor?.name,
                        studioSlug = scopes.studio?.slug,
                        locationId = scopes.location?.id,
                    )
                }.await()

                val sessions = page.toSessions()
                val active = scopes.all
                val chips = entities?.await()?.let { dto ->
                    dto.studios.map { SearchScope.Studio(slug = it.slug, label = it.name) } +
                        dto.locations.map {
                            SearchScope.Location(id = it.id, label = "${it.studioName} ${it.name}")
                        } +
                        dto.instructors.map { SearchScope.Instructor(it.name) }
                }.orEmpty().filterNot { it in active }

                telemetry.searchPerformed(query, sessions.size, scoped = !scopes.isEmpty)
                effectiveQuery?.let(recentSearches::record)

                _uiState.value = if (sessions.isEmpty() && chips.isEmpty()) {
                    SearchUiState.NoResults(query, active)
                } else {
                    SearchUiState.Results(
                        chips = chips,
                        sessions = sessions,
                        nextCursor = page.nextCursor,
                        activeScopes = active,
                    )
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            _uiState.value = SearchUiState.Error(error.toErrorType())
        }
    }

    private fun effectiveQuery(): String? =
        _query.value.trim().takeIf { it.length >= MIN_QUERY_LENGTH }

    private fun today(): LocalDate = Clock.System.todayIn(ScheduleViewModel.ScheduleTimeZone)

    private fun windowEnd(): LocalDate =
        today().plus(ScheduleViewModel.WINDOW_DAYS - 1, DateTimeUnit.DAY)

    companion object {
        const val SEARCH_DEBOUNCE_MS: Long = 250L
        const val MIN_QUERY_LENGTH: Int = 2
    }
}
