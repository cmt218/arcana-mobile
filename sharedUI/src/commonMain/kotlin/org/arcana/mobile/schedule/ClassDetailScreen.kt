@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package org.arcana.mobile.schedule

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.arcana.mobile.booking.BookCta
import org.arcana.mobile.booking.BookingInfoCallout
import org.arcana.mobile.booking.BookingSheet
import org.arcana.mobile.booking.BookingStudioContext
import org.arcana.mobile.booking.BookingSubmit
import org.arcana.mobile.booking.BookingViewModel
import org.arcana.mobile.booking.bookingInfoOrNull
import org.arcana.mobile.booking.CancelReservationSheet
import org.arcana.mobile.booking.CancelState
import org.arcana.mobile.booking.bookingErrorCopy
import org.arcana.mobile.booking.outsideWindowCopy
import org.arcana.mobile.booking.useBookingGestures
import org.arcana.mobile.data.ScheduleSessionDto
import org.arcana.mobile.networking.ErrorType
import org.arcana.mobile.theme.Arcana
import org.arcana.mobile.theme.ArcanaShapes
import org.arcana.mobile.theme.Ash
import org.arcana.mobile.theme.Ash2
import org.arcana.mobile.theme.Atmosphere
import org.arcana.mobile.theme.BurntNectar
import org.arcana.mobile.theme.Dur
import org.arcana.mobile.theme.Ease
import org.arcana.mobile.theme.Graphite
import org.arcana.mobile.theme.Ink
import org.arcana.mobile.theme.Lime
import org.arcana.mobile.theme.Mist
import org.arcana.mobile.theme.Mist2
import org.arcana.mobile.theme.Moss
import org.arcana.mobile.theme.MossLight
import org.arcana.mobile.theme.Surface
import org.arcana.mobile.theme.Springs
import org.arcana.mobile.theme.Stone
import org.arcana.mobile.theme.Warning
import org.arcana.mobile.ui.AddressRow
import org.arcana.mobile.ui.studioColorFor
import org.arcana.mobile.ui.ArcanaIcons
import org.arcana.mobile.ui.ArcanaPullToRefreshBox
import org.arcana.mobile.ui.BodyText
import org.arcana.mobile.ui.Caption
import org.arcana.mobile.ui.CircleMonogram
import org.arcana.mobile.ui.Display
import org.arcana.mobile.ui.DotMatrixLoader
import org.arcana.mobile.ui.DotMatrixLoaderCompact
import org.arcana.mobile.ui.ErrorCopy
import org.arcana.mobile.ui.ErrorSnackbar
import org.arcana.mobile.ui.FullScreenError
import org.arcana.mobile.ui.Overline
import org.arcana.mobile.ui.PrimaryCta
import org.arcana.mobile.ui.SectionRule
import org.arcana.mobile.ui.StrokeIcon
import org.arcana.mobile.ui.TransientSurface
import org.arcana.mobile.ui.cardShadow
import org.arcana.mobile.ui.controlShadow
import org.arcana.mobile.ui.innerHighlight
import org.arcana.mobile.ui.opticallyCentredCapsVertical
import org.arcana.mobile.ui.pressable
import org.arcana.mobile.ui.pressedShade
import org.arcana.mobile.ui.recedeBehindSheet
import org.arcana.mobile.ui.rememberHaptics
import org.arcana.mobile.ui.rememberPressed
import org.arcana.mobile.ui.safeBottomBarPadding
import org.arcana.mobile.ui.safeContentPadding
import org.arcana.mobile.ui.safeHorizontalPadding
import org.arcana.mobile.ui.softShadow
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

// ── Constants -----------------------------------------------------------------

// Keep the booking sheet mounted this long after a gesture booking so the
// in-place BOOKED check is seen before the VM-closed sheet unmounts.
private const val CONFIRM_HOLD_MS = 550L

// Copy of Month.abbr() from ScheduleScreen — small intentional duplication
// to keep both files self-contained without promoting the helper to internal.
private fun Month.abbr(): String = name.take(3)

// ── "Booking opens …" copy (Mariana Tek windows) ─────────────────────────────

/**
 * Eastern Time. Mariana Tek booking windows are defined in ET wall-clock
 * ("opens Monday 11 AM ET"); we localize the "OPENS …" copy to ET specifically
 * — not the device zone — so it matches what the member sees on the studio's
 * own site, even if they're travelling.
 */
private val EasternTime = TimeZone.of("America/New_York")

/** "Mon" / "Jun" — title-cased three-letter abbreviation of an enum name. */
private fun shortTitle(name: String): String =
    name.take(3).lowercase().replaceFirstChar { it.uppercase() }

/** 12-hour clock parts for a local time: (hour 1–12, "AM"/"PM"). */
private fun LocalDateTime.hour12(): Pair<Int, String> =
    (((hour + 11) % 12) + 1) to (if (hour < 12) "AM" else "PM")

/**
 * Sticky-CTA label for a not-open class: "OPENS MON 11:00 AM ET". Always ET.
 * Pure + internal so it's unit-testable with a fixed [Instant].
 */
internal fun opensAtCtaLabel(opensAt: Instant): String {
    val dt = opensAt.toLocalDateTime(EasternTime)
    val (h12, ampm) = dt.hour12()
    val minute = dt.minute.toString().padStart(2, '0')
    return "OPENS ${dt.dayOfWeek.name.take(3)} $h12:$minute $ampm ET"
}

