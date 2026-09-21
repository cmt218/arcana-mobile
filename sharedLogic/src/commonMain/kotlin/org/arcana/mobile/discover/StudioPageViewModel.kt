package org.arcana.mobile.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.arcana.mobile.analytics.Telemetry
import org.arcana.mobile.data.StudioPageDto
import org.arcana.mobile.data.StudioPageLocationDto
import org.arcana.mobile.favorites.FavoritesRepository
import org.arcana.mobile.networking.DiscoverApi
import org.arcana.mobile.networking.ErrorType
import org.arcana.mobile.networking.toErrorType
import org.arcana.mobile.schedule.ScheduleScopeRequest
import org.arcana.mobile.schedule.ScheduleScopeRequests
import org.arcana.mobile.ui.studioLocationLabel

sealed interface StudioPageUiState {
    data object Loading : StudioPageUiState
    data class Success(
        val page: StudioPageDto,
        /** Location ids of this brand the member has favorited (whole-studio
         *  favorites expanded). */
        val favoriteLocationIds: Set<Int>,
        val savingFavorites: Boolean = false,
        val favoritesError: ErrorType? = null,
        /** The location the member came from (a map pin), when the brand has
         *  others too: the page leads with it and the Book button scopes to it. */
        val fromLocation: StudioPageLocationDto? = null,
    ) : StudioPageUiState {
        val isFavorite: Boolean get() = favoriteLocationIds.isNotEmpty()
    }
    data class Error(val type: ErrorType) : StudioPageUiState
}

/** What the "Add to favorites" tap did: added the only location, or needs
 *  the location picker sheet. */
enum class FavoriteTapResult { Added, Sheet, NoOp }

class StudioPageViewModel(
    val brandSlug: String,
    private val source: String,
    private val fromLocationId: Int?,
    private val api: DiscoverApi,
    private val favoritesRepository: FavoritesRepository,
    private val scopeRequests: ScheduleScopeRequests,
    private val telemetry: Telemetry = Telemetry.Noop,
) : ViewModel() {
    private val _uiState = MutableStateFlow<StudioPageUiState>(StudioPageUiState.Loading)
    val uiState: StateFlow<StudioPageUiState> = _uiState

    private val _retrying = MutableStateFlow(false)
    val retrying: StateFlow<Boolean> = _retrying

    private var viewed = false

    init {
        viewModelScope.launch { fetch() }
    }

    fun retry() {
        if (_retrying.value) return
        _retrying.value = true
        viewModelScope.launch {
            try {
                fetch()
            } finally {
                _retrying.value = false
            }
        }
    }

    private suspend fun fetch() {
        try {
            val page = api.fetchStudioPage(brandSlug)
            // A one-location brand has nothing to narrow: its page is that location's.
            val from = page.locations.takeIf { it.size > 1 }?.firstOrNull { it.id == fromLocationId }
            _uiState.value = StudioPageUiState.Success(page, page.favoriteLocationIds.toSet(), fromLocation = from)
            if (!viewed) {
                viewed = true
                telemetry.studioPageViewed(brandSlug, source)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiState.value = StudioPageUiState.Error(e.toErrorType())
        }
    }

    /** Posts the Book tab's scope: the location the member came from, or every
     *  location of the brand. The screen then switches tabs. */
    fun requestSchedule() {
        val state = _uiState.value as? StudioPageUiState.Success ?: return
        val page = state.page
        val from = state.fromLocation
        telemetry.studioScheduleTapped(brandSlug, locationId = from?.id)
        scopeRequests.request(
            if (from == null) ScheduleScopeRequest(page.slug, page.name, page.locations.map { it.id })
            else ScheduleScopeRequest(
                page.slug, page.name, listOf(from.id),
                label = "${page.name} · ${studioLocationLabel(page.name, from.name)}",
            ),
        )
    }

    /** One location: favorite it now. Several: the caller opens the picker. */
    fun onFavoriteTapped(): FavoriteTapResult {
        val state = _uiState.value as? StudioPageUiState.Success ?: return FavoriteTapResult.NoOp
        if (state.isFavorite || state.savingFavorites) return FavoriteTapResult.NoOp
        val locations = state.page.locations
        return when (locations.size) {
            0 -> FavoriteTapResult.NoOp
            1 -> {
                telemetry.studioFavoriteTapped(brandSlug, 1, "added")
                saveFavorites(setOf(locations.single().id))
                FavoriteTapResult.Added
            }
            else -> {
                telemetry.studioFavoriteTapped(brandSlug, locations.size, "sheet")
                FavoriteTapResult.Sheet
            }
        }
    }

    /** Merges [locationIds] into the member's current favorites and saves the
     *  replace-set. A failure keeps the previous set and reports inline. */
    fun saveFavorites(locationIds: Set<Int>) {
        val state = _uiState.value as? StudioPageUiState.Success ?: return
        if (locationIds.isEmpty() || state.savingFavorites) return
        _uiState.value = state.copy(savingFavorites = true, favoritesError = null)
        viewModelScope.launch {
            try {
                val current = favoritesRepository.favorites.value ?: favoritesRepository.refresh()
                val studioSlugs = current?.studios?.map { it.slug }.orEmpty()
                val existing = current?.locations?.map { it.id }.orEmpty()
                val saved = favoritesRepository.save(studioSlugs, (existing + locationIds).distinct())
                val mine = state.page.locations.map { it.id }.toSet()
                val favored = saved.expandedLocationIds().filter { it in mine }.toSet()
                (_uiState.value as? StudioPageUiState.Success)?.let {
                    _uiState.value = it.copy(favoriteLocationIds = favored, savingFavorites = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                (_uiState.value as? StudioPageUiState.Success)?.let {
                    _uiState.value = it.copy(savingFavorites = false, favoritesError = e.toErrorType())
                }
            }
        }
    }

    fun onInstructorTapped(profileId: Int) = telemetry.instructorTapped(profileId, "studio_page")
}
