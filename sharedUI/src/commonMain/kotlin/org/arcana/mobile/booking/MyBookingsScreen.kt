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
import org.arcana.mobile.networking.ErrorType
import org.arcana.mobile.schedule.LOAD_MORE_LOOKAHEAD
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
import org.arcana.mobile.ui.GhostCta
import org.arcana.mobile.ui.Heading2
import org.arcana.mobile.ui.IconCircle
import org.arcana.mobile.ui.InlineError
import org.arcana.mobile.ui.Overline
import org.arcana.mobile.ui.SegmentedControl
import org.arcana.mobile.ui.ShimmerBox
import org.arcana.mobile.ui.StatusPill
import org.arcana.mobile.ui.TextLink
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
    val haptics = rememberHaptics()

    BackHandler(enabled = cancelTarget == null) { onClose() }
    val cancelSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Off the sheet's target, not the VM value, so the page tracks the sheet down.
    val cancelReceding = cancelTarget != null && cancelSheetState.targetValue != SheetValue.Hidden

    Box(modifier = Modifier.fillMaxSize().recedeBehindSheet(open = cancelReceding)) {
        Atmosphere()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeContentPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            IconCircle(
                icon = ArcanaIcons.Close,
                diameter = 38,
                iconSize = 18,
                background = Surface,
                borderColor = Ash,
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
            Spacer(Modifier.height(8.dp))
            ArcanaPullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = vm::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when (segment) {
                    ReservationSegment.Upcoming -> UpcomingList(
                        state = upcoming,
                        retrying = retrying,
                        onRetry = vm::retry,
                        onOpenClass = onOpenClass,
                        onCancel = vm::openCancel,
                        onBookClass = onBookClass,
                    )
                    ReservationSegment.Past -> PastList(
                        state = past,
                        retrying = retrying,
                        onRetry = vm::retry,
                        onOpenClass = onOpenClass,
                        onLoadMore = vm::loadMore,
                        onRetryPage = vm::retryLoadMore,
                    )
                }
            }
        }
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
    retrying: Boolean,
    onRetry: () -> Unit,
    onOpenClass: (Int) -> Unit,
    onCancel: (BookingDto) -> Unit,
    onBookClass: () -> Unit,
) {
    when (state) {
        UpcomingUiState.Loading -> SkeletonList()
        is UpcomingUiState.Error -> ErrorList(state.type, retrying, onRetry)
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
    retrying: Boolean,
    onRetry: () -> Unit,
    onOpenClass: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onRetryPage: () -> Unit,
) {
    when (state) {
        PastUiState.Loading -> SkeletonList()
        is PastUiState.Error -> ErrorList(state.type, retrying, onRetry)
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
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = listPadding()) {
                if (state.bookings.isEmpty()) {
                    item { EmptyState(title = EMPTY_PAST) }
                    return@LazyColumn
                }
                items(state.bookings, key = { "b-${it.id}" }) { b ->
                    ReservationRow(b = b, onCancel = null, onClick = { onOpenClass(b.session.id) })
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
private fun ErrorList(type: ErrorType, retrying: Boolean, onRetry: () -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Box(modifier = Modifier.fillParentMaxSize()) {
                FullScreenError(type = type, onRetry = onRetry, retrying = retrying)
            }
        }
    }
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
            Caption(body, size = 13, color = Ash)
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
        Box(Modifier.weight(1f).height(1.dp).background(Mist))
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
            Caption(dateTimeLabel, size = 12, color = Ash)
            Spacer(Modifier.height(2.dp))
            Caption("$studioSpot$instructorSuffix", size = 12, color = Ash)
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