/**
 * Availability-block line for a not-open class: "Booking opens Mon, Jun 22 ·
 * 11:00 AM ET". Always ET.
 */
internal fun opensAtAvailabilityLine(opensAt: Instant): String {
    val dt = opensAt.toLocalDateTime(EasternTime)
    val (h12, ampm) = dt.hour12()
    val minute = dt.minute.toString().padStart(2, '0')
    val day = shortTitle(dt.dayOfWeek.name)
    val month = shortTitle(dt.month.name)
    return "Booking opens $day, $month ${dt.date.day} · $h12:$minute $ampm ET"
}

/**
 * Sticky-CTA label. Pure + internal so the precedence between "past",
 * "you hold a booking", "outside your membership" and "not open yet" is
 * unit-testable without a Compose harness.
 *
 * `✓` marks *you just did this*; a return visit shows the same status
 * without it. A direct-integration studio confirms inside the create call, so
 * the just-booked label follows the returned status instead of assuming the
 * manual queue's "requested".
 */
internal fun classDetailCtaLabel(
    isPast: Boolean,
    justBooked: Boolean,
    bookingStatus: String?,
    outsideWindow: Boolean,
    // Non-null only while the booking window is still shut.
    opensAt: Instant?,
    fallback: String,
): String = when {
    isPast -> "CLASS ENDED"
    justBooked -> when (bookingStatus) {
        "confirmed" -> "CONFIRMED ✓"
        "requested" -> "REQUESTED ✓"
        else -> "BOOKED ✓"
    }
    bookingStatus == "confirmed" -> "CONFIRMED ✓"
    bookingStatus == "requested" -> "REQUESTED"
    bookingStatus != null -> bookingStatus.uppercase()
    // Member holds a wallet but not for this class's month (e.g. a July-only
    // member on an August class). Outranks the booking window — even once it
    // opens they still can't book this month.
    outsideWindow -> "OUTSIDE YOUR MEMBERSHIP"
    opensAt != null -> opensAtCtaLabel(opensAt)
    else -> fallback
}

// DetailCapacity + computeDetailCapacity live in :sharedLogic schedule/ClassDetailLogic.kt
// (same package — unqualified references below resolve across the module boundary).

private fun ScheduleSessionDto.detailCapacity(notOpen: Boolean = false): DetailCapacity = computeDetailCapacity(
    available = arcanaSpotsAvailable,
    publishesCapacity = location.studio.publishesCapacity,
    notOpen = notOpen,
)

// ── Entry ---------------------------------------------------------------------

@Composable
fun ClassDetailScreen(
    sessionId: Int,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ClassDetailViewModel = koinViewModel { parametersOf(sessionId) },
) {
    val state by viewModel.uiState.collectAsState()
    val refreshing by viewModel.isRefreshing.collectAsState()
    val retrying by viewModel.retrying.collectAsState()

    // Outer Box on Stone — children handle their own safe-area padding so the
    // sticky CTA can sit flush with the bottom safe inset while the list scrolls
    // edge-to-edge underneath it.
    Box(modifier = modifier.fillMaxSize()) {
        Atmosphere()
        when (val s = state) {
            ClassDetailUiState.Loading -> LoadingBlock(onClose)
            is ClassDetailUiState.Error -> ErrorBlock(
                type = s.type,
                onClose = onClose,
                onRetry = viewModel::retry,
                retrying = retrying,
            )
            is ClassDetailUiState.Success -> SuccessBlock(
                session = s.session,
                onClose = onClose,
                isRefreshing = refreshing,
                onRefresh = viewModel::refresh,
            )
        }
    }
}

// ── Loading / Error -----------------------------------------------------------

@Composable
private fun LoadingBlock(onClose: () -> Unit) {
    BackHandler { onClose() }
    // TopBar overlays rather than stacks: stacking leaves only the space below
    // the bar, so the loader centres below the true middle.
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            DotMatrixLoader()
        }
        Box(modifier = Modifier.safeContentPadding()) {
            TopBar(onClose = onClose)
        }
    }
}

/** TopBar OVERLAYS [FullScreenError] rather than stacking above it: the close
 *  button stays usable either way, but stacking gave the error only the space
 *  below the bar, so the block centred lower than the identical error on Home
 *  and Schedule. Nothing here may consume the top inset before FullScreenError
 *  measures, or the centre shifts down by half the status bar. */
@Composable
private fun ErrorBlock(
    type: ErrorType,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    retrying: Boolean,
) {
    BackHandler { onClose() }
    Box(modifier = Modifier.fillMaxSize()) {
        FullScreenError(
            type = type,
            onRetry = onRetry,
            retrying = retrying,
            modifier = Modifier.fillMaxSize(),
        )
        Box(modifier = Modifier.safeContentPadding()) {
            TopBar(onClose = onClose)
        }
    }
}

