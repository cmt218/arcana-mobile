package org.arcana.mobile.booking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
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

    fun load() {
        viewModelScope.launch {
            val me = runCatching { membershipApi.membershipMe() }.getOrNull()
            val alreadyBooked = runCatching { bookingApi.myBookings() }
                .getOrNull()?.upcoming?.any { it.session.id == sessionId } ?: false
            _creditsRemaining.value = me?.currentPeriod?.creditsRemaining
            _ctaState.value = bookCtaState(spotsAvailable, me?.currentPeriod, alreadyBooked)
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
                _ctaState.value = BookCta.AlreadyBooked
                _sheetOpen.value = false
            } catch (e: BookingError) {
                _submitState.value = BookingSubmit.Failed(e.code)
            } catch (e: Exception) {
                _submitState.value = BookingSubmit.Failed("booking_failed")
            }
        }
    }
}
