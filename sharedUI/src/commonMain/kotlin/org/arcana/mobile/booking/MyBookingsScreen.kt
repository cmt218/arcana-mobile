@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package org.arcana.mobile.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.time.Clock
import kotlinx.datetime.todayIn
import org.arcana.mobile.data.BookingDto
import org.arcana.mobile.schedule.LOAD_MORE_LOOKAHEAD
import org.arcana.mobile.schedule.JumpToTop
import org.arcana.mobile.schedule.ScheduleViewModel
import org.arcana.mobile.schedule.wallClock
import org.arcana.mobile.theme.*
import org.arcana.mobile.ui.AddressLink
import org.arcana.mobile.ui.ArcanaIcons
import org.arcana.mobile.ui.ArcanaPullToRefreshBox
import org.arcana.mobile.ui.BodyText
import org.arcana.mobile.ui.Caption
import org.arcana.mobile.ui.ErrorCopy
import org.arcana.mobile.ui.ErrorSnackbar
import org.arcana.mobile.ui.FullScreenError
import org.arcana.mobile.ui.chromeBottom
import org.arcana.mobile.ui.GhostCta
import org.arcana.mobile.ui.Heading2
import org.arcana.mobile.ui.IconCircle
import org.arcana.mobile.ui.InlineError
import org.arcana.mobile.ui.Overline
import org.arcana.mobile.ui.SegmentedControl
import org.arcana.mobile.ui.ShimmerBox
import org.arcana.mobile.ui.StatusPill
import org.arcana.mobile.ui.TextLink
import org.arcana.mobile.data.ReviewDto
import org.arcana.mobile.review.ReviewCard
import org.arcana.mobile.review.ReviewCardStyle
import org.arcana.mobile.review.ReviewSaveNoticeHost
import org.arcana.mobile.review.ReviewSubjects
import org.arcana.mobile.review.rememberReviewSaveNotice
import org.arcana.mobile.ui.TransientSurface
import org.arcana.mobile.ui.pressable
import org.arcana.mobile.ui.recedeBehindSheet
import org.arcana.mobile.ui.rememberHaptics
import org.arcana.mobile.ui.safeContentPadding
import org.arcana.mobile.ui.safeHorizontalPadding
import org.koin.compose.viewmodel.koinViewModel

private const val EMPTY_UPCOMING_TITLE = "Nothing reserved yet."
private const val EMPTY_UPCOMING_BODY = "Your week is open."
private const val EMPTY_PAST = "Your first class will show here."
private const val BOOK_A_CLASS = "Book a class"
private const val REVIEWED = "Reviewed"

