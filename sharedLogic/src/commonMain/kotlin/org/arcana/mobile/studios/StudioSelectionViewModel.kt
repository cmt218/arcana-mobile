package org.arcana.mobile.studios

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.arcana.mobile.analytics.Telemetry
import org.arcana.mobile.data.FavoritesDto
import org.arcana.mobile.data.StudioDto
import org.arcana.mobile.schedule.ScheduleViewModel
import org.arcana.mobile.networking.ScheduleApi
import org.arcana.mobile.data.OverviewBrandDto
import kotlinx.datetime.todayIn
import kotlinx.datetime.plus
import kotlinx.datetime.DateTimeUnit
import kotlin.time.Clock
import org.arcana.mobile.favorites.FavoritesRepository
import org.arcana.mobile.logWarning
import org.arcana.mobile.networking.ErrorType
import org.arcana.mobile.networking.FavoritesApi
import org.arcana.mobile.networking.toErrorType

sealed interface StudioSelectionUiState {
    data object Loading : StudioSelectionUiState
    data class Ready(
        val studios: List<StudioDto>,
        val selectedStudioSlugs: Set<String>,
        val selectedLocationIds: Set<Int>,
        val expandedStudioSlugs: Set<String>,
        /** Slugs in [studios] that are brands (saved as `brand_slugs`); the rest
         *  are site rows the overview could not place, saved as `studio_slugs`. */
        val brandSlugs: Set<String> = emptySet(),
        val saving: Boolean = false,
        val saved: Boolean = false,
        val error: String? = null,
    ) : StudioSelectionUiState
    data class Error(val type: ErrorType) : StudioSelectionUiState
}

