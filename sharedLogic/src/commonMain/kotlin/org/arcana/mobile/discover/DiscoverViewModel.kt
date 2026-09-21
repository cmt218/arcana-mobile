@file:OptIn(kotlinx.coroutines.FlowPreview::class)

package org.arcana.mobile.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.arcana.mobile.analytics.Telemetry
import org.arcana.mobile.data.DiscoverCategoryDto
import org.arcana.mobile.data.DiscoverStudioDto
import org.arcana.mobile.networking.DiscoverApi
import org.arcana.mobile.networking.ErrorType
import org.arcana.mobile.networking.toErrorType

sealed interface DiscoverUiState {
    data object Loading : DiscoverUiState
    data class Success(
        val studios: List<DiscoverStudioDto>,
        /** Filter catalogs, taken from the unfiltered directory so a narrowing
         *  never hides the other options. */
        val categories: List<DiscoverCategoryDto>,
        val neighborhoods: List<String>,
        val selectedCategories: Set<String>,
        val selectedNeighborhoods: Set<String>,
        /** A filter change is being applied; the stale list dims. */
        val refreshingFilters: Boolean = false,
        /** What the map draws for [studios] under the current filters. */
        val pins: List<DiscoverPin> = emptyList(),
        /** Bumped whenever a filter changes the pins, so the map can reframe them. */
        val pinsEpoch: Int = 0,
        /** Bumped when the map should go to the selected pin ("show on the map"
         *  from a class page), wherever the member had left it. */
        val focusEpoch: Int = 0,
    ) : DiscoverUiState
    data class Error(val type: ErrorType) : DiscoverUiState
}

