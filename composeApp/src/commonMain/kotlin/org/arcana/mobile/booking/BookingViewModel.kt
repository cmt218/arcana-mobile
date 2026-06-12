package org.arcana.mobile.booking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.arcana.mobile.data.BookingDto
import org.arcana.mobile.data.SpotDto
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

class BookingViewModel(
    private val sessionId: Int,
    private val spotsAvailable: Int,
    private val requiresSpot: Boolean,
    private val bookingApi: BookingApi,
    private val membershipApi: MembershipApi,
) : ViewModel() {

    private val _ctaState = MutableStateFlow(BookCta.NotBookable)
    val ctaState: StateFlow<BookCta> = _ctaState

    private val _creditsRemaining = MutableStateFlow<Int?>(null)
    val creditsRemaining: StateFlow<Int?> = _creditsRemaining

    private val _selectedSpot = MutableStateFlow<SpotDto?>(null)
    val selectedSpot: StateFlow<SpotDto?> = _selectedSpot

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
            _creditsRemaining.value = me?.currentPeriod?.creditsRemaining
            _ctaState.value = bookCtaState(spotsAvailable, me?.currentPeriod, alreadyBooked = existing != null)
            _loaded.value = true
        }
    }

    fun openSheet() { if (_ctaState.value == BookCta.Bookable) _sheetOpen.value = true }
    fun dismissSheet() { _sheetOpen.value = false }
    fun selectSpot(spot: SpotDto) { _selectedSpot.value = spot }

    val canConfirm: Boolean get() = !requiresSpot || _selectedSpot.value != null

    fun confirmBooking() {
        if (!canConfirm) return
        if (_submitState.value is BookingSubmit.Submitting) return
        _submitState.value = BookingSubmit.Submitting
        viewModelScope.launch {
            try {
                val b = bookingApi.createBooking(sessionId, _selectedSpot.value?.id)
                _submitState.value = BookingSubmit.Booked(b.id)
                _existingBooking.value = b
                _ctaState.value = BookCta.AlreadyBooked
                _sheetOpen.value = false
            } catch (e: BookingError) {
                _submitState.value = BookingSubmit.Failed(e.code)
            } catch (e: Exception) {
                _submitState.value = BookingSubmit.Failed("booking_failed")
            }
        }
    }

    fun openCancelSheet() { if (_existingBooking.value != null) _cancelSheetOpen.value = true }

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
                bookingApi.cancelBooking(booking.id)
                _cancelSheetOpen.value = false
                _cancelState.value = CancelState.Idle
                _existingBooking.value = null
                _submitState.value = BookingSubmit.Idle
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _cancelState.value = CancelState.Failed("cancel_failed")
            }
        }
    }
}