// ── Success layout ------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuccessBlock(
    session: ScheduleSessionDto,
    onClose: () -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
) {
    // The studio's clock, not the device's — a traveller must see the same
    // time the schedule list and the studio's own site show.
    val tz = remember(session.location.timezone) { sessionTimeZone(session.location.timezone) }
    val startLocal = remember(session.startAt) { Instant.parse(session.startAt).toLocalDateTime(tz) }
    val studio = session.location.studio
    val sc = studioColorFor(studio.primaryColor)
    val isCancelled = session.status == "cancelled_by_studio"
    // Mariana Tek booking window: the instant the studio opens reservations for
    // this class (null = always open). When it's in the future the server zeroes
    // spots and rejects bookings; we render "NOT OPEN" with an "opens …" treatment
    // localized to Eastern Time, but keep the class fully viewable.
    val opensAt = remember(session.bookableAt) {
        session.bookableAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
    }
    val notOpenYet = remember(opensAt) { opensAt != null && Clock.System.now() < opensAt }
    val capacity = session.detailCapacity(notOpen = notOpenYet)
    // A class whose end time has passed: no availability + no live booking.
    val isPast = remember(session.endAt) {
        try { Instant.parse(session.endAt) < Clock.System.now() } catch (_: Exception) { false }
    }
    // Hoisted above the LazyColumn: a remember() inside CapacityPips (a lazy
    // item) is torn down on scroll-away and replays from empty on return.
    val pipsTaken = (session.arcanaSpotsOffered - session.arcanaSpotsAvailable).coerceAtLeast(0)
    // Capacity renders at its final value — no fill animation.
    val takenProgress = remember { Animatable(pipsTaken.toFloat()) }
    LaunchedEffect(pipsTaken) { takenProgress.snapTo(pipsTaken.toFloat()) }

    val requiresSpot = session.template.spotSelectionMode != "none"
    val bookingVm: BookingViewModel = koinViewModel {
        parametersOf(
            session.id, session.arcanaSpotsAvailable, requiresSpot, session.startAt,
            BookingStudioContext(studio.id, studio.name, session.location.id, session.location.name),
        )
    }
    LaunchedEffect(session.id) { bookingVm.load() }
    LaunchedEffect(session.id, session.shouldAskStudioVisit) {
        bookingVm.setShouldAskStudioVisit(session.shouldAskStudioVisit)
    }
    LaunchedEffect(session.id) {
        bookingVm.setSpotPreferenceOptions(
            options = session.template.spotPreferenceOptions,
            label = session.template.spotPreferenceLabel,
        )
    }
    val cta by bookingVm.ctaState.collectAsState()
    val sheetOpen by bookingVm.sheetOpen.collectAsState()
    val selectedSpot by bookingVm.selectedSpot.collectAsState()
    val shouldAskVisit by bookingVm.shouldAskStudioVisit.collectAsState()
    val visitedBefore by bookingVm.visitedBefore.collectAsState()
    val selectedSpotPreference by bookingVm.selectedSpotPreference.collectAsState()
    val credits by bookingVm.creditsRemaining.collectAsState()
    val coveredMonths by bookingVm.coveredMonths.collectAsState()
    // Non-null when the member holds a wallet but none covers this class's month
    // (e.g. July-only member on an August class) — overrides the CTA copy.
    val outsideWindow by bookingVm.outsideWindow.collectAsState()
    val submit by bookingVm.submitState.collectAsState()
    val existing by bookingVm.existingBooking.collectAsState()
    // The spot on the live booking (effective → fulfilled → requested), shown on
    // the CTA + cancel sheet for spot studios. Null for non-spot classes.
    val bookedSpotLabel = existing?.let { it.spot?.label ?: it.fulfilledSpot?.label ?: it.requestedSpot?.label }
    val loaded by bookingVm.loaded.collectAsState()
    val cancelSheetOpen by bookingVm.cancelSheetOpen.collectAsState()
    val cancelState by bookingVm.cancelState.collectAsState()
    val membershipLoadFailed by bookingVm.membershipLoadFailed.collectAsState()

    // While the VM is still fetching /me + /bookings, show a neutral spinner on
    // the CTA instead of the default "NOT AVAILABLE" flash. Past classes resolve
    // independently (isPast) once loaded.
    val ctaLoading = !loaded && submit is BookingSubmit.Idle
    val hasLiveBooking = existing != null
    val haptics = rememberHaptics()
    val useGestures = useBookingGestures()
    LaunchedEffect(submit) {
        when (submit) {
            is BookingSubmit.Booked -> haptics.confirm()
            is BookingSubmit.Failed -> haptics.reject()
            else -> Unit
        }
    }
    // Cancel success is the Submitting -> Idle transition; CancelState has no
    // Success. Tap path only — HoldToConfirm fires reject() itself on completion.
    var prevCancel by remember { mutableStateOf<CancelState>(CancelState.Idle) }
    LaunchedEffect(cancelState) {
        val was = prevCancel
        prevCancel = cancelState
        if (!useGestures && was is CancelState.Submitting && cancelState is CancelState.Idle) {
            haptics.reject()
        }
    }
    var holdForConfirm by remember { mutableStateOf(false) }
    LaunchedEffect(submit) {
        if (submit is BookingSubmit.Booked && useGestures) {
            holdForConfirm = true
            delay(CONFIRM_HOLD_MS)
            holdForConfirm = false
        }
    }

    // Route the system back gesture through onClose so it plays the X's
    // push-down (popExit) on both platforms, not the platform interactive-pop.
    // Disabled while a sheet is up so back dismisses the sheet first.
    BackHandler(enabled = !(sheetOpen || cancelSheetOpen || holdForConfirm)) { onClose() }

    // Scrollable list under a sticky CTA. The LazyColumn pads its bottom by
    // ~140dp so the last content can scroll out from behind the CTA without
    // ever being permanently obscured.
    // The atmosphere lives on the screen's root, so the surface exposed around the
    // receding page is the same living one, dimmed by the scrim, not flat Stone.
    val bookingSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val cancelSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Drive the recede off the sheet's target, not the VM boolean: targetValue flips
    // to Hidden the instant a dismiss begins, so the page tracks the sheet down as it
    // slides instead of un-shrinking after it is already gone.
    val bookingReceding = (sheetOpen && bookingSheetState.targetValue != SheetValue.Hidden) || holdForConfirm
    val cancelReceding = cancelSheetOpen && cancelSheetState.targetValue != SheetValue.Hidden

    Box(modifier = Modifier.fillMaxSize().recedeBehindSheet(open = bookingReceding || cancelReceding)) {
        ArcanaPullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                onRefresh()
                bookingVm.load()
            },
            modifier = Modifier.fillMaxSize(),
        ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().safeContentPadding(),
            contentPadding = PaddingValues(bottom = 140.dp),
        ) {
            item("topbar") {
                TopBar(onClose = onClose)
            }
            item("hero") {
                HeroCard(
                    studioName = studio.name,
                    locationShort = session.location.shortLabel(),
                    modality = session.template.modality,
                    title = session.template.name,
                    studioColor = sc,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            item("summary") {
                Spacer(Modifier.height(16.dp))
                SummaryStrip(
                    startLocal = startLocal,
                    durationMinutes = session.durationMinutes,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            bookingInfoOrNull(existing)?.let { note ->
                item("booking-info") {
                    Spacer(Modifier.height(24.dp))
                    BookingInfoCallout(
                        note = note,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }
            item("instructor") {
                val instructor = session.instructors.firstOrNull()
                if (instructor != null) {
                    Spacer(Modifier.height(20.dp))
                    InstructorRow(
                        name = instructor.name,
                        studioColor = sc,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }
            if (isCancelled) {
                item("cancelled") {
                    Spacer(Modifier.height(24.dp))
                    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                        SectionRule(label = "Cancelled")
                        Spacer(Modifier.height(8.dp))
                        BodyText(
                            text = "This class has been cancelled by the studio.",
                            size = 14, color = Warning,
                        )
                    }
                }
            } else if (!isPast) {
                item("availability") {
                    Spacer(Modifier.height(24.dp))
                    AvailabilityBlock(
                        offered = session.arcanaSpotsOffered,
                        available = session.arcanaSpotsAvailable,
                        capacity = capacity,
                        publishesCapacity = studio.publishesCapacity,
                        studioColor = sc,
                        takenProgress = takenProgress,
                        // Non-null for a not-open class — replaces the spot count
                        // with "Booking opens …" (ET).
                        opensLine = if (notOpenYet) opensAtAvailabilityLine(opensAt!!) else null,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }
            if (session.template.description.isNotBlank()) {
                item("about") {
                    Spacer(Modifier.height(24.dp))
                    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                        SectionRule(label = "About this class")
                        Spacer(Modifier.height(12.dp))
                        BodyText(text = session.template.description, size = 14, color = Ink)
                    }
                }
            }
            item("location") {
                Spacer(Modifier.height(24.dp))
                LocationRow(
                    studioName = studio.name,
                    locationName = session.location.name,
                    address = session.location.address,
                    latitude = session.location.latitude,
                    longitude = session.location.longitude,
                    studioColor = sc,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        }
        }
        // Sticky reserve CTA — pinned to bottom safe inset. Capped fade above
        // it so scrolling list content feathers out instead of butting hard
        // against the pill.
        if (!isCancelled) {
            val ctaLabel = classDetailCtaLabel(
                isPast = isPast,
                justBooked = submit is BookingSubmit.Booked,
                bookingStatus = existing?.status,
                outsideWindow = outsideWindow != null,
                opensAt = if (notOpenYet) opensAt else null,
                fallback = cta.label,
            )
            // For the out-of-window state, the sub-line explains the coverage gap
            // in the member's actual months, e.g. "July credits don't cover August."
            val ctaSubOverride = outsideWindow?.let {
                "${it.heldMonths} credits don't cover ${it.classMonth}."
            }
            // The no-active-membership state reads as one clear line — drop the
            // time/day sub-stamp. It's the only state that renders NotBookable's
            // label (past/booked/out-of-window/not-open all take precedence above).
            val showCtaSubStamp =
                ctaLabel != BookCta.NotBookable.label && ctaLabel != BookCta.Unknown.label
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(2f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
            TransientSurface(
                visible = membershipLoadFailed,
                modifier = Modifier
                    .safeHorizontalPadding()
                    // The CTA opens with a 40dp fade before its pill, so
                    // sitting flush above it leaves 48dp to the PILL. Drop
                    // into the fade to match the pill's own bottom gap.
                    .offset(y = CTA_FADE_HEIGHT + CTA_EDGE_GAP - CTA_PILL_GAP)
                    // Above the fade: the CTA is a later sibling, so its
                    // gradient would paint across the bar as a glow.
                    .zIndex(1f),
            ) {
                ErrorSnackbar(
                    text = ErrorCopy.REFRESH_FAILED,
                    // Same refresh the pull gesture drives, or the top indicator
                    // never appears and the retry looks inert.
                    onRetry = {
                        bookingVm.dismissMembershipLoadFailed()
                        onRefresh()
                        bookingVm.load()
                    },
                    onDismiss = bookingVm::dismissMembershipLoadFailed,
                )
            }
            StickyReserveCta(
                capacity = capacity,
                available = session.arcanaSpotsAvailable,
                startLocal = startLocal,
                label = ctaLabel,
                showSubStamp = showCtaSubStamp,
                subStampOverride = ctaSubOverride,
                // Show the booked spot on the CTA's sub-line for spot studios.
                spotLabel = bookedSpotLabel,
                loading = ctaLoading,
                pulse = submit is BookingSubmit.Booked,
                // Tappable when there's a live booking (→ cancel) or the class is
                // bookable; while loading the CTA is inert and shows a spinner. A
                // not-open class (no live booking) is inert until the window opens.
                enabled = !isPast && !ctaLoading && (hasLiveBooking || (cta.enabled && !notOpenYet)),
                onClick = {
                    when {
                        ctaLoading || isPast -> {}
                        hasLiveBooking -> bookingVm.openCancelSheet()
                        cta == BookCta.Bookable -> bookingVm.openSheet()
                        else -> {}
                    }
                },
            )
            }
        }
    }

    if (sheetOpen || holdForConfirm) {
        // A failed attempt renders inside the sheet (replacing the confirm UI)
        // rather than as a top banner that collides with the camera punch-out.
        val bookingError = (submit as? BookingSubmit.Failed)?.let { f ->
            if (f.code == "session_outside_window") outsideWindowCopy(coveredMonths)
            else bookingErrorCopy(f.code)
        }
        BookingSheet(
            session = session,
            requiresSpot = requiresSpot,
            selectedSpot = selectedSpot,
            creditsRemaining = credits,
            onSelectSpot = bookingVm::selectSpot,
            shouldAskStudioVisit = shouldAskVisit,
            visitedBefore = visitedBefore,
            onAnswerVisit = bookingVm::answerStudioVisit,
            spotPreferenceOptions = bookingVm.spotPreferenceOptions,
            spotPreferenceLabel = bookingVm.spotPreferenceLabel,
            selectedSpotPreference = selectedSpotPreference,
            onSelectSpotPreference = bookingVm::selectSpotPreference,
            confirmEnabled = bookingVm.canConfirm,
            submitting = submit is BookingSubmit.Submitting,
            errorMessage = bookingError,
            onConfirm = bookingVm::confirmBooking,
            onDismiss = { if (!holdForConfirm) bookingVm.dismissSheet() },
            booked = submit is BookingSubmit.Booked,
            bookedStatus = existing?.status,
            sheetState = bookingSheetState,
        )
    }
    if (cancelSheetOpen) {
        CancelReservationSheet(
            className = session.template.name,
            spotLabel = bookedSpotLabel,
            willForfeitCredit = existing?.cancelPolicy?.willForfeitCredit == true,
            cancelState = cancelState,
            onConfirm = bookingVm::confirmCancel,
            onDismiss = bookingVm::dismissCancelSheet,
            sheetState = cancelSheetState,
        )
    }
}

// ── Top bar -------------------------------------------------------------------

@Composable
private fun TopBar(onClose: () -> Unit) {
    // Bookmark + share are post-beta follow-ups; just the close affordance now.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(
            icon = ArcanaIcons.Close,
            onClick = onClose,
            contentDescription = "Close class details",
        )
    }
}

@Composable
private fun CircleIconButton(
    icon: org.jetbrains.compose.resources.DrawableResource,
    onClick: () -> Unit,
    contentDescription: String,
) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(38.dp)
            .pressable(source, pressedScale = 0.94f)
            .softShadow(CircleShape)
            .clip(CircleShape)
            .background(Surface)
            .border(1.dp, Ash, CircleShape)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        StrokeIcon(
            icon = icon,
            size = 18.dp,
            tint = Ink,
            contentDescription = contentDescription,
        )
    }
}

// ── Hero card -----------------------------------------------------------------

/**
 * Studio-tinted plate. Replaces the empty greenish band from before-class-detail.
 * Subtle vertical gradient in the brand color (15% → 8%), a 1px brand@20% border,
 * a 14dp radius, and a soft dot field rotated -3° anchored to the top-right
 * corner of the card. Foreground content is a brand+location chip, the modality
 * overline, and the class title (max 2 lines).
 */
@Composable
private fun HeroCard(
    studioName: String,
    locationShort: String,
    modality: String,
    title: String,
    studioColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .cardShadow(ArcanaShapes.Hero)
            .clip(ArcanaShapes.Hero)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        studioColor.copy(alpha = 0.15f),
                        studioColor.copy(alpha = 0.08f),
                    ),
                ),
            )
            .border(1.dp, studioColor.copy(alpha = 0.20f), ArcanaShapes.Hero),
    ) {
        Column(
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 20.dp),
        ) {
            // Brand + location chip — paper bg, brand-color text + border.
            BrandLocationChip(
                studioName = studioName,
                locationShort = locationShort,
                studioColor = studioColor,
            )
            Spacer(Modifier.height(14.dp))
            if (modality.isNotBlank()) {
                Overline(text = modality, size = 10, color = Ash)
                Spacer(Modifier.height(8.dp))
            }
            Display(
                text = title,
                size = 42,
                color = Ink,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun BrandLocationChip(
    studioName: String,
    locationShort: String,
    studioColor: Color,
) {
    Row(
        modifier = Modifier
            .softShadow(CircleShape)
            .clip(CircleShape)
            .background(Surface)
            .border(1.dp, studioColor.copy(alpha = 0.35f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(studioColor))
        Text(
            text = studioName.uppercase(),
            maxLines = 1, softWrap = false,
            style = TextStyle(
                fontFamily = Arcana.fonts.body,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 0.22.em,
                color = studioColor,
            ),
        )
        if (locationShort.isNotEmpty()) {
            Box(Modifier.size(3.dp).clip(CircleShape).background(studioColor.copy(alpha = 0.55f)))
            Text(
                text = locationShort,
                maxLines = 1, softWrap = false,
                style = TextStyle(
                    fontFamily = Arcana.fonts.body,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    letterSpacing = 0.20.em,
                    color = studioColor.copy(alpha = 0.78f),
                ),
            )
        }
    }
}

// ── Summary strip -------------------------------------------------------------

/** Three-column hairline-divided stat row: WHEN / TIME / DURATION. Heat was
 *  dropped because the backend doesn't yet expose a temperature field — see
 *  the design-brief follow-up notes. */
@Composable
private fun SummaryStrip(
    startLocal: LocalDateTime,
    durationMinutes: Int,
    modifier: Modifier = Modifier,
) {
    val day = startLocal.dayOfWeek.name.take(3)
    val dateLine = "${startLocal.date.day} ${startLocal.date.month.abbr()}"
    val hour12 = ((startLocal.hour + 11) % 12) + 1
    val ampm = if (startLocal.hour < 12) "AM" else "PM"
    val time = "${hour12.toString().padStart(2, '0')}:${startLocal.minute.toString().padStart(2, '0')}"

    Column(modifier = modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(MossLight))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            SummaryCell(label = "WHEN", value = day, unit = dateLine, modifier = Modifier.weight(1f))
            VerticalHairline()
            SummaryCell(label = "TIME", value = time, unit = ampm, modifier = Modifier.weight(1f))
            VerticalHairline()
            SummaryCell(label = "DURATION", value = durationMinutes.toString(), unit = "MIN", modifier = Modifier.weight(1f))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(MossLight))
    }
}

@Composable
private fun SummaryCell(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
    ) {
        Overline(text = label, size = 9, color = Ash)
        Spacer(Modifier.height(8.dp))
        Display(text = value, size = 22, color = Ink)
        Spacer(Modifier.height(4.dp))
        Overline(text = unit, size = 9, color = Ash)
    }
}

