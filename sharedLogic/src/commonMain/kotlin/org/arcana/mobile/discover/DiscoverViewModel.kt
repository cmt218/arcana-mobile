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
    ) : DiscoverUiState
    data class Error(val type: ErrorType) : DiscoverUiState
}

class DiscoverViewModel(
    private val api: DiscoverApi,
    private val telemetry: Telemetry = Telemetry.Noop,
) : ViewModel() {
    private val _uiState = MutableStateFlow<DiscoverUiState>(DiscoverUiState.Loading)
    val uiState: StateFlow<DiscoverUiState> = _uiState

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _refreshFailed = MutableStateFlow(false)
    val refreshFailed: StateFlow<Boolean> = _refreshFailed

    private val _retrying = MutableStateFlow(false)
    val retrying: StateFlow<Boolean> = _retrying

    private var categories: List<DiscoverCategoryDto> = emptyList()
    private var neighborhoods: List<String> = emptyList()
    private var selectedCategories: Set<String> = emptySet()
    private var selectedNeighborhoods: Set<String> = emptySet()
    private var studios: List<DiscoverStudioDto> = emptyList()
    private var catalogLoaded = false
    private var opened = false
    private var generation = 0
    private val filterEpoch = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            filterEpoch.drop(1).debounce(FILTER_DEBOUNCE_MS).collectLatest { fetch(keepContent = true) }
        }
        viewModelScope.launch { fetch(keepContent = false) }
    }

    fun onOpened() {
        if (opened) return
        opened = true
        telemetry.discoverOpened()
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
            if (!catalogLoaded || (selectedCategories.isEmpty() && selectedNeighborhoods.isEmpty())) {
                categories = directory.studios.flatMap { it.categories }.distinctBy { it.slug }.sortedBy { it.name }
                neighborhoods = directory.studios.flatMap { it.neighborhoods }.distinct().sorted()
                catalogLoaded = true
            }
            _refreshFailed.value = false
            publish(refreshing = false)
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
        )
    }

    private companion object {
        const val FILTER_DEBOUNCE_MS = 250L
    }
}