class StudioSelectionViewModel(
    private val favoritesApi: FavoritesApi,
    private val repository: FavoritesRepository,
    private val telemetry: Telemetry = Telemetry.Noop,
    /** Supplies the overview's brands block, which maps site rows onto brands.
     *  Null (tests) lists rows as they come. */
    private val scheduleApi: ScheduleApi? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow<StudioSelectionUiState>(StudioSelectionUiState.Loading)
    val uiState: StateFlow<StudioSelectionUiState> = _uiState

    /** True while [retry] is in flight; the retry control carries the progress. */
    private val _retrying = MutableStateFlow(false)
    val retrying: StateFlow<Boolean> = _retrying

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        try {
            val rows = favoritesApi.fetchStudios()
            val favorites = repository.refresh()
            val grouped = groupRowsByBrand(rows, fetchBrands())
            val (whole, picks) = initialSelection(favorites, grouped)
            _uiState.value = StudioSelectionUiState.Ready(
                studios = grouped.studios,
                selectedStudioSlugs = whole,
                selectedLocationIds = picks,
                expandedStudioSlugs = emptySet(),
                brandSlugs = grouped.brandSlugs,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logWarning("StudioSelectionViewModel", e.message ?: "load failed")
            _uiState.value = StudioSelectionUiState.Error(e.toErrorType())
        }
    }

    /** The overview's brands for the schedule window; a failure just means no
     *  grouping this time, never a failed screen. */
    private suspend fun fetchBrands(): List<OverviewBrandDto> {
        val api = scheduleApi ?: return emptyList()
        return try {
            val today = Clock.System.todayIn(ScheduleViewModel.ScheduleTimeZone)
            api.fetchOverview(from = today, to = today.plus(ScheduleViewModel.WINDOW_DAYS - 1, DateTimeUnit.DAY)).brands
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logWarning("StudioSelectionViewModel", "brands unavailable: ${e.message}")
            emptyList()
        }
    }

    private class Grouped(val studios: List<StudioDto>, val brandSlugs: Set<String>)

    /** One card per brand: its rows' locations under the brand's name. Rows the
     *  overview does not place (no sessions in the window) stay their own card. */
    private fun groupRowsByBrand(rows: List<StudioDto>, brands: List<OverviewBrandDto>): Grouped {
        if (brands.isEmpty()) return Grouped(rows, emptySet())
        val brandByRowId = brands.flatMap { b -> b.locations.map { it.studioId to b } }.toMap()
        val out = LinkedHashMap<String, StudioDto>()
        val brandSlugs = mutableSetOf<String>()
        for (row in rows) {
            val brand = brandByRowId[row.id]
            if (brand == null) {
                out[row.slug] = row
                continue
            }
            brandSlugs += brand.slug
            val existing = out[brand.slug]
            out[brand.slug] = StudioDto(
                id = brand.id,
                slug = brand.slug,
                name = brand.name,
                logoUrl = brand.logoUrl.ifBlank { existing?.logoUrl ?: row.logoUrl },
                heroImageUrl = existing?.heroImageUrl ?: row.heroImageUrl,
                primaryColor = brand.primaryColor.ifBlank { existing?.primaryColor ?: row.primaryColor },
                locations = (existing?.locations.orEmpty() + row.locations).distinctBy { it.id },
            )
        }
        // Sort past a leading bracket or symbol so "[solidcore]" files under S.
        return Grouped(out.values.sortedBy { it.name.trimStart { c -> !c.isLetterOrDigit() }.lowercase() }, brandSlugs)
    }

    /** Whole-card slugs and location picks from the saved favorites. A brand
     *  favorite covering every location of its card is a whole card; a partial
     *  one becomes location picks. */
    private fun initialSelection(favorites: FavoritesDto?, grouped: Grouped): Pair<Set<String>, Set<Int>> {
        if (favorites == null) return emptySet<String>() to emptySet()
        val cards = grouped.studios.associateBy { it.slug }
        val whole = mutableSetOf<String>()
        val picks = favorites.locations.map { it.id }.toMutableSet()
        if (favorites.brands.isNotEmpty()) {
            for (brand in favorites.brands) {
                val card = cards[brand.slug] ?: continue
                val all = card.locations.map { it.id }
                if (all.isNotEmpty() && all.all { it in brand.locationIds }) whole += brand.slug
                else picks += brand.locationIds.filter { it in all }
            }
            // Rows the overview could not place keep their row-grain favorite.
            favorites.studios.forEach { if (it.slug in cards && it.slug !in grouped.brandSlugs) whole += it.slug }
        } else {
            favorites.studios.forEach { if (it.slug in cards) whole += it.slug }
        }
        return whole to picks
    }

    fun retry() {
        // Claimed synchronously so taps arriving before the coroutine starts
        // don't each queue their own load.
        if (_retrying.value) return
        _retrying.value = true
        viewModelScope.launch {
            try {
                load()
            } finally {
                _retrying.value = false
            }
        }
    }

    private inline fun update(block: (StudioSelectionUiState.Ready) -> StudioSelectionUiState.Ready) {
        val current = _uiState.value as? StudioSelectionUiState.Ready ?: return
        _uiState.value = block(current)
    }

    /** Whole-Studio toggle. Selecting a Studio implies every location, so
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

    /** Individual-location toggle.
     *
     *  - Tapping a location while the whole Studio is selected narrows:
     *    Studio off, just this location on.
     *  - Tapping the last unselected location PROMOTES to a whole-Studio
     *    selection — "all locations" is visually and semantically identical to
     *    favoriting the Studio (and should pick up future locations too), so
     *    we store it that way rather than as N individual picks. This is what
     *    gives a single-location Studio the full treatment from one tap. */
    fun toggleLocation(studioSlug: String, locationId: Int) = update { s ->
        val studio = s.studios.firstOrNull { it.slug == studioSlug } ?: return@update s
        if (studioSlug in s.selectedStudioSlugs) {
            s.copy(
                selectedStudioSlugs = s.selectedStudioSlugs - studioSlug,
                selectedLocationIds = s.selectedLocationIds + locationId,
            )
        } else if (locationId in s.selectedLocationIds) {
            s.copy(selectedLocationIds = s.selectedLocationIds - locationId)
        } else {
            val allLocationIds = studio.locations.map { it.id }.toSet()
            val withAdded = s.selectedLocationIds + locationId
            if (allLocationIds.isNotEmpty() && allLocationIds.all { it in withAdded }) {
                // Completed the set → promote to whole-Studio, dropping the
                // now-redundant explicit picks for this studio.
                s.copy(
                    selectedStudioSlugs = s.selectedStudioSlugs + studioSlug,
                    selectedLocationIds = withAdded - allLocationIds,
                )
            } else {
                s.copy(selectedLocationIds = withAdded)
            }
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
        // Snapshot the previously-saved favorites BEFORE the PUT so we can diff
        // the delta (which studios/locations were added vs removed).
        val previous = repository.favorites.value
        viewModelScope.launch {
            // Completion paths go through update {} (not a copy of the
            // tap-time snapshot) so toggles made during a slow PUT survive.
            update { it.copy(saving = true, error = null) }
            try {
                repository.save(
                    studioSlugs = (current.selectedStudioSlugs - current.brandSlugs).toList(),
                    locationIds = current.selectedLocationIds.toList(),
                    brandSlugs = (current.selectedStudioSlugs intersect current.brandSlugs).toList(),
                )
                emitFavoriteDeltas(
                    previous = previous,
                    newStudioSlugs = current.selectedStudioSlugs,
                    newLocationIds = current.selectedLocationIds,
                    studios = current.studios,
                    brandSlugs = current.brandSlugs,
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

    /** Emit per-studio / per-location favorite_added / favorite_removed events
     *  for the delta vs the previously-saved set, plus a favorites_saved summary
     *  and an updated favorite-studios person profile — so usage can be broken
     *  down by studio and location. */
    private fun emitFavoriteDeltas(
        previous: FavoritesDto?,
        newStudioSlugs: Set<String>,
        newLocationIds: Set<Int>,
        studios: List<StudioDto>,
        brandSlugs: Set<String> = emptySet(),
    ) {
        val bySlug = studios.associateBy { it.slug }
        // location id → (its studio, location name)
        val locationOwner = studios.flatMap { studio ->
            studio.locations.map { loc -> loc.id to (studio to loc.name) }
        }.toMap()

        // Only slugs that are cards on this screen count; a brand favorite also
        // lists its site rows, which are not cards here.
        val oldStudioSlugs = (previous?.studios?.map { it.slug }.orEmpty() + previous?.brands?.map { it.slug }.orEmpty())
            .filter { it in bySlug }.toSet()
        val oldLocationIds = previous?.locations?.map { it.id }?.toSet() ?: emptySet()
        fun kind(slug: String) = if (slug in brandSlugs) "brand" else "studio"

        (newStudioSlugs - oldStudioSlugs).forEach { slug ->
            val s = bySlug[slug]
            telemetry.favoriteAdded(kind(slug), s?.id, slug, s?.name)
        }
        (oldStudioSlugs - newStudioSlugs).forEach { slug ->
            val s = bySlug[slug]
            telemetry.favoriteRemoved(kind(slug), s?.id, slug, s?.name ?: previous?.studios?.firstOrNull { it.slug == slug }?.name)
        }
        (newLocationIds - oldLocationIds).forEach { id ->
            val owner = locationOwner[id]
            telemetry.favoriteAdded("location", owner?.first?.id, owner?.first?.slug, owner?.first?.name, id, owner?.second)
        }
        (oldLocationIds - newLocationIds).forEach { id ->
            val owner = locationOwner[id]
            telemetry.favoriteRemoved("location", owner?.first?.id, owner?.first?.slug, owner?.first?.name, id, owner?.second)
        }

        telemetry.favoritesSaved(
            studioCount = (newStudioSlugs - brandSlugs).size,
            locationCount = newLocationIds.size,
            studioSlugs = (newStudioSlugs - brandSlugs).toList(),
            locationIds = newLocationIds.toList(),
            brandSlugs = (newStudioSlugs intersect brandSlugs).toList(),
        )
        telemetry.setFavoriteProfile(
            favoriteStudioCount = newStudioSlugs.size,
            favoriteStudios = newStudioSlugs.map { bySlug[it]?.name ?: it },
        )
    }
}