@Composable
private fun VerticalHairline() {
    // Horizontal padding gives the hairline its own breathing room so adjacent
    // cell values ("07:00", "45") don't crowd the bar — the 1dp line stays thin
    // but sits inset 12dp from the values on either side.
    Box(Modifier.padding(horizontal = 12.dp).width(1.dp).height(56.dp).background(MossLight))
}

// ── Instructor row ------------------------------------------------------------

/** Single-instructor row with avatar circle (initials) and a "TAUGHT BY / NAME"
 *  block. Lineage and years are intentionally omitted — those fields don't
 *  exist on [org.arcana.mobile.data.InstructorBriefDto] today. Instructor
 *  profiles are a post-beta follow-up, so the row is non-interactive for now. */
@Composable
private fun InstructorRow(
    name: String,
    studioColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Mist2)
                .border(1.5.dp, studioColor.copy(alpha = 0.33f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            CircleMonogram(
                text = initialsOf(name),
                fontSize = 16,
                color = studioColor,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Overline(text = "TAUGHT BY", size = 10, color = Ash)
            Spacer(Modifier.height(4.dp))
            Display(text = name, size = 18, color = Ink)
        }
    }
}

private fun initialsOf(name: String): String {
    val parts = name.trim().split(' ').filter { it.isNotBlank() }
    if (parts.isEmpty()) return "?"
    val first = parts.first().firstOrNull()?.uppercaseChar() ?: '?'
    val last = parts.drop(1).lastOrNull()?.firstOrNull()?.uppercaseChar()
    return if (last != null) "$first$last" else first.toString()
}

