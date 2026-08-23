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
import org.arcana.mobile.data.classCohortMonthName
import org.arcana.mobile.data.coveredMonthsPhrase
import org.arcana.mobile.data.coveringPeriodForClass
import org.arcana.mobile.data.periodForClass
import org.arcana.mobile.networking.BookingApi
import org.arcana.mobile.networking.BookingError
import org.arcana.mobile.networking.ErrorType
import org.arcana.mobile.networking.MembershipApi
import org.arcana.mobile.networking.toErrorType
import org.arcana.mobile.networking.transportFailureCode

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

/** Set when the member holds a wallet but none of their wallets cover THIS
 *  class's month (e.g. a July-only member viewing an August class). Carries the
 *  month strings the CTA copy needs. Distinct from "no active membership at all". */
data class OutsideWindowInfo(val heldMonths: String, val classMonth: String)

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

    // Starts Unknown, not NotBookable: until /me answers we have no basis to
    // say anything about the member's account. A failed cold load leaves this
    // as-is; a failed refresh keeps whatever was last established.
    private val _ctaState = MutableStateFlow(BookCta.Unknown)
    val ctaState: StateFlow<BookCta> = _ctaState

    private val _creditsRemaining = MutableStateFlow<Int?>(null)
    val creditsRemaining: StateFlow<Int?> = _creditsRemaining

    // Non-null when the member holds a wallet but none covers this class's month.
    // Drives the CTA's "OUTSIDE YOUR MEMBERSHIP" state; null in every other case
    // (covered, no membership, rolling subscriber).
    private val _outsideWindow = MutableStateFlow<OutsideWindowInfo?>(null)
    val outsideWindow: StateFlow<OutsideWindowInfo?> = _outsideWindow

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

    // Static, per-class-type spot *preference* options (e.g. ["Bag","Bench"]) and
    // their label, pushed in from the class-detail template the same way
    // `setShouldAskStudioVisit` pushes the visit flag. DISTINCT from — and
    // mutually exclusive with — real spot selection: when `requiresSpot` is true
    // the preference is suppressed entirely (see [spotPreferenceActive]). Always
    // best-effort (never required): the chosen value (null until tapped) just
    // rides along on the booking as free text when present.
    private var _spotPreferenceOptionsRaw: List<String> = emptyList()
    private var _spotPreferenceLabel: String? = null

    private val _selectedSpotPreference = MutableStateFlow<String?>(null)
    val selectedSpotPreference: StateFlow<String?> = _selectedSpotPreference

    /** True when the preference dropdown is actually in play: the template has
     *  options AND this isn't a real-spot class (real spots win). */
    private val spotPreferenceActive: Boolean
        get() = !requiresSpot && _spotPreferenceOptionsRaw.isNotEmpty()

    /** Template options to render — empty (dropdown hidden) when not active. */
    val spotPreferenceOptions: List<String>
        get() = if (spotPreferenceActive) _spotPreferenceOptionsRaw else emptyList()

    /** Dropdown label/prompt; null when not active OR blank, so the caller's
     *  default ("Spot preference") actually triggers. The server stores an unset
     *  label as "" (not null), so normalize blank → null here. */
    val spotPreferenceLabel: String?
        get() = if (spotPreferenceActive) _spotPreferenceLabel?.takeIf { it.isNotBlank() } else null

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

    /** True when the membership fetch failed, so the CTA is stale rather than
     *  authoritative. The screen shows the refresh snackbar instead of letting
     *  the button claim the member has no membership. */
    private val _membershipLoadFailed = MutableStateFlow(false)
    val membershipLoadFailed: StateFlow<Boolean> = _membershipLoadFailed

    fun dismissMembershipLoadFailed() {
        _membershipLoadFailed.value = false
    }

    fun load() {
        viewModelScope.launch {
            val meResult = runCatching { membershipApi.membershipMe() }
            // A failed fetch is not the same fact as "no membership", and
            // `getOrNull()` collapses the two. Keep the last known CTA rather
            // than asserting something false about the member's account.
            if (meResult.isFailure) {
                _membershipLoadFailed.value = true
                // Still mark loaded: the CTA reads `!loaded` as "fetching" and
                // would otherwise spin forever when the FIRST fetch fails.
                _loaded.value = true
                return@launch
            }
            _membershipLoadFailed.value = false
            val me = meResult.getOrNull()
            val existing = runCatching { bookingApi.myBookings() }
                .getOrNull()?.upcoming?.firstOrNull { it.session.id == sessionId }
            _existingBooking.value = existing
            _coveredMonths.value = me?.coveredMonthsPhrase()
            // The wallet whose window actually covers THIS class, if any. Null
            // means either no membership at all, or a membership that doesn't
            // reach this class's month.
            val covering = me?.coveringPeriodForClass(sessionStartIso)
            val hasAnyWallet = me != null && (me.currentPeriod != null || me.upcomingPeriod != null)
            // Already-booked outranks coverage — an existing booking is always
            // shown/cancellable regardless of which wallet paid for it.
            if (existing == null && covering == null && hasAnyWallet) {
                // Member holds a wallet, but not for this class's month.
                _outsideWindow.value = OutsideWindowInfo(
                    heldMonths = me!!.coveredMonthsPhrase() ?: "your current",
                    classMonth = classCohortMonthName(sessionStartIso) ?: "this month",
                )
                _creditsRemaining.value = null
                // Inert; the screen overrides the label to "OUTSIDE YOUR MEMBERSHIP".
                _ctaState.value = BookCta.NotBookable
            } else {
                _outsideWindow.value = null
                // Show the balance of the wallet that will actually pay for THIS
                // class (current vs the next-month wallet), not just the current one.
                _creditsRemaining.value = covering?.creditsRemaining
                _ctaState.value = bookCtaState(spotsAvailable, covering, alreadyBooked = existing != null)
            }
            _loaded.value = true
        }
    }

    /** Set from the class-detail payload (`should_ask_studio_visit`). */
    fun setShouldAskStudioVisit(shouldAsk: Boolean) {
        _shouldAskStudioVisit.value = shouldAsk
    }

    /** Set from the class-detail payload's template (`spot_preference_*`). */
    fun setSpotPreferenceOptions(options: List<String>, label: String?) {
        _spotPreferenceOptionsRaw = options
        _spotPreferenceLabel = label
    }

    fun selectSpotPreference(value: String) {
        _selectedSpotPreference.value = value
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
    // The spot preference is always optional (best-effort) — it never gates
    // confirm; a member can book without choosing one.
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
                // Only forward a preference when the dropdown is actually active —
                // never leak a stale value from a suppressed (real-spot) class.
                val spotPreference = if (spotPreferenceActive) _selectedSpotPreference.value else null
                val b = bookingApi.createBooking(sessionId, _selectedSpot.value?.id, _visitedBefore.value, spotPreference)
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
                // The server named a reason. Always more useful than a category.
                telemetry.bookingFailed(e.code, sessionId)
                _submitState.value = BookingSubmit.Failed(e.code)
            } catch (e: Exception) {
                // No typed reason from the server — classify by transport so a
                // connection failure doesn't read as the same generic copy as a server failure.
                val code = e.transportFailureCode()
                telemetry.bookingFailed(code, sessionId)
                telemetry.recordError(e, mapOf("op" to "createBooking", "session_id" to sessionId))
                _submitState.value = BookingSubmit.Failed(code)
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
                // Computed once for both telemetry and UI state so they can't
                // disagree — mirrors confirmBooking's catch block above.
                val code = e.transportFailureCode()
                telemetry.bookingCancelFailed(booking.id, code)
                telemetry.recordError(e, mapOf("op" to "cancelBooking", "booking_id" to booking.id))
                _cancelState.value = CancelState.Failed(code)
            }
        }
    }
}