/** Reservations: Upcoming and Past segments. The route is still `MyBookings`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyBookingsScreen(
    source: String,
    onClose: () -> Unit,
    onOpenClass: (Int) -> Unit,
    onBookClass: () -> Unit,
) {
    val vm = koinViewModel<MyBookingsViewModel>()
    LaunchedEffect(Unit) {
        vm.onOpened(source)
        vm.load()
    }
    val segment by vm.segment.collectAsState()
    val upcoming by vm.upcoming.collectAsState()
    val past by vm.past.collectAsState()
    val isRefreshing by vm.isRefreshing.collectAsState()
    val refreshFailed by vm.refreshFailed.collectAsState()
    val retrying by vm.retrying.collectAsState()
    val cancelTarget by vm.cancelTarget.collectAsState()
    val cancelState by vm.cancelState.collectAsState()
    val openReviews by vm.openReviews.collectAsState()
    val reviewNotice = rememberReviewSaveNotice()
    val haptics = rememberHaptics()

    BackHandler(enabled = cancelTarget == null) { onClose() }
    val cancelSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Off the sheet's target, not the VM value, so the page tracks the sheet down.
    val cancelReceding = cancelTarget != null && cancelSheetState.targetValue != SheetValue.Hidden

    // A cold failure of the segment on screen. It is drawn UNDER the header on the
    // whole screen, not in the list below it: see FullScreenError.
    val coldError = when (segment) {
        ReservationSegment.Upcoming -> (upcoming as? UpcomingUiState.Error)?.type
        ReservationSegment.Past -> (past as? PastUiState.Error)?.type
    }
    var headerBottom by remember { mutableStateOf(0.dp) }

    Box(modifier = Modifier.fillMaxSize().recedeBehindSheet(open = cancelReceding)) {
        Atmosphere()
        coldError?.let {
            FullScreenError(type = it, onRetry = vm::retry, retrying = retrying, topInset = headerBottom)
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeContentPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Column(modifier = Modifier.chromeBottom { headerBottom = it }) {
            IconCircle(
                icon = ArcanaIcons.Close,
                diameter = 38,
                iconSize = 18,
                background = Surface,
                borderColor = Outline,
                contentColor = Ink,
                onClick = onClose,
                contentDescription = "Close reservations",
            )
            Spacer(Modifier.height(16.dp))
            Heading2("Reservations", size = 26, color = Wood)
            Spacer(Modifier.height(16.dp))
            SegmentedControl(
                labels = ReservationSegment.entries.map { it.name },
                selectedIndex = segment.ordinal,
                onSelect = { index ->
                    val target = ReservationSegment.entries[index]
                    if (target != segment) {
                        haptics.selection()
                        vm.selectSegment(target)
                    }
                },
            )
            }
            if (coldError == null) {
            Spacer(Modifier.height(8.dp))
            ArcanaPullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = vm::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when (segment) {
                    ReservationSegment.Upcoming -> UpcomingList(
                        state = upcoming,
                        onOpenClass = onOpenClass,
                        onCancel = vm::openCancel,
                        onBookClass = onBookClass,
                    )
                    ReservationSegment.Past -> PastList(
                        state = past,
                        openReviews = openReviews,
                        onReviewStarted = vm::reviewStarted,
                        onReviewFinished = vm::reviewFinished,
                        onReviewSaveFailed = reviewNotice::show,
                        onOpenClass = onOpenClass,
                        onLoadMore = vm::loadMore,
                        onRetryPage = vm::retryLoadMore,
                    )
                }
            }
            }
        }
        ReviewSaveNoticeHost(
            notice = reviewNotice,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeHorizontalPadding()
                .padding(bottom = 16.dp),
        )
        TransientSurface(
            visible = refreshFailed,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeHorizontalPadding()
                .padding(bottom = 16.dp),
        ) {
            ErrorSnackbar(
                text = ErrorCopy.REFRESH_FAILED,
                onRetry = {
                    vm.dismissRefreshFailed()
                    vm.refresh()
                },
                onDismiss = vm::dismissRefreshFailed,
            )
        }
    }

    cancelTarget?.let { b ->
        CancelReservationSheet(
            className = b.session.name,
            spotLabel = b.spot?.label ?: b.fulfilledSpot?.label ?: b.requestedSpot?.label,
            willForfeitCredit = b.cancelPolicy.willForfeitCredit,
            cancelState = cancelState,
            onConfirm = vm::confirmCancel,
            onDismiss = vm::dismissCancel,
            sheetState = cancelSheetState,
        )
    }
}

// ── Segments ──────────────────────────────────────────────────────────────────

@Composable
private fun UpcomingList(
    state: UpcomingUiState,
    onOpenClass: (Int) -> Unit,
    onCancel: (BookingDto) -> Unit,
    onBookClass: () -> Unit,
) {
    when (state) {
        UpcomingUiState.Loading -> SkeletonList()
        // Unreachable: the screen draws a cold failure full screen and does not
        // compose this list. Explicit so a new state still fails to compile.
        is UpcomingUiState.Error -> Unit
        is UpcomingUiState.Success -> {
            val today = remember { Clock.System.todayIn(ScheduleViewModel.ScheduleTimeZone) }
            val groups = remember(state.bookings) { groupReservationsByDay(state.bookings, today) }
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = listPadding()) {
                if (groups.isEmpty()) {
                    item {
                        EmptyState(title = EMPTY_UPCOMING_TITLE, body = EMPTY_UPCOMING_BODY) {
                            GhostCta(label = BOOK_A_CLASS, onClick = onBookClass)
                        }
                    }
                    return@LazyColumn
                }
                groups.forEach { group ->
                    item(key = "day-${group.label}") { DayHeader(group.label) }
                    items(group.bookings, key = { "b-${it.id}" }) { b ->
                        ReservationRow(
                            b = b,
                            onCancel = if (b.isLive) ({ onCancel(b) }) else null,
                            onClick = { onOpenClass(b.session.id) },
                        )
                    }
                }
                item(key = "book") {
                    Spacer(Modifier.height(24.dp))
                    GhostCta(label = BOOK_A_CLASS, onClick = onBookClass)
                }
            }
        }
    }
}

@Composable
private fun PastList(
    state: PastUiState,
    openReviews: Set<Int>,
    onReviewStarted: (BookingDto, ReviewDto) -> Unit,
    onReviewFinished: (Int) -> Unit,
    onReviewSaveFailed: (String) -> Unit,
    onOpenClass: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onRetryPage: () -> Unit,
) {
    when (state) {
        PastUiState.Loading -> SkeletonList()
        is PastUiState.Error -> Unit // unreachable, as in UpcomingList
        is PastUiState.Success -> {
            val listState = rememberLazyListState()
            val loadMore by rememberUpdatedState(onLoadMore)
            LaunchedEffect(listState) {
                snapshotFlow {
                    listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index to
                        listState.layoutInfo.totalItemsCount
                }
                    .distinctUntilChanged()
                    .collect { (lastVisible, totalCount) ->
                        if (lastVisible != null && lastVisible >= totalCount - LOAD_MORE_LOOKAHEAD) loadMore()
                    }
            }
            Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = listPadding()) {
                if (state.bookings.isEmpty()) {
                    item { EmptyState(title = EMPTY_PAST) }
                    return@LazyColumn
                }
                items(state.bookings, key = { "b-${it.id}" }) { b ->
                    ReservationRow(b = b, onCancel = null, onClick = { onOpenClass(b.session.id) })
                    // The same card as Home, inline (spec 5.4): it stays open until the
                    // member closes it; a reviewed combination says so and nothing else.
                    when {
                        b.canReview || b.id in openReviews -> ReviewCard(
                            bookingId = b.id,
                            surface = "past",
                            initialReview = null,
                            subjects = ReviewSubjects(b.session.instructor, b.session.name, b.session.studio),
                            style = ReviewCardStyle.Inline,
                            onStarted = { onReviewStarted(b, it) },
                            onSaveFailed = onReviewSaveFailed,
                            onDone = { onReviewFinished(b.id) },
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                        b.reviewed -> Caption(REVIEWED, size = 12, color = Moss, modifier = Modifier.padding(bottom = 8.dp))
                    }
                }
                if (state.loadingMore) {
                    item(key = "more") { SkeletonRow() }
                }
                state.pageError?.let { type ->
                    item(key = "page-error") {
                        InlineError(type = type, onRetry = onRetryPage, modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
            JumpToTop(listState)
            }
        }
    }
}

private val BookingDto.isLive: Boolean
    get() = status == "requested" || status == "confirmed"

// ── States ────────────────────────────────────────────────────────────────────

@Composable
private fun listPadding(): PaddingValues =
    PaddingValues(bottom = 24.dp + WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding())

/** Every state renders inside a LazyColumn so pull-to-refresh keeps working. */
@Composable
private fun SkeletonList() {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = listPadding()) {
        item { Spacer(Modifier.height(12.dp)) }
        repeat(4) { item { SkeletonRow() } }
    }
}

