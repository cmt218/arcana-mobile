package org.arcana.mobile.booking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.arcana.mobile.analytics.Telemetry
import org.arcana.mobile.data.BookingDto
import org.arcana.mobile.data.SpotDto
import org.arcana.mobile.data.coveredMonthsPhrase
import org.arcana.mobile.data.periodForClass
import org.arcana.mobile.networking.BookingApi
import org.arcana.mobile.networking.BookingError
import org.arcana.mobile.networking.MembershipApi

sealed interface BookingSubmit {
    data object Idle : BookingSubmit
    data object Submitting : BookingSubmit
    data class Booked(val bookingId: Int) : BookingSubmit
    data class Failed(val code: String) : BookingSubmit
}

sealed interface CancelState {
    data object Idle : CancelState
    data object Submitting : CancelState
    data class Failed(val code: String) : CancelState
}

/** Studio/location context for booking-funnel analytics, grouped into one value
 *  so the Koin factory stays within its 5-parameter destructuring limit. */
data class BookingStudioContext(
    val studioId: Int = 0,
    val studioName: String = "",
    val locationId: Int = 0,
    val locationName: String = "",
)

class BookingViewModel(
    private val sessionId: Int,
    private val spotsAvailable: Int,
    private val requiresSpot: Boolean,
    private val bookingApi: BookingApi,
    private val membershipApi: MembershipApi,
    // ISO-8601 start of THIS class — used to pick the wallet that will pay for it
    // (current vs the next-month beta wallet). Empty in unit tests → current wallet.
    private val sessionStartIso: String = "",
    private val telemetry: Telemetry = Telemetry.Noop,
    // Studio/location context for booking-funnel events (so bookings break down
    // by studio + location). Default keeps unit-test construction lightweight.
    private val studioContext: BookingStudioContext = BookingStudioContext(),
) : ViewModel() {

    private fun studioIdOrNull() = studioContext.studioId.takeIf { it != 0 }
    private fun locationIdOrNull() = studioContext.locationId.takeIf { it != 0 }

    private val _ctaState = MutableStateFlow(BookCta.NotBookable)
    val ctaState: StateFlow<BookCta> = _ctaState

    private val _creditsRemaining = MutableStateFlow<Int?>(null)
    val creditsRemaining: StateFlow<Int?> = _creditsRemaining

    // The month(s) this member's wallet(s) cover, e.g. "July" / "July and August".
    // Drives the concierge popup when they try to book outside their window.
    private val _coveredMonths = MutableStateFlow<String?>(null)
    val coveredMonths: StateFlow<String?> = _coveredMonths

    private val _selectedSpot = MutableStateFlow<SpotDto?>(null)
    val selectedSpot: StateFlow<SpotDto?> = _selectedSpot

    // "Have you been to this studio before?" — asked once per (member, studio)
    // at booking time. `shouldAsk` comes from the class-detail payload; the
    // answer (null until tapped) gates confirm when the prompt is shown.
    private val _shouldAskStudioVisit = MutableStateFlow(false)
    val shouldAskStudioVisit: StateFlow<Boolean> = _shouldAskStudioVisit

    private val _visitedBefore = MutableStateFlow<Boolean?>(null)
    val visitedBefore: StateFlow<Boolean?> = _visitedBefore

    private val _sheetOpen = MutableStateFlow(false)
    val sheetOpen: StateFlow<Boolean> = _sheetOpen

    private val _submitState = MutableStateFlow<BookingSubmit>(BookingSubmit.Idle)
    val submitState: StateFlow<BookingSubmit> = _submitState

    // The member's existing (live) booking for THIS session, if any — carries
    // the ops-driven status ("requested"/"confirmed") plus the id + cancel
    // policy needed to cancel. null = not booked. Lets the CTA both reflect the
    // real status and open the cancel flow.
    private val _existingBooking = MutableStateFlow<BookingDto?>(null)
    val existingBooking: StateFlow<BookingDto?> = _existingBooking

    // Gates the CTA's initial "loading" spinner — false until load() resolves
    // /me + /bookings so the CTA never flashes "NOT AVAILABLE" pre-fetch.
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded

    private val _cancelState = MutableStateFlow<CancelState>(CancelState.Idle)
    val cancelState: StateFlow<CancelState> = _cancelState

    private val _cancelSheetOpen = MutableStateFlow(false)
    val cancelSheetOpen: StateFlow<Boolean> = _cancelSheetOpen

    fun load() {
        viewModelScope.launch {
            val me = runCatching { membershipApi.membershipMe() }.getOrNull()
            val existing = runCatching { bookingApi.myBookings() }
                .getOrNull()?.upcoming?.firstOrNull { it.session.id == sessionId }
            _existingBooking.value = existing
            // Show the balance of the wallet that will actually pay for THIS
            // class (current vs the next-month wallet), not just the current one.
            val walletForClass = me?.periodForClass(sessionStartIso)
            _creditsRemaining.value = walletForClass?.creditsRemaining
            _coveredMonths.value = me?.coveredMonthsPhrase()
            _ctaState.value = bookCtaState(spotsAvailable, walletForClass, alreadyBooked = existing != null)
            _loaded.value = true
        }
    }

    /** Set from the class-detail payload (`should_ask_studio_visit`). */
    fun setShouldAskStudioVisit(shouldAsk: Boolean) {
        _shouldAskStudioVisit.value = shouldAsk
    }

    fun answerStudioVisit(visited: Boolean) {
        _visitedBefore.value = visited
        telemetry.studioVisitAnswered(sessionId, studioIdOrNull(), studioContext.studioName, visited)
    }

    fun openSheet() {
        if (_ctaState.value == BookCta.Bookable) {
            _sheetOpen.value = true
            telemetry.bookingSheetOpened(sessionId, studioIdOrNull(), locationIdOrNull(), requiresSpot)
            if (_shouldAskStudioVisit.value) {
                telemetry.studioVisitPromptShown(sessionId, studioIdOrNull(), studioContext.studioName)
            }
        }
    }
    fun dismissSheet() {
        // Fire abandonment only when the member backs out of an OPEN sheet
        // without a completed booking (success closes the sheet directly).
        if (_sheetOpen.value && _submitState.value !is BookingSubmit.Booked) {
            telemetry.bookingSheetAbandoned(
                sessionId = sessionId,
                reachedSpotSelection = requiresSpot,
                hadSelectedSpot = _selectedSpot.value != null,
            )
        }
        _sheetOpen.value = false
        // Clear a failed attempt so reopening the sheet starts clean.
        if (_submitState.value is BookingSubmit.Failed) _submitState.value = BookingSubmit.Idle
    }
    fun selectSpot(spot: SpotDto) {
        _selectedSpot.value = spot
        telemetry.spotSelected(sessionId, spot.id, spot.label)
    }

    // Confirm is gated on BOTH a picked spot (when required) AND the studio-visit
    // answer (when the prompt is shown), so we always capture the answer first.
    val canConfirm: Boolean get() =
        (!requiresSpot || _selectedSpot.value != null) &&
        (!_shouldAskStudioVisit.value || _visitedBefore.value != null)

    fun confirmBooking() {
        if (!canConfirm) return
        if (_submitState.value is BookingSubmit.Submitting) return
        _submitState.value = BookingSubmit.Submitting
        val hasSpot = _selectedSpot.value != null
        telemetry.bookingSubmitted(sessionId, hasSpot)
        viewModelScope.launch {
            try {
                // visitedBefore is null when the prompt wasn't shown — the server
                // treats null as a no-op, so old/non-prompted bookings are unaffected.
                val b = bookingApi.createBooking(sessionId, _selectedSpot.value?.id, _visitedBefore.value)
                _submitState.value = BookingSubmit.Booked(b.id)
                _existingBooking.value = b
                _ctaState.value = BookCta.AlreadyBooked
                _sheetOpen.value = false
                telemetry.bookingSucceeded(
                    bookingId = b.id,
                    status = b.status,
                    sessionId = sessionId,
                    studioId = studioIdOrNull(),
                    locationId = locationIdOrNull(),
                    hasSpot = hasSpot,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: BookingError) {
                telemetry.bookingFailed(e.code, sessionId)
                _submitState.value = BookingSubmit.Failed(e.code)
            } catch (e: Exception) {
                telemetry.bookingFailed("booking_failed", sessionId)
                telemetry.recordError(e, mapOf("op" to "createBooking", "session_id" to sessionId))
                _submitState.value = BookingSubmit.Failed("booking_failed")
            }
        }
    }

    fun openCancelSheet() {
        val booking = _existingBooking.value ?: return
        _cancelSheetOpen.value = true
        telemetry.bookingCancelStarted(
            bookingId = booking.id,
            sessionId = sessionId,
            willForfeitCredit = booking.cancelPolicy.willForfeitCredit,
        )
    }

    fun dismissCancelSheet() {
        _cancelSheetOpen.value = false
        _cancelState.value = CancelState.Idle
    }

    fun confirmCancel() {
        val booking = _existingBooking.value ?: return
        if (_cancelState.value is CancelState.Submitting) return
        _cancelState.value = CancelState.Submitting
        viewModelScope.launch {
            try {
                val resp = bookingApi.cancelBooking(booking.id)
                telemetry.bookingCancelled(
                    bookingId = booking.id,
                    creditRefunded = resp.creditRefunded,
                    lateCancel = resp.lateCancel,
                    studioId = studioIdOrNull(),
                    locationId = locationIdOrNull(),
                )
                _cancelSheetOpen.value = false
                _cancelState.value = CancelState.Idle
                _existingBooking.value = null
                _submitState.value = BookingSubmit.Idle
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                telemetry.bookingCancelFailed(booking.id)
                telemetry.recordError(e, mapOf("op" to "cancelBooking", "booking_id" to booking.id))
                _cancelState.value = CancelState.Failed("cancel_failed")
            }
        }
    }
}