// ── Availability block --------------------------------------------------------

/**
 * "N OF M SPOTS OPEN" headline + segmented pip row + status copy. Each pip
 * represents one spot — reads as a counter, not just a progress bar. The open
 * (trailing) pips carry the state color — moss when open, warning when scarce,
 * ash when full — so available capacity is the prominent signal; taken pips
 * recede to Mist@70 (stone).
 */
@Composable
private fun AvailabilityBlock(
    offered: Int,
    available: Int,
    capacity: DetailCapacity,
    publishesCapacity: Boolean,
    studioColor: Color,
    takenProgress: Animatable<Float, AnimationVector1D>,
    modifier: Modifier = Modifier,
    // Non-null for a not-open Mariana Tek class: "Booking opens Mon, Jun 22 ·
    // 11:00 AM ET". When present it replaces the spot-count headline + pips —
    // there's no meaningful capacity to show before the window opens.
    opensLine: String? = null,
) {
    val taken = (offered - available).coerceAtLeast(0)
    Column(modifier = modifier) {
        SectionRule(label = "Availability", accent = true)
        Spacer(Modifier.height(14.dp))
        if (opensLine != null) {
            Display(text = "NOT OPEN", size = 20, color = Ink, weight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            BodyText(text = opensLine, size = 14, color = Ash)
        } else if (publishesCapacity) {
            // Precise form: "N OF M SPOTS OPEN" + the segmented pip strip
            // showing exact taken-vs-open.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val headline = if (available <= 0) "FULLY BOOKED"
                else "$available OF $offered SPOTS OPEN"
                Display(text = headline, size = 20, color = Ink, weight = FontWeight.Bold)
                Overline(text = "$taken / $offered TAKEN", size = 10, color = Ash)
            }
            if (offered > 0) {
                Spacer(Modifier.height(12.dp))
                CapacityPips(
                    offered = offered,
                    capacity = capacity, studioColor = studioColor,
                    takenProgress = takenProgress,
                )
            }
        } else {
            // Hidden-capacity form: studio doesn't publish exact counts
            // (e.g. ID Hot Yoga — their own first-party app hides them too).
            // Render binary AVAILABLE / FULLY BOOKED without pips or
            // "N of M" — claiming numbers we don't actually have would be
            // worse than the simpler signal.
            val headline = if (available <= 0) "FULLY BOOKED" else "AVAILABLE"
            Display(text = headline, size = 20, color = Ink, weight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CapacityPips(
    offered: Int,
    capacity: DetailCapacity,
    studioColor: Color,
    // Owned by SuccessBlock, above the LazyColumn, so the sweep survives this
    // composable's own lazy-item disposal on scroll-away.
    takenProgress: Animatable<Float, AnimationVector1D>,
) {
    // Available capacity is the prominent signal: open pips carry the state
    // color (moss when open, warning when scarce), taken pips recede to stone.
    val openColor = when (capacity) {
        DetailCapacity.Open -> MossLight
        DetailCapacity.Scarce -> Warning
        DetailCapacity.Full -> Ash2
        // Not reached — not-open classes render the "opens …" line, not pips —
        // but the when must be exhaustive.
        DetailCapacity.NotOpen -> Ash2
    }
    // Suppress unused-parameter warning while keeping the API future-proof —
    // when brand-tinted pips land in a later iteration, the studioColor will
    // be the source.
    @Suppress("UNUSED_VARIABLE") val tint = studioColor
    val takenColor = Mist.copy(alpha = 0.70f)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(offered) { i ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(takenColor)
                    // Draw phase only, so a 20-pip row doesn't recompose every
                    // animation frame.
                    .drawBehind { if (i >= takenProgress.value) drawRect(openColor) },
            )
        }
    }
}

// ── Location row --------------------------------------------------------------

/** The class's location as an [AddressRow]: Pin avatar, "LOCATION" overline,
 *  name and address. Tapping opens the maps sheet. */
@Composable
private fun LocationRow(
    studioName: String,
    locationName: String,
    address: String,
    latitude: Double?,
    longitude: Double?,
    studioColor: Color,
    modifier: Modifier = Modifier,
) {
    AddressRow(
        name = if (locationName.isNotBlank()) locationName else studioName,
        businessName = listOf(studioName, locationName).filter { it.isNotBlank() }.distinct().joinToString(" "),
        address = address,
        latitude = latitude,
        longitude = longitude,
        surface = "class_detail",
        overline = "LOCATION",
        nameAsDisplay = true,
        modifier = modifier,
        leading = {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Mist2)
                    .border(1.5.dp, studioColor.copy(alpha = 0.33f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                // decorative — the row carries the accessible label.
                StrokeIcon(icon = ArcanaIcons.Pin, size = 22.dp, tint = studioColor)
            }
        },
    )
}

// Shared by the primary label's style and its optical nudge: separate
// literals would drift and silently un-centre the label (ui/Buttons.kt CTA_LABEL_SIZE).
private val CTA_PRIMARY_LABEL_SIZE = 14.sp
private const val CTA_PRIMARY_LABEL_TRACKING_EM = 0.10f

// ── Sticky CTA ----------------------------------------------------------------

/**
 * Pinned-to-bottom reserve pill with a transparent→stone gradient above it so
 * the scrolling list content feathers out instead of butting against the pill.
 * State-driven colors:
 * - Open   → moss pill, lime arrow well
 * - Scarce → warning pill, lime arrow well
 * - Full / disabled → graphite pill, stone clock well
 *
 * Sits above the home-indicator safe inset via [safeBottomBarPadding].
 */
@Composable
private fun StickyReserveCta(
    capacity: DetailCapacity,
    available: Int,
    startLocal: LocalDateTime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    spotLabel: String? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    // When false, the time/day sub-stamp is hidden and the primary label reads as
    // a single centered line (used for the "no active membership" state).
    showSubStamp: Boolean = true,
    // Replaces the computed time/day sub-stamp with custom text when non-null
    // (used for the "outside your membership" coverage explanation).
    subStampOverride: String? = null,
    pulse: Boolean = false,
) {
    val pillShape = RoundedCornerShape(22.dp)
    val pillColor = when {
        loading -> Graphite
        !enabled -> Graphite
        capacity == DetailCapacity.Full -> Graphite
        // Scarce stays green like Open — scarcity reads from the "only N left"
        // label + availability block, not a yellow button.
        else -> Moss
    }
    val arrowWellColor = if (capacity == DetailCapacity.Full || !enabled) Stone else Lime
    val arrowIcon = if (capacity == DetailCapacity.Full || !enabled) ArcanaIcons.Clock else ArcanaIcons.ArrowRight
    // `label` is always supplied by the caller (ClassDetailScreen computes the
    // full state machine — past/booked/not-open/eligibility — and passes it in),
    // so this `when` is just an exhaustive fallback.
    val primaryLabel = label ?: when (capacity) {
        DetailCapacity.Open -> "RESERVE THIS SPOT"
        DetailCapacity.Scarce -> "RESERVE: ONLY $available LEFT"
        DetailCapacity.Full -> "CLASS FULL"
        DetailCapacity.NotOpen -> "NOT OPEN"
    }
    val subStamp = remember(startLocal, spotLabel, subStampOverride) {
        val hour12 = ((startLocal.hour + 11) % 12) + 1
        val ampm = if (startLocal.hour < 12) "AM" else "PM"
        val timeStamp = "${hour12.toString().padStart(2, '0')}:${startLocal.minute.toString().padStart(2, '0')} $ampm"
        val dayStamp = "${startLocal.dayOfWeek.name.take(3)} ${startLocal.date.day} ${startLocal.date.month.abbr()}"
        subStampOverride
            ?: if (spotLabel != null) "$timeStamp · $dayStamp · ${spotLabel.uppercase()}" else "$timeStamp · $dayStamp"
    }
    val glow = remember { Animatable(0f) }
    LaunchedEffect(pulse) {
        if (!pulse) return@LaunchedEffect
        glow.animateTo(1f, tween(Dur.Short, easing = Ease.Emphasized))
        glow.animateTo(0f, tween(Dur.Medium, easing = Ease.Exit))
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // No scrim: the page's atmosphere sits behind the CTA, and the list's
        // 140dp bottom pad already clears content above the pill. A Stone fade
        // here read as a white bloom over the lime surface.
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            if (loading) {
                // Neutral pill with a centered compact dot loader while the VM
                // resolves eligibility — no label, no arrow well, inert.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(pillShape)
                        .background(pillColor),
                    contentAlignment = Alignment.Center,
                ) {
                    DotMatrixLoaderCompact()
                }
            } else {
                val source = remember { MutableInteractionSource() }
                val pressed by rememberPressed(source)
                val fill by animateColorAsState(
                    targetValue = if (pressed && enabled) pillColor.pressedShade() else pillColor,
                    animationSpec = tween(Dur.Quick),
                    label = "stickyCtaFill",
                )
                val kick by animateDpAsState(
                    targetValue = if (pressed && enabled) 2.dp else 0.dp,
                    animationSpec = Springs.kick(),
                    label = "stickyCtaKick",
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .pressable(source, enabled)
                        .then(if (enabled) Modifier.controlShadow(pillShape) else Modifier)
                        .clip(pillShape)
                        .background(fill)
                        .then(if (enabled) Modifier.innerHighlight(pillShape) else Modifier)
                        .clickable(enabled = enabled, interactionSource = source, indication = null, onClick = onClick)
                        .padding(start = 20.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = primaryLabel,
                            maxLines = 1,
                            modifier = Modifier.opticallyCentredCapsVertical(CTA_PRIMARY_LABEL_SIZE),
                            style = TextStyle(
                                fontFamily = Arcana.fonts.display,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = CTA_PRIMARY_LABEL_SIZE,
                                letterSpacing = CTA_PRIMARY_LABEL_TRACKING_EM.em,
                                color = Stone,
                            ),
                        )
                        if (showSubStamp) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = subStamp,
                                maxLines = 1,
                                style = TextStyle(
                                    fontFamily = Arcana.fonts.body,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 9.sp,
                                    letterSpacing = 0.10.em,
                                    color = Stone.copy(alpha = 0.67f),
                                ),
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(kick.roundToPx(), 0) }
                            .size(44.dp)
                            .graphicsLayer { val s = 1f + 0.12f * glow.value; scaleX = s; scaleY = s }
                            .drawBehind { drawCircle(Lime.copy(alpha = 0.35f * glow.value), radius = size.minDimension * (0.5f + 0.35f * glow.value)) }
                            .clip(CircleShape)
                            .background(arrowWellColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        // decorative — the reserve CTA's own label names it.
                        StrokeIcon(icon = arrowIcon, size = 18.dp, tint = Ink)
                    }
                }
            }
        }
        // Home-indicator inset — transparent so the atmosphere shows through
        // rather than a white Stone strip under the gesture bar.
        Box(Modifier.fillMaxWidth().safeBottomBarPadding())
    }
}

/** The CTA's own vertical inset. The refresh snackbar reuses it so the gap
 *  above the CTA matches the gap below it. */
private val CTA_EDGE_GAP = 8.dp

/** The CTA's leading transparent→Stone fade — invisible, but it occupies real
 *  space above the pill, so anything stacked above the CTA must account for it. */
private val CTA_FADE_HEIGHT = 40.dp

/** Target gap between the refresh snackbar and the visible CTA pill. Matches the
 *  gap the pill leaves against the bottom of the screen. */
private val CTA_PILL_GAP = 32.dp

// ── Booking error banner -------------------------------------------------------

@Composable
private fun BookingErrorBanner(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Caption(message, size = 13, color = BurntNectar, maxLines = 3)
    }
}

// `LocationBriefDto.shortLabel()` lives in :sharedLogic schedule/ScheduleViewModel.kt (public for cross-module access).