@Composable
private fun SkeletonRow() {
    ShimmerBox(
        modifier = Modifier
            .padding(vertical = 6.dp)
            .fillMaxWidth()
            .height(64.dp),
        shape = ArcanaShapes.Chip,
    )
}

@Composable
private fun EmptyState(
    title: String,
    body: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BodyText(title, size = 16, color = Ink)
        if (body != null) {
            Spacer(Modifier.height(4.dp))
            Caption(body, size = 13, color = Charcoal)
        }
        if (action != null) {
            Spacer(Modifier.height(24.dp))
            action()
        }
    }
}

// ── Rows ──────────────────────────────────────────────────────────────────────

@Composable
private fun DayHeader(label: String) {
    Row(
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Overline(text = label, size = 10, color = Moss)
        Box(Modifier.weight(1f).height(1.dp).background(MossLight))
    }
}

/** Formats an ISO-8601 start time as "Tue, Jun 2 · 5:00 PM", in the studio's
 *  wall clock — Kotlin/Native-safe. */
private fun formatBookingDateTime(startAt: String): String {
    return try {
        val local = wallClock(startAt)
        val dow = local.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.titlecase() }
        val mon = local.month.name.take(3).lowercase().replaceFirstChar { it.titlecase() }
        val day = local.date.day
        val h = if (local.hour % 12 == 0) 12 else local.hour % 12
        val m = local.minute.toString().padStart(2, '0')
        val ampm = if (local.hour < 12) "AM" else "PM"
        "$dow, $mon $day · $h:$m $ampm"
    } catch (_: Exception) {
        startAt.take(16).replace("T", " ")
    }
}

@Composable
private fun ReservationRow(b: BookingDto, onCancel: (() -> Unit)?, onClick: () -> Unit) {
    val dateTimeLabel = remember(b.session.startAt) { formatBookingDateTime(b.session.startAt) }
    val locationSuffix = b.session.location?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""
    val spotSuffix = b.spot?.let { " · ${it.label}" } ?: ""
    // Static spot *preference* (e.g. "Bag"), distinct from the real spot above.
    val preferenceSuffix = b.spotPreference?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""
    val studioSpot = "${b.session.studio}$locationSuffix$spotSuffix$preferenceSuffix"
    val instructorSuffix = b.session.instructor?.let { " · with $it" } ?: ""
    val rowSource = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The text block opens class detail; the trailing controls stay separate targets.
        Column(
            modifier = Modifier
                .weight(1f)
                .pressable(rowSource, pressedScale = 0.99f)
                .clickable(interactionSource = rowSource, indication = null, onClick = onClick),
        ) {
            BodyText(b.session.name, size = 16, color = Wood)
            Spacer(Modifier.height(2.dp))
            Caption(dateTimeLabel, size = 12, color = Charcoal)
            Spacer(Modifier.height(2.dp))
            Caption("$studioSpot$instructorSuffix", size = 12, color = Charcoal)
            b.session.locationAddress?.takeIf { it.isNotBlank() }?.let { address ->
                AddressLink(
                    name = b.session.location?.takeIf { it.isNotBlank() } ?: b.session.studio,
                    businessName = listOfNotNull(b.session.studio, b.session.location?.takeIf { it.isNotBlank() }).joinToString(" "),
                    address = address,
                    latitude = b.session.latitude,
                    longitude = b.session.longitude,
                    surface = "reservation_row",
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        StatusPill(b.status)
        if (onCancel != null) {
            Spacer(Modifier.width(8.dp))
            TextLink(label = "Cancel", onClick = onCancel, color = BurntNectar)
        }
    }
}
