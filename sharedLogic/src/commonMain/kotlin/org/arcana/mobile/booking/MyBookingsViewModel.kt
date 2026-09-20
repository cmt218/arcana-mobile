package org.arcana.mobile.booking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.arcana.mobile.analytics.Telemetry
import org.arcana.mobile.data.BookingDto
import org.arcana.mobile.data.ReviewDto
import org.arcana.mobile.networking.BookingApi
import org.arcana.mobile.networking.ErrorType
import org.arcana.mobile.networking.toErrorType
import org.arcana.mobile.networking.transportFailureCode

enum class ReservationSegment(val key: String) { Upcoming("upcoming"), Past("past") }

sealed interface UpcomingUiState {
    data object Loading : UpcomingUiState
    data class Success(val bookings: List<BookingDto>) : UpcomingUiState
    data class Error(val type: ErrorType) : UpcomingUiState
}

sealed interface PastUiState {
    data object Loading : PastUiState
    data class Success(
        val bookings: List<BookingDto>,
        val nextCursor: String?,
        val loadingMore: Boolean = false,
        /** A later page failed; the rows already shown stay. */
        val pageError: ErrorType? = null,
    ) : PastUiState
    data class Error(val type: ErrorType) : PastUiState
}

/** The Reservations screen: Upcoming and Past segments over the scoped
 *  `bookings/me/` reads. The route is still `MyBookings`. */
