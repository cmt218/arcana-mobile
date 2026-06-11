package org.arcana.mobile.studios

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.arcana.mobile.data.StudioDto
import org.arcana.mobile.favorites.FavoritesRepository
import org.arcana.mobile.logWarning
import org.arcana.mobile.networking.FavoritesApi

sealed interface StudioSelectionUiState {
    data object Loading : StudioSelectionUiState
    data class Ready(
        val studios: List<StudioDto>,
        val selectedStudioSlugs: Set<String>,
        val selectedLocationIds: Set<Int>,
        val expandedStudioSlugs: Set<String>,
        val saving: Boolean = false,
        val saved: Boolean = false,
        val error: String? = null,
    ) : StudioSelectionUiState
    data class Error(val message: String) : StudioSelectionUiState
}

class StudioSelectionViewModel(
    private val favoritesApi: FavoritesApi,
    private val repository: FavoritesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<StudioSelectionUiState>(StudioSelectionUiState.Loading)
    val uiState: StateFlow<StudioSelectionUiState> = _uiState

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        _uiState.value = StudioSelectionUiState.Loading
        try {
            val studios = favoritesApi.fetchStudios()
            val favorites = repository.refresh()
            _uiState.value = StudioSelectionUiState.Ready(
                studios = studios,
                selectedStudioSlugs = favorites?.studios?.map { it.slug }?.toSet() ?: emptySet(),
                selectedLocationIds = favorites?.locations?.map { it.id }?.toSet() ?: emptySet(),
                expandedStudioSlugs = emptySet(),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logWarning("StudioSelectionViewModel", e.message ?: "load failed")
            _uiState.value = StudioSelectionUiState.Error("Couldn't load Partners.")
        }
    }

    fun retry() {
        viewModelScope.launch { load() }
    }

    private inline fun update(block: (StudioSelectionUiState.Ready) -> StudioSelectionUiState.Ready) {
        val current = _uiState.value as? StudioSelectionUiState.Ready ?: return
        _uiState.value = block(current)
    }

    /** Whole-Partner toggle. Selecting a Partner implies every location, so
     *  any individual location picks for it are dropped as redundant. */
    fun toggleStudio(slug: String) = update { s ->
        val studio = s.studios.firstOrNull { it.slug == slug } ?: return@update s
        if (slug in s.selectedStudioSlugs) {
            s.copy(selectedStudioSlugs = s.selectedStudioSlugs - slug)
        } else {
            val locationIdsForStudio = studio.locations.map { it.id }.toSet()
            s.copy(
                selectedStudioSlugs = s.selectedStudioSlugs + slug,
                selectedLocationIds = s.selectedLocationIds - locationIdsForStudio,
            )
        }
    }

    /** Individual-location toggle. Tapping a location while the whole Partner
     *  is selected narrows: Partner off, just this location on. */
    fun toggleLocation(studioSlug: String, locationId: Int) = update { s ->
        if (studioSlug in s.selectedStudioSlugs) {
            s.copy(
                selectedStudioSlugs = s.selectedStudioSlugs - studioSlug,
                selectedLocationIds = s.selectedLocationIds + locationId,
            )
        } else if (locationId in s.selectedLocationIds) {
            s.copy(selectedLocationIds = s.selectedLocationIds - locationId)
        } else {
            s.copy(selectedLocationIds = s.selectedLocationIds + locationId)
        }
    }

    fun toggleExpanded(slug: String) = update { s ->
        s.copy(
            expandedStudioSlugs = if (slug in s.expandedStudioSlugs) {
                s.expandedStudioSlugs - slug
            } else {
                s.expandedStudioSlugs + slug
            }
        )
    }

    fun save() {
        val current = _uiState.value as? StudioSelectionUiState.Ready ?: return
        if (current.saving) return
        viewModelScope.launch {
            // Completion paths go through update {} (not a copy of the
            // tap-time snapshot) so toggles made during a slow PUT survive.
            update { it.copy(saving = true, error = null) }
            try {
                repository.save(
                    studioSlugs = current.selectedStudioSlugs.toList(),
                    locationIds = current.selectedLocationIds.toList(),
                )
                update { it.copy(saving = false, saved = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logWarning("StudioSelectionViewModel", e.message ?: "save failed")
                update { it.copy(saving = false, error = "Couldn't save. Try again.") }
            }
        }
    }
}