class DiscoverViewModel(
    private val api: DiscoverApi,
    private val telemetry: Telemetry = Telemetry.Noop,
    private val mapRequests: DiscoverMapRequests = DiscoverMapRequests(),
) : ViewModel() {
    private val _uiState = MutableStateFlow<DiscoverUiState>(DiscoverUiState.Loading)
    val uiState: StateFlow<DiscoverUiState> = _uiState

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _refreshFailed = MutableStateFlow(false)
    val refreshFailed: StateFlow<Boolean> = _refreshFailed

    private val _retrying = MutableStateFlow(false)
    val retrying: StateFlow<Boolean> = _retrying

    private val _mode = MutableStateFlow(DiscoverMode.Studios)
    val mode: StateFlow<DiscoverMode> = _mode

    private val _selectedPinId = MutableStateFlow<Int?>(null)
    val selectedPinId: StateFlow<Int?> = _selectedPinId

    val mapCamera = MapCameraMemory()

    private var categories: List<DiscoverCategoryDto> = emptyList()
    private var neighborhoods: List<String> = emptyList()
    private var selectedCategories: Set<String> = emptySet()
    private var selectedNeighborhoods: Set<String> = emptySet()
    private var studios: List<DiscoverStudioDto> = emptyList()
    private var pins: List<DiscoverPin> = emptyList()
    private var pinsEpoch = 0
    private var focusEpoch = 0
    private var pendingFocus: Int? = null
    private var catalogLoaded = false
    private var opened = false
    private var generation = 0
    private val filterEpoch = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            filterEpoch.drop(1).debounce(FILTER_DEBOUNCE_MS).collectLatest { fetch(keepContent = true) }
        }
        viewModelScope.launch { fetch(keepContent = false) }
        viewModelScope.launch {
            mapRequests.pending.collect { locationId ->
                if (locationId != null) {
                    mapRequests.consume()
                    focusLocation(locationId)
                }
            }
        }
    }

    fun onOpened() {
        if (opened) return
        opened = true
        telemetry.discoverOpened()
    }

    fun setMode(mode: DiscoverMode) {
        if (_mode.value == mode) return
        _mode.value = mode
        _selectedPinId.value = null
        telemetry.discoverModeChanged(mode.key)
    }

    fun selectPin(locationId: Int) {
        val pin = pins.firstOrNull { it.locationId == locationId } ?: return
        if (_selectedPinId.value == locationId) return
        _selectedPinId.value = locationId
        telemetry.discoverMapPinTapped(pin.brandSlug, pin.locationId)
    }

    fun clearPin() {
        _selectedPinId.value = null
    }

    /** "Show on the map": the Map lens, on that location's pin. The member asked
     *  for this place, so filters that could hide it are dropped first. */
    private fun focusLocation(locationId: Int) {
        if (_mode.value != DiscoverMode.Map) {
            _mode.value = DiscoverMode.Map
            telemetry.discoverModeChanged(DiscoverMode.Map.key)
        }
        pendingFocus = locationId
        if (selectedCategories.isNotEmpty() || selectedNeighborhoods.isNotEmpty()) clearFilters() else resolveFocus()
    }

    /** Runs once the unfiltered pins are in hand. A location with no pin leaves
     *  the member on the map where they were. */
    private fun resolveFocus() {
        val locationId = pendingFocus ?: return
        if (!catalogLoaded || selectedCategories.isNotEmpty() || selectedNeighborhoods.isNotEmpty()) return
        pendingFocus = null
        val pin = pins.firstOrNull { it.locationId == locationId } ?: return
        _selectedPinId.value = locationId
        // A map not built yet opens here; one already alive is moved by the epoch.
        framePins(listOf(pin))?.let { frame ->
            mapCamera.latitude = frame.latitude
            mapCamera.longitude = frame.longitude
            mapCamera.latitudeDelta = frame.latitudeDelta
            mapCamera.longitudeDelta = frame.longitudeDelta
            mapCamera.zoom = DiscoverMapDefaults.FOCUS_ZOOM
        }
        focusEpoch += 1
        if (_uiState.value is DiscoverUiState.Success) publish(refreshing = false)
    }

    fun toggleCategory(slug: String) {
        selectedCategories = if (slug in selectedCategories) selectedCategories - slug else selectedCategories + slug
        onFiltersChanged()
    }

    fun toggleNeighborhood(name: String) {
        selectedNeighborhoods =
            if (name in selectedNeighborhoods) selectedNeighborhoods - name else selectedNeighborhoods + name
        onFiltersChanged()
    }

    fun clearFilters() {
        if (selectedCategories.isEmpty() && selectedNeighborhoods.isEmpty()) return
        selectedCategories = emptySet()
        selectedNeighborhoods = emptySet()
        onFiltersChanged()
    }

    fun refresh() {
        _isRefreshing.value = true
        viewModelScope.launch {
            try {
                fetch(keepContent = true)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun retry() {
        if (_retrying.value) return
        _retrying.value = true
        viewModelScope.launch {
            try {
                fetch(keepContent = false)
            } finally {
                _retrying.value = false
            }
        }
    }

    fun dismissRefreshFailed() {
        _refreshFailed.value = false
    }

    private fun onFiltersChanged() {
        telemetry.discoverFilterChanged(selectedCategories.size, selectedNeighborhoods.size)
        (_uiState.value as? DiscoverUiState.Success)?.let { publish(refreshing = true) }
        filterEpoch.value += 1
    }

    /** [keepContent]: a failure leaves the current list and raises the
     *  snackbar flag instead of replacing it with the error screen. */
    private suspend fun fetch(keepContent: Boolean) {
        val myGeneration = ++generation
        try {
            val directory = api.fetchDirectory(selectedCategories, selectedNeighborhoods)
            if (myGeneration != generation) return
            studios = directory.studios
            val next = discoverPins(studios, selectedNeighborhoods)
            if (next.map { it.locationId } != pins.map { it.locationId }) {
                // The first load opens on the default frame; later changes reframe.
                if (catalogLoaded) pinsEpoch += 1
                if (next.none { it.locationId == _selectedPinId.value }) _selectedPinId.value = null
            }
            pins = next
            if (!catalogLoaded || (selectedCategories.isEmpty() && selectedNeighborhoods.isEmpty())) {
                categories = directory.studios.flatMap { it.categories }.distinctBy { it.slug }.sortedBy { it.name }
                neighborhoods = directory.studios.flatMap { it.neighborhoods }.distinct().sorted()
                catalogLoaded = true
            }
            _refreshFailed.value = false
            publish(refreshing = false)
            resolveFocus()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (myGeneration != generation) return
            if (keepContent && _uiState.value is DiscoverUiState.Success) {
                _refreshFailed.value = true
                publish(refreshing = false)
            } else {
                _uiState.value = DiscoverUiState.Error(e.toErrorType())
            }
        }
    }

    private fun publish(refreshing: Boolean) {
        _uiState.value = DiscoverUiState.Success(
            studios = studios,
            categories = categories,
            neighborhoods = neighborhoods,
            selectedCategories = selectedCategories,
            selectedNeighborhoods = selectedNeighborhoods,
            refreshingFilters = refreshing,
            pins = pins,
            pinsEpoch = pinsEpoch,
            focusEpoch = focusEpoch,
        )
    }

    private companion object {
        const val FILTER_DEBOUNCE_MS = 250L
    }
}