class MyBookingsViewModel(
    private val api: BookingApi,
    private val telemetry: Telemetry = Telemetry.Noop,
) : ViewModel() {
    private val _segment = MutableStateFlow(ReservationSegment.Upcoming)
    val segment: StateFlow<ReservationSegment> = _segment

    private val _upcoming = MutableStateFlow<UpcomingUiState>(UpcomingUiState.Loading)
    val upcoming: StateFlow<UpcomingUiState> = _upcoming

    private val _past = MutableStateFlow<PastUiState>(PastUiState.Loading)
    val past: StateFlow<PastUiState> = _past

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    /** A refresh failed while content was on screen; the content stays. */
    private val _refreshFailed = MutableStateFlow(false)
    val refreshFailed: StateFlow<Boolean> = _refreshFailed

    /** True while the full-screen error's retry is in flight. */
    private val _retrying = MutableStateFlow(false)
    val retrying: StateFlow<Boolean> = _retrying

    private val _cancelTarget = MutableStateFlow<BookingDto?>(null)
    val cancelTarget: StateFlow<BookingDto?> = _cancelTarget

    private val _cancelState = MutableStateFlow<CancelState>(CancelState.Idle)
    val cancelState: StateFlow<CancelState> = _cancelState

    private var opened = false
    private var pastRequested = false
    private var pastGeneration = 0
    private var pastPageIndex = 0
    private var upcomingJob: Job? = null
    private var pastJob: Job? = null

    fun onOpened(source: String) {
        if (opened) return
        opened = true
        telemetry.reservationsOpened(source)
    }

    /** Cold load of the Upcoming segment. Past loads on its first selection. */
    fun load() {
        _upcoming.value = UpcomingUiState.Loading
        upcomingJob?.cancel()
        upcomingJob = viewModelScope.launch { fetchUpcoming() }
    }

    fun selectSegment(target: ReservationSegment) {
        if (_segment.value == target) return
        _segment.value = target
        _refreshFailed.value = false
        telemetry.reservationsSegmentChanged(target.key)
        if (target == ReservationSegment.Past && !pastRequested) loadPast()
    }

    /** Pull-to-refresh on the visible segment. Content stays put; a failure
     *  raises [refreshFailed] instead of replacing it. */
    fun refresh() {
        _isRefreshing.value = true
        viewModelScope.launch {
            try {
                fetchCurrentSegment()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /** Retry from the full-screen error. Keeps the error on screen while in
     *  flight so the retry control can show progress. */
    fun retry() {
        if (_retrying.value) return
        _retrying.value = true
        viewModelScope.launch {
            try {
                fetchCurrentSegment()
            } finally {
                _retrying.value = false
            }
        }
    }

    fun dismissRefreshFailed() {
        _refreshFailed.value = false
    }

    private suspend fun fetchCurrentSegment() {
        when (_segment.value) {
            ReservationSegment.Upcoming -> fetchUpcoming()
            ReservationSegment.Past -> fetchPastFirstPage()
        }
    }

    private suspend fun fetchUpcoming() {
        try {
            val data = api.myUpcoming()
            _upcoming.value = UpcomingUiState.Success(data.upcoming)
            _refreshFailed.value = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (_upcoming.value is UpcomingUiState.Success) _refreshFailed.value = true
            else _upcoming.value = UpcomingUiState.Error(e.toErrorType())
        }
    }

    private fun loadPast() {
        pastRequested = true
        _past.value = PastUiState.Loading
        pastJob?.cancel()
        pastJob = viewModelScope.launch { fetchPastFirstPage() }
    }

    /** Replaces the past list with page one. Bumps the generation so an
     *  in-flight load-more from the previous list is discarded. */
    private suspend fun fetchPastFirstPage() {
        pastRequested = true
        val generation = ++pastGeneration
        try {
            val page = api.myPast(cursor = null)
            if (generation != pastGeneration) return
            pastPageIndex = 0
            _past.value = PastUiState.Success(page.past.distinctBy { it.id }, page.nextCursor)
            _refreshFailed.value = false
            telemetry.reservationsPageLoaded(pageIndex = 0, count = page.past.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (generation != pastGeneration) return
            if (_past.value is PastUiState.Success) _refreshFailed.value = true
            else _past.value = PastUiState.Error(e.toErrorType())
        }
    }

    /** Next past page. No-op when there is none, one is in flight, or the
     *  last one failed (see [retryLoadMore]). */
    fun loadMore() {
        val current = _past.value as? PastUiState.Success ?: return
        val cursor = current.nextCursor ?: return
        if (current.loadingMore || current.pageError != null) return
        _past.value = current.copy(loadingMore = true)
        val generation = pastGeneration
        viewModelScope.launch {
            try {
                val page = api.myPast(cursor = cursor)
                if (generation != pastGeneration) return@launch
                val latest = _past.value as? PastUiState.Success ?: return@launch
                pastPageIndex += 1
                _past.value = latest.copy(
                    bookings = (latest.bookings + page.past).distinctBy { it.id },
                    nextCursor = page.nextCursor,
                    loadingMore = false,
                )
                telemetry.reservationsPageLoaded(pageIndex = pastPageIndex, count = page.past.size)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation != pastGeneration) return@launch
                (_past.value as? PastUiState.Success)?.let {
                    _past.value = it.copy(loadingMore = false, pageError = e.toErrorType())
                }
            }
        }
    }

    fun retryLoadMore() {
        (_past.value as? PastUiState.Success)?.let { _past.value = it.copy(pageError = null) }
        loadMore()
    }

    /** Rows whose review card is open: step one is saved, the member may still
     *  be answering the rest, so the row keeps its card until it is closed. */
    private val _openReviews = MutableStateFlow<Set<Int>>(emptySet())
    val openReviews: StateFlow<Set<Int>> = _openReviews

    /** Step one landed on [booking]'s row. Every sibling of the same combination
     *  flips to reviewed at once; the server's page confirms on the next read. */
    fun reviewStarted(booking: BookingDto, review: ReviewDto) {
        _openReviews.value = _openReviews.value + booking.id
        val current = _past.value as? PastUiState.Success ?: return
        _past.value = current.copy(
            bookings = current.bookings.map { row ->
                when {
                    row.id == booking.id -> row
                    row.id == review.bookingId -> row.copy(reviewState = "reviewed_here")
                    row.sameCombinationAs(booking) && row.canReview -> row.copy(reviewState = "reviewed_elsewhere")
                    else -> row
                }
            },
        )
    }

    /** The member closed the card: the row now just says it was reviewed. */
    fun reviewFinished(bookingId: Int) {
        _openReviews.value = _openReviews.value - bookingId
        val current = _past.value as? PastUiState.Success ?: return
        _past.value = current.copy(
            bookings = current.bookings.map { row ->
                if (row.id == bookingId && row.canReview) row.copy(reviewState = "reviewed_here") else row
            },
        )
    }

    fun openCancel(booking: BookingDto) {
        _cancelTarget.value = booking
        _cancelState.value = CancelState.Idle
        telemetry.bookingCancelStarted(
            bookingId = booking.id,
            sessionId = booking.session.id,
            willForfeitCredit = booking.cancelPolicy.willForfeitCredit,
        )
    }

    fun dismissCancel() {
        _cancelTarget.value = null
        _cancelState.value = CancelState.Idle
    }

    fun confirmCancel() {
        val booking = _cancelTarget.value ?: return
        if (_cancelState.value is CancelState.Submitting) return
        _cancelState.value = CancelState.Submitting
        viewModelScope.launch {
            try {
                val resp = api.cancelBooking(booking.id)
                telemetry.bookingCancelled(
                    bookingId = booking.id,
                    creditRefunded = resp.creditRefunded,
                    lateCancel = resp.lateCancel,
                    studioId = null,
                    locationId = booking.session.locationId,
                )
                _cancelTarget.value = null
                _cancelState.value = CancelState.Idle
                // Drop the row now; the refetch confirms the server's view.
                (_upcoming.value as? UpcomingUiState.Success)?.let { s ->
                    _upcoming.value = s.copy(bookings = s.bookings.filterNot { it.id == booking.id })
                }
                fetchUpcoming()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val code = e.transportFailureCode()
                telemetry.bookingCancelFailed(booking.id, code)
                telemetry.recordError(e, mapOf("op" to "cancelBooking", "booking_id" to booking.id))
                _cancelState.value = CancelState.Failed(code)
            }
        }
    }
}

/** The review combination as the row can see it: brand, class type, instructor. */
internal fun BookingDto.sameCombinationAs(other: BookingDto): Boolean =
    session.brandSlug == other.session.brandSlug &&
        session.classTypeKey == other.session.classTypeKey &&
        session.instructorProfileId == other.session.instructorProfileId
