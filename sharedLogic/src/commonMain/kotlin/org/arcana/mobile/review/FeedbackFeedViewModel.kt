package org.arcana.mobile.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.arcana.mobile.analytics.Telemetry
import org.arcana.mobile.data.FeedbackScopeDto
import org.arcana.mobile.data.ReviewDto
import org.arcana.mobile.networking.ErrorType
import org.arcana.mobile.networking.ReviewApi
import org.arcana.mobile.networking.toErrorType

sealed interface FeedbackFeedUiState {
    data object Loading : FeedbackFeedUiState
    data class Success(
        val scope: FeedbackScopeDto,
        val items: List<ReviewDto>,
        val nextCursor: String?,
        val loadingMore: Boolean = false,
        /** A later page failed; the items already shown stay. */
        val pageError: ErrorType? = null,
    ) : FeedbackFeedUiState
    data class Error(val type: ErrorType) : FeedbackFeedUiState
}

/** The member feedback feed for one scope, keyset paged newest first. */
class FeedbackFeedViewModel(
    val scopeType: String,
    val scopeValue: String,
    private val source: String,
    private val api: ReviewApi,
    private val telemetry: Telemetry = Telemetry.Noop,
) : ViewModel() {
    private val _uiState = MutableStateFlow<FeedbackFeedUiState>(FeedbackFeedUiState.Loading)
    val uiState: StateFlow<FeedbackFeedUiState> = _uiState

    private val _retrying = MutableStateFlow(false)
    val retrying: StateFlow<Boolean> = _retrying

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _refreshFailed = MutableStateFlow(false)
    val refreshFailed: StateFlow<Boolean> = _refreshFailed

    private var generation = 0
    private var opened = false

    init {
        viewModelScope.launch { fetchFirstPage() }
    }

    fun onOpened() {
        if (opened) return
        opened = true
        telemetry.feedbackFeedOpened(scopeType, source)
    }

    fun onItemTapped() = telemetry.feedbackItemTapped(scopeType, "studio_page")

    fun retry() {
        if (_retrying.value) return
        _retrying.value = true
        viewModelScope.launch {
            try {
                fetchFirstPage()
            } finally {
                _retrying.value = false
            }
        }
    }

    fun refresh() {
        _isRefreshing.value = true
        viewModelScope.launch {
            try {
                fetchFirstPage()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun dismissRefreshFailed() {
        _refreshFailed.value = false
    }

    private suspend fun fetchFirstPage() {
        val myGeneration = ++generation
        try {
            val page = api.fetchFeedback(scopeType, scopeValue, cursor = null)
            if (myGeneration != generation) return
            _uiState.value = FeedbackFeedUiState.Success(page.scope, page.items.distinctBy { it.id }, page.nextCursor)
            _refreshFailed.value = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (myGeneration != generation) return
            if (_uiState.value is FeedbackFeedUiState.Success) _refreshFailed.value = true
            else _uiState.value = FeedbackFeedUiState.Error(e.toErrorType())
        }
    }

    /** Next page. No-op with no cursor, a page in flight, or a failed page
     *  awaiting [retryLoadMore]. */
    fun loadMore() {
        val current = _uiState.value as? FeedbackFeedUiState.Success ?: return
        val cursor = current.nextCursor ?: return
        if (current.loadingMore || current.pageError != null) return
        _uiState.value = current.copy(loadingMore = true)
        val myGeneration = generation
        viewModelScope.launch {
            try {
                val page = api.fetchFeedback(scopeType, scopeValue, cursor = cursor)
                if (myGeneration != generation) return@launch
                val latest = _uiState.value as? FeedbackFeedUiState.Success ?: return@launch
                _uiState.value = latest.copy(
                    items = (latest.items + page.items).distinctBy { it.id },
                    nextCursor = page.nextCursor,
                    loadingMore = false,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (myGeneration != generation) return@launch
                (_uiState.value as? FeedbackFeedUiState.Success)?.let {
                    _uiState.value = it.copy(loadingMore = false, pageError = e.toErrorType())
                }
            }
        }
    }

    fun retryLoadMore() {
        (_uiState.value as? FeedbackFeedUiState.Success)?.let { _uiState.value = it.copy(pageError = null) }
        loadMore()
    }
}
