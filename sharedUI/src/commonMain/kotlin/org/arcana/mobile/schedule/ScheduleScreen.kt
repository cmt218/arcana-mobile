package org.arcana.mobile.schedule

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.datetime.IllegalTimeZoneException
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.datetime.toLocalDateTime
import org.arcana.mobile.data.LocationBriefDto
import org.arcana.mobile.data.ScheduleSessionDto
import org.arcana.mobile.data.isNotOpenYet
import org.arcana.mobile.theme.Arcana
import org.arcana.mobile.theme.ArcanaShapes
import org.arcana.mobile.theme.Ash
import org.arcana.mobile.theme.Ash2
import org.arcana.mobile.theme.Atmosphere
import org.arcana.mobile.theme.Dur
import org.arcana.mobile.theme.Ease
import org.arcana.mobile.theme.Graphite
import org.arcana.mobile.theme.Ink
import org.arcana.mobile.theme.Lime
import org.arcana.mobile.theme.Mist
import org.arcana.mobile.theme.Moss
import org.arcana.mobile.theme.MossLight
import org.arcana.mobile.theme.Surface
import org.arcana.mobile.theme.Springs
import org.arcana.mobile.theme.Stone
import org.arcana.mobile.theme.Warning
import androidx.compose.ui.text.style.TextOverflow
import org.arcana.mobile.ui.ArcanaIcons
import org.arcana.mobile.ui.ArcanaPullToRefreshBox
import org.arcana.mobile.ui.ErrorCopy
import org.arcana.mobile.ui.ErrorSnackbar
import org.arcana.mobile.ui.LocalFloatingBarInset
import org.arcana.mobile.ui.BodyText
import org.arcana.mobile.ui.Caption
import org.arcana.mobile.ui.Display
import org.arcana.mobile.ui.DotMatrixLoader
import org.arcana.mobile.ui.DotMatrixLoaderCompact
import org.arcana.mobile.ui.FilterChip
import org.arcana.mobile.ui.FlowChipRow
import org.arcana.mobile.ui.FullScreenError
import org.arcana.mobile.ui.Overline
import org.arcana.mobile.ui.StatusPillFitted
import org.arcana.mobile.ui.StrokeIcon
import org.arcana.mobile.ui.StudioAccordionCard
import org.arcana.mobile.ui.StudioLocationRow
import org.arcana.mobile.ui.TransientSurface
import org.arcana.mobile.ui.cardShadow
import org.arcana.mobile.ui.controlShadow
import org.arcana.mobile.ui.opticallyCentredCaps
import org.arcana.mobile.ui.pressable
import org.arcana.mobile.ui.pressedShade
import org.arcana.mobile.ui.rememberHaptics
import org.arcana.mobile.ui.rememberPressed
import org.arcana.mobile.ui.safeContentPadding
import org.arcana.mobile.ui.safeHorizontalPadding
import org.arcana.mobile.ui.softShadow
import org.koin.compose.viewmodel.koinViewModel

// ── Constants -----------------------------------------------------------------

private val FALLBACK_STUDIO_COLOR = Moss

/** Sessions with <= 2 remaining spots are visually marked as "scarce". */
private const val SCARCE_THRESHOLD = 2

/** Fetch the next page once the user scrolls within this many items of the
 *  bottom — early enough that pages usually land before the footer loader
 *  is even visible. */
internal const val LOAD_MORE_LOOKAHEAD = 10

/** Fixed width of the Schedule row's left (time) column. Holds the HH:MM time
 *  and the width-filling booking-status pill, so every row's content starts at
 *  the same x whether or not it carries a REQUESTED / CONFIRMED pill. */
internal val SCHEDULE_TIME_COL_WIDTH = 64.dp

/** Absolute ceiling for the filter popover card, regardless of device. */
private val FILTER_PANEL_MAX = 340.dp

/** Slice of the schedule kept visible below the popover, so it reads as an
 *  overlay and clears Android's floating tab bar. */
private val PAGER_MIN_VISIBLE = 120.dp


// ── Display helpers -----------------------------------------------------------

internal fun titleCase(name: String): String =
    name.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

internal fun LocalDate.weekdayAbbr(): String = dayOfWeek.name.take(3)

/** "06:15" from a LocalTime — commonMain-safe (no String.format dependency). */
private fun LocalTime.hhmm(): String =
    "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

/** Parse `#RRGGBB` (server payload format) → Compose Color. Returns null on
 *  empty/invalid input so the caller can fall back. */
private fun parseHexColor(hex: String): Color? {
    if (hex.length != 7 || !hex.startsWith("#")) return null
    return try {
        val r = hex.substring(1, 3).toInt(16)
        val g = hex.substring(3, 5).toInt(16)
        val b = hex.substring(5, 7).toInt(16)
        Color(r, g, b)
    } catch (_: NumberFormatException) {
        null
    }
}

private fun studioColorFor(primaryColor: String): Color =
    parseHexColor(primaryColor) ?: FALLBACK_STUDIO_COLOR


// ── Capacity tier -------------------------------------------------------------



private fun ScheduleSessionDto.capacityTier(notOpen: Boolean = false): CapacityTier = computeCapacityTier(
    available = arcanaSpotsAvailable,
    offered = arcanaSpotsOffered,
    publishesCapacity = location.studio.publishesCapacity,
    notOpen = notOpen,
)

// ── Time-of-day grouping ------------------------------------------------------

internal enum class TimeBand(val label: String) {
    MORNING("MORNING"), AFTERNOON("AFTERNOON"), EVENING("EVENING")
}

internal fun LocalTime.timeBand(): TimeBand = when {
    hour < 12 -> TimeBand.MORNING
    hour < 17 -> TimeBand.AFTERNOON
    else -> TimeBand.EVENING
}

// ── Screen --------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    modifier: Modifier = Modifier,
    viewModel: ScheduleViewModel = koinViewModel(),
    onOpenClassDetail: (Int) -> Unit = {},
    onManageFavorites: () -> Unit = {},
    onOpenSearch: (Rect?) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val refreshing by viewModel.isRefreshing.collectAsState()
    val retrying by viewModel.retrying.collectAsState()
    val refreshFailed by viewModel.refreshFailed.collectAsState()

    // Re-fetch the "already booked" pills each time the Schedule returns to the
    // foreground — including popping back from ClassDetail after booking or
    // cancelling — so a just-cancelled pill clears without a manual refresh.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshBookings()
        onPauseOrDispose { }
    }

    // safeContentPadding sits on the CONTENT, not on this box — matching
    // HomeScreen. windowInsetsPadding consumes, so padding the shared container
    // would eat the status-bar inset before FullScreenError measured and centre
    // the error statusBar/2 lower than the identical error on Home.
    ArcanaPullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = viewModel::refresh,
        modifier = modifier
            .fillMaxSize(),
    ) {
        Atmosphere()
        when (val s = state) {
            is ScheduleUiState.Loading -> Box(Modifier.fillMaxSize().safeContentPadding()) {
                LoadingPlaceholder()
            }
            is ScheduleUiState.Error -> FullScreenError(
                type = s.type,
                onRetry = viewModel::reload,
                retrying = retrying,
            )
            is ScheduleUiState.Success -> Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().safeContentPadding()) {
                    SuccessContent(
                        state = s,
                        viewModel = viewModel,
                        onOpenClassDetail = onOpenClassDetail,
                        onManageFavorites = onManageFavorites,
                        onOpenSearch = onOpenSearch,
                    )
                }
                TransientSurface(
                    visible = refreshFailed,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .safeHorizontalPadding()
                        .padding(bottom = 16.dp + LocalFloatingBarInset.current),
                ) {
                    ErrorSnackbar(
                        text = ErrorCopy.REFRESH_FAILED,
                        onRetry = {
                            viewModel.dismissRefreshFailed()
                            viewModel.refresh()
                        },
                        onDismiss = viewModel::dismissRefreshFailed,
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingPlaceholder() {
    // Same anchor tz as the VM so the month header can't disagree with the
    // day rail that replaces it (device tz could differ near a month flip).
    val today = remember { Clock.System.todayIn(ScheduleViewModel.ScheduleTimeZone) }
    Column(modifier = Modifier.fillMaxSize()) {
        Display(
            text = "${titleCase(today.month.name)}.",
            size = 56, color = Ink,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp),
        )
        // Centered in the space below the header so the wave reads as the
        // screen's focal point while the schedule loads.
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            DotMatrixLoader()
        }
    }
}

/** Success-state render: a pinned header (title, rail, filters) above a
 *  [HorizontalPager] of per-day [DayPage]s, kept in sync with the
 *  ViewModel's selected day via [selectedIndex]. */
@Composable
private fun SuccessContent(
    state: ScheduleUiState.Success,
    viewModel: ScheduleViewModel,
    onOpenClassDetail: (Int) -> Unit,
    onManageFavorites: () -> Unit = {},
    onOpenSearch: (Rect?) -> Unit = {},
) {
    // Day selection lives in the ViewModel: it survives navigation via the
    // session-scoped store, and the debounced refetch pipeline needs it.
    val selectedDate = state.selectedDate
    // Live pill bounds (root px) — the Search reveal animates out of them.
    var searchPillBounds by remember { mutableStateOf<Rect?>(null) }
    // Session-scoped dismissal of the "choose favorites" nudge — survives
    // navigation away and back, resets on process restart. Fine for a nudge.
    var nudgeDismissed by rememberSaveable { mutableStateOf(false) }

    val haptics = rememberHaptics()
    // state.days can shrink/shift under a refetch, so indexOf can miss —
    // fall back to the first page rather than a negative index.
    val selectedIndex = state.days.indexOf(selectedDate).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = selectedIndex) { state.days.size }
    // favoritesKnown gates against a FAILED favorites fetch: never nudge a
    // member who may already have favorites we just couldn't confirm.
    val showNudge = state.favoritesKnown && !state.hasFavorites && !nudgeDismissed

    // Pager → ViewModel: a settled swipe selects the day. Keyed on pagerState
    // only: state.days can change (midnight roll) without settledPage changing,
    // and re-keying on it would replay the stale page index against the new list.
    val currentDays by rememberUpdatedState(state.days)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .drop(1)
            .collect { page ->
                val date = currentDays.getOrNull(page) ?: return@collect
                viewModel.selectDay(date, method = "swipe")
            }
    }
    // ViewModel → pager: a chip tap (or a restored selection) scrolls the pager.
    // MutatorMutex hands priority to a live drag/fling, so this never fights one.
    LaunchedEffect(selectedIndex) {
        if (pagerState.currentPage != selectedIndex) {
            pagerState.animateScrollToPage(selectedIndex, animationSpec = Springs.Settle)
        }
    }
    // A chip tap can sweep several pages; only a finger-owned scroll ticks.
    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()
    var fingerDriven by remember { mutableStateOf(false) }
    LaunchedEffect(pagerState) {
        snapshotFlow { isDragged }.collect { dragging -> if (dragging) fingerDriven = true }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }.collect { inProgress ->
            if (!inProgress) fingerDriven = false
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.drop(1).collect { if (fingerDriven) haptics.tick() }
    }

    Column(Modifier.fillMaxSize()) {
        // Track the *selected* day so the header flips when the user taps
        // a day in a different month (e.g. May 27 → June 1 on the rail).
        Row(
            modifier = Modifier.padding(start = 24.dp, end = 16.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Display(
                text = "${titleCase(selectedDate.month.name)}.",
                size = 56, color = Ink,
            )
            SearchEntryPill(
                onClick = { onOpenSearch(searchPillBounds) },
                onBounds = { searchPillBounds = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
                    // The month is all-caps display type: its ink centre
                    // sits ~0.0914em above the layout centre (see
                    // ui/OpticalCentering.kt), so a box centred on the
                    // layout reads low beside it. 56sp × 0.0914 ≈ 5dp.
                    .offset(y = (-5).dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        DayRail(
            days = state.days,
            position = { pagerState.currentPage + pagerState.currentPageOffsetFraction },
            selectedIndex = selectedIndex,
            onSelect = { i ->
                state.days.getOrNull(i)?.let { day ->
                    haptics.selection()
                    viewModel.selectDay(day)
                }
            },
        )
        Spacer(Modifier.height(16.dp))
        // Filter controls stay in the layout flow at a fixed height; the four
        // expandable panels render as an anchored popover in the stage below, so
        // opening one overlays the schedule instead of pushing it down.
        var expandedSection by rememberSaveable { mutableStateOf("") }
        val expandedSlugs = rememberSaveable(
            saver = listSaver(save = { it.toList() }, restore = { it.toMutableStateList() }),
        ) { mutableStateListOf<String>() }
        FilterControls(
            state = state,
            viewModel = viewModel,
            expandedSection = expandedSection,
            onExpandedSectionChange = { expandedSection = it },
        )
        // Pinned between the controls and the pager while a debounced filter
        // refetch is in flight — pairs with each page's own list dim.
        if (state.refreshingFilters) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                DotMatrixLoaderCompact()
            }
        }
        Spacer(Modifier.height(12.dp))
        // Stage: the day pager, with the filter popover overlaid on top of it and
        // anchored to its top edge (directly under the controls). The pager never
        // moves when a filter opens — the popover floats above it.
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
            // Cap the popover so it can't run under the floating tab bar and a
            // slice of the schedule always stays visible beneath it.
            val popoverMax = (maxHeight - PAGER_MIN_VISIBLE - LocalFloatingBarInset.current)
                .coerceIn(160.dp, FILTER_PANEL_MAX)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                key = { page -> state.days.getOrNull(page)?.toString() ?: "page-$page" },
            ) { page ->
                val date = state.days.getOrNull(page) ?: return@HorizontalPager
                val isCurrent = date == selectedDate
                DayPage(
                    date = date,
                    dayState = state.dayStates[date],
                    dayError = if (isCurrent) state.dayError else null,
                    dayRetrying = state.dayRetrying,
                    refreshingFilters = state.refreshingFilters,
                    bookedSessions = state.bookedSessions,
                    isCurrent = isCurrent,
                    onOpenClassDetail = onOpenClassDetail,
                    onRetry = viewModel::retryDay,
                    onLoadMore = viewModel::loadMore,
                    // Only pinned when there's a nudge to show; a collapsed
                    // (zero-height) header item would defeat the jump-to-top arrow's
                    // canScrollBackward check.
                    header = if (showNudge) {
                        {
                            FavoritesNudge(
                                visible = true,
                                onManageFavorites = onManageFavorites,
                                onDismiss = { nudgeDismissed = true },
                            )
                        }
                    } else {
                        null
                    },
                )
            }
            FilterPopoverOverlay(
                state = state,
                viewModel = viewModel,
                onManageFavorites = onManageFavorites,
                expandedSection = expandedSection,
                expandedSlugs = expandedSlugs,
                onExpandedSectionChange = { expandedSection = it },
                popoverMaxHeight = popoverMax,
            )
        }
    }
}

/** The "choose favorites" nudge: a one-tap path into the favorites manager for
 *  a member with none yet. Dismissal is hoisted so it applies across every
 *  day's page, not just the one it was tapped from. */
@Composable
private fun FavoritesNudge(visible: Boolean, onManageFavorites: () -> Unit, onDismiss: () -> Unit) {
    val cardShape = ArcanaShapes.Card
    TransientSurface(visible = visible, collapse = true) {
        Column {
            Row(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .cardShadow(cardShape)
                    .clip(cardShape)
                    .background(Surface)
                    .border(1.dp, Ash, cardShape)
                    // Keep both the top and bottom whitespace INSIDE the column
                    // (BodyText top pad + CTA bottom pad) so the column's vertical
                    // midpoint matches the card's — that's what keeps the dismiss
                    // X (centered against the row) reading as centered in the cell.
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    BodyText(
                        text = "Make it yours. Save your favorite Studios.",
                        size = 13, color = Ink,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    val chooseSource = remember { MutableInteractionSource() }
                    val choosePressed by rememberPressed(chooseSource)
                    val chooseAlpha = animateFloatAsState(
                        targetValue = if (choosePressed) 0.7f else 1f,
                        animationSpec = Springs.Snappy,
                        label = "chooseFavoritesAlpha",
                    )
                    // Padding inside the clickable so the CTA's hit area
                    // clears the 40dp minimum despite the 11sp label.
                    Overline(
                        text = "CHOOSE FAVORITES",
                        size = 11, color = Moss,
                        modifier = Modifier
                            .graphicsLayer { alpha = chooseAlpha.value }
                            .clickable(interactionSource = chooseSource, indication = null, onClick = onManageFavorites)
                            .padding(top = 12.dp, bottom = 12.dp, end = 12.dp),
                    )
                }
                // 40dp tap target around the 14dp glyph (house pattern —
                // see StudioAccordionCard's chevron).
                val dismissSource = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .pressable(dismissSource, pressedScale = 0.94f)
                        .softShadow(CircleShape)
                        .clip(CircleShape)
                        .background(Surface)
                        .border(1.dp, Ash, CircleShape)
                        .clickable(interactionSource = dismissSource, indication = null) { onDismiss() },
                    contentAlignment = Alignment.Center,
                ) {
                    StrokeIcon(
                        icon = ArcanaIcons.Close,
                        size = 16.dp,
                        tint = Ink,
                        contentDescription = "Dismiss",
                    )
                }
            }
            // Inside the collapsing column so dismissal closes this gap with the
            // card instead of leaving it behind for a frame.
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** The Book header's search entry: a compact oval hugging its icon + label,
 *  echoing the field it opens. The label hides when a long month name leaves
 *  too little room; an icon-only oval is the floor. Reports its root bounds
 *  so the Search screen's container-transform reveal starts exactly here. */
@Composable
private fun SearchEntryPill(
    onClick: () -> Unit,
    onBounds: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.CenterEnd) {
        val showLabel = maxWidth >= 120.dp
        val source = remember { MutableInteractionSource() }
        Row(
            modifier = Modifier
                .height(44.dp)
                .pressable(source, pressedScale = 0.96f)
                .softShadow(CircleShape)
                .clip(CircleShape)
                .background(Surface)
                .border(1.dp, Ash, CircleShape)
                .clickable(interactionSource = source, indication = null, onClick = onClick)
                // A graphicsLayer scale update schedules no layout pass, so this
                // stays accurate mid-press: onGloballyPositioned only re-fires on
                // an actual layout change, not a redraw.
                .onGloballyPositioned { onBounds(it.boundsInRoot()) }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StrokeIcon(
                icon = ArcanaIcons.Search,
                size = 20.dp,
                tint = Ink,
                contentDescription = "Search",
            )
            if (showLabel) {
                Spacer(Modifier.width(8.dp))
                BodyText(text = "Search", size = 14, color = Ash)
            }
        }
    }
}

/** End-of-list footer for a fully-loaded list: three dots (center lit) over a
 *  caption. The dot is the brand's repeating gesture — a centered triad reads
 *  as a deliberate full-stop. Shared with Search's end-of-results. */
@Composable
internal fun EndOfListMarker(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 28.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { i ->
                Box(
                    Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(if (i == 1) Lime else Mist)
                )
            }
        }
        Overline(text = text, size = 10, color = Ash)
    }
}

// ── Filter section (collapsed bar → expandable studio accordion) ─────────────

// Shared expand/collapse for every filter panel. Exit is the true reverse of
// enter — same Medium duration + Emphasized easing — so closing reads as smooth
// as the downward unroll rather than snapping shut.
private val filterPanelEnter =
    expandVertically(tween(Dur.Medium, easing = Ease.Emphasized), expandFrom = Alignment.Top) +
        fadeIn(tween(Dur.Short))
private val filterPanelExit =
    shrinkVertically(tween(Dur.Medium, easing = Ease.Emphasized), shrinkTowards = Alignment.Top) +
        fadeOut(tween(Dur.Short))

/** Wraps an expanded filter's content as the floating popover card: elevation,
 *  rounded on every corner (no pointer), a hairline, a capped height with its own
 *  scroll, and a 24.dp horizontal inset matching the controls' content width so
 *  the card is never wider than them (parent CLAUDE.md: width <= controls).
 *  [FilterPopoverOverlay] anchors it under the controls, over the schedule. */
@Composable
private fun FloatingFilterPanel(
    maxHeight: Dp,
    verticalArrangement: Arrangement.Vertical,
    contentHorizontalPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cardSource = remember { MutableInteractionSource() }
    val scroll = rememberScrollState()
    val scrollScope = rememberCoroutineScope()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, bottom = 12.dp)
            .cardShadow(ArcanaShapes.Card)
            .clip(ArcanaShapes.Card)
            .background(Surface)
            .border(1.dp, Mist, ArcanaShapes.Card)
            // Swallow taps in the card's own gaps so the outside-tap catcher
            // behind it doesn't close the popover.
            .clickable(interactionSource = cardSource, indication = null) {},
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = maxHeight)
                .verticalScroll(scroll)
                .padding(horizontal = contentHorizontalPadding, vertical = 14.dp),
            verticalArrangement = verticalArrangement,
            content = content,
        )
        // Jump-to-top / jump-to-bottom affordances, each fading in only when the
        // list can still scroll that way; a tap snaps to that end. Only the tall
        // panels (All Studios, Modalities) ever overflow enough to show them.
        ScrollJumpChevron(
            pointsDown = false,
            visible = scroll.canScrollBackward,
            onClick = { scrollScope.launch { scroll.animateScrollTo(0) } },
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
        )
        ScrollJumpChevron(
            pointsDown = true,
            visible = scroll.canScrollForward,
            onClick = { scrollScope.launch { scroll.animateScrollTo(scroll.maxValue) } },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
        )
    }
}

/** A round scroll affordance in the search-well treatment (Surface fill + Ash
 *  outline). Fades in only when [visible]; a tap jumps the list to that end.
 *  [pointsDown] = false renders an up-chevron (jump to top). Shared by the filter
 *  popovers and the Book day list. */
@Composable
internal fun ScrollJumpChevron(
    pointsDown: Boolean,
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(Dur.Short),
        label = "scrollJump",
    )
    if (alpha > 0f) {
        val source = remember { MutableInteractionSource() }
        Box(
            modifier = modifier
                .graphicsLayer { this.alpha = alpha }
                .size(26.dp)
                .pressable(source, pressedScale = 0.9f)
                .softShadow(CircleShape)
                .clip(CircleShape)
                .background(Surface)
                .border(1.dp, Ash, CircleShape)
                .clickable(interactionSource = source, indication = null, enabled = visible, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            StrokeIcon(
                icon = ArcanaIcons.ChevronDown,
                size = 14.dp,
                tint = Moss,
                modifier = Modifier.graphicsLayer { rotationZ = if (pointsDown) 0f else 180f },
                contentDescription = if (pointsDown) "Scroll to bottom" else "Scroll to top",
            )
        }
    }
}

/** The always-visible filter controls (in the layout flow): the scope toggle,
 *  the Time / Modalities buttons, and the active-filter chip rail. The panels
 *  they open are drawn separately by [FilterPopoverOverlay]; [expandedSection] is
 *  hoisted so the two agree on which one is open. */
@Composable
private fun FilterControls(
    state: ScheduleUiState.Success,
    viewModel: ScheduleViewModel,
    expandedSection: String,
    onExpandedSectionChange: (String) -> Unit,
) {
    val favoritesActive = state.scope == ScopeMode.Favorites
    val hasModalities = state.availableModalities.isNotEmpty()
    val modalityLabels = state.availableModalities.associate { it.slug to it.label }

    Column(modifier = Modifier.fillMaxWidth()) {
        // ── Tier 1: the studio/location SCOPE toggle (Favorites ⟷ All Studios).
        // Exactly one active. Tapping switches scope + opens its panel; tapping
        // the active one again toggles the panel.
        ScopeToggle(
            hasFavorites = state.hasFavorites,
            favoritesActive = favoritesActive,
            onFavorites = {
                if (!favoritesActive) {
                    // First tap just switches scope (no auto-expand — less jarring).
                    viewModel.useMyFavorites()
                    onExpandedSectionChange("")
                } else {
                    // Tapping the already-active scope toggles its panel.
                    onExpandedSectionChange(if (expandedSection == "fav") "" else "fav")
                }
            },
            onAllStudios = {
                if (favoritesActive) {
                    viewModel.showAllStudios()
                    onExpandedSectionChange("")
                } else {
                    onExpandedSectionChange(if (expandedSection == "all") "" else "all")
                }
            },
        )

        // ── Tier 2: the additive overlay filters (Time + Modalities), visually
        // separated from the scope toggle. Each opens a picker; active state =
        // "has a selection". Selections render as removable chips below.
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterPill(
                label = "TIME",
                active = state.timeFilter != null,
                modifier = Modifier.weight(1f),
                onClick = { onExpandedSectionChange(if (expandedSection == "time") "" else "time") },
            )
            if (hasModalities) {
                FilterPill(
                    label = "MODALITIES",
                    active = state.selectedModalitySlugs.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    onClick = { onExpandedSectionChange(if (expandedSection == "mod") "" else "mod") },
                )
            }
        }

        // ── Chip rail: the active overlay filters as removable bubbles.
        if (state.timeFilter != null || state.selectedModalitySlugs.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            FlowChipRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                state.timeFilter?.let { tf ->
                    FilterChip(label = tf.label, onRemove = { viewModel.clearTimeFilter() })
                }
                state.selectedModalitySlugs.forEach { slug ->
                    FilterChip(
                        label = modalityLabels[slug] ?: slug,
                        onRemove = { viewModel.removeModality(slug) },
                    )
                }
            }
        }
    }
}

/** The expanded filter panels, drawn as a popover anchored to the top of the
 *  schedule stage (directly under the controls) so opening one overlays the
 *  schedule instead of pushing it down. A transparent full-stage catcher closes
 *  the popover on an outside tap; the controls sit above the stage, so re-tapping
 *  the open control still toggles it shut. Exactly one panel shows at a time. */
@Composable
private fun BoxScope.FilterPopoverOverlay(
    state: ScheduleUiState.Success,
    viewModel: ScheduleViewModel,
    onManageFavorites: () -> Unit,
    expandedSection: String,
    expandedSlugs: MutableList<String>,
    onExpandedSectionChange: (String) -> Unit,
    popoverMaxHeight: Dp,
) {
    val hasModalities = state.availableModalities.isNotEmpty()

    // Outside-tap catcher over the schedule beneath the popover.
    val catcherSource = remember { MutableInteractionSource() }
    if (expandedSection != "") {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(interactionSource = catcherSource, indication = null) {
                    onExpandedSectionChange("")
                },
        )
    }

    // The cards, anchored at the stage's top edge. Only one is ever visible; each
    // keeps the shared downward-reveal enter and its smooth reverse exit.
    Box(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
        // Favorites panel — read-only, with a path into the Profile manager.
        AnimatedVisibility(
            visible = expandedSection == "fav" && state.hasFavorites && state.favoriteEntries.isNotEmpty(),
            enter = filterPanelEnter,
            exit = filterPanelExit,
        ) {
            // Only composed while the panel is open (or closing), so this
            // fires on every appearance and never while collapsed.
            LaunchedEffect(Unit) { viewModel.onFavoritesDropdownShown() }
            FloatingFilterPanel(maxHeight = popoverMaxHeight, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.favoriteEntries.forEach { entry ->
                    FavoriteEntryRow(name = entry.name, detail = entry.detail)
                }
                val manageSource = remember { MutableInteractionSource() }
                val managePressed by rememberPressed(manageSource)
                val manageAlpha = animateFloatAsState(
                    targetValue = if (managePressed) 0.7f else 1f,
                    animationSpec = Springs.Snappy,
                    label = "manageInProfileAlpha",
                )
                Overline(
                    text = "MANAGE IN PROFILE",
                    size = 11, color = Moss,
                    modifier = Modifier
                        .graphicsLayer { alpha = manageAlpha.value }
                        .clickable(interactionSource = manageSource, indication = null) {
                            viewModel.onManageFavoritesTapped()
                            onManageFavorites()
                        }
                        .padding(top = 4.dp, bottom = 8.dp, end = 12.dp),
                )
                FilterDoneButton(
                    onClick = { onExpandedSectionChange("") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // All-Studios panel — compact studio rows for narrowing to a subset.
        AnimatedVisibility(
            visible = expandedSection == "all",
            enter = filterPanelEnter,
            exit = filterPanelExit,
        ) {
            FloatingFilterPanel(maxHeight = popoverMaxHeight, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                state.filterStudios.forEach { studio ->
                    val chosen = studio.slug in state.filters.studioSlugs
                    val expanded = studio.slug in expandedSlugs
                    Column {
                        CompactStudioFilterRow(
                            name = studio.name,
                            locationCount = studio.locations.size,
                            chosen = chosen,
                            expanded = expanded,
                            selectedLocationCount = studio.locations.count { it.id in state.filters.locationIds },
                            onToggle = { viewModel.toggleStudioWhole(studio.slug) },
                            onToggleExpanded = {
                                if (studio.slug in expandedSlugs) expandedSlugs.remove(studio.slug)
                                else expandedSlugs.add(studio.slug)
                            },
                        )
                        if (expanded) {
                            Column(
                                modifier = Modifier.padding(start = 34.dp, top = 4.dp, bottom = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                studio.locations.forEach { location ->
                                    StudioLocationRow(
                                        label = location.label,
                                        checked = location.id in state.filters.locationIds || chosen,
                                        implied = chosen,
                                        onTap = { viewModel.toggleLocation(studio.slug, location.id) },
                                    )
                                }
                            }
                        }
                    }
                }
                FilterDoneButton(
                    onClick = { onExpandedSectionChange("") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }

        // Time picker — presets + a custom From/To range.
        AnimatedVisibility(
            visible = expandedSection == "time",
            enter = filterPanelEnter,
            exit = filterPanelExit,
        ) {
            FloatingFilterPanel(
                maxHeight = popoverMaxHeight,
                verticalArrangement = Arrangement.Top,
                contentHorizontalPadding = 16.dp,
            ) {
                TimeFilterPanel(
                    active = state.timeFilter,
                    onApply = { viewModel.setTimeFilter(it); onExpandedSectionChange("") },
                    onClear = { viewModel.clearTimeFilter() },
                    onDone = { onExpandedSectionChange("") },
                )
            }
        }

        // Modalities picker — a flat multi-select list; picks become chips.
        AnimatedVisibility(
            visible = expandedSection == "mod" && hasModalities,
            enter = filterPanelEnter,
            exit = filterPanelExit,
        ) {
            FloatingFilterPanel(maxHeight = popoverMaxHeight, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                state.availableModalities.forEach { option ->
                    StudioLocationRow(
                        label = option.label,
                        checked = option.slug in state.selectedModalitySlugs,
                        implied = false,
                        onTap = { viewModel.toggleModality(option.slug) },
                    )
                }
                FilterDoneButton(
                    onClick = { onExpandedSectionChange("") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }
    }
}

/** Compact single-line studio row for the All-Studios FILTER popover: a small
 *  square check, the studio name at the favorites-list size with an inline
 *  "· N locations", and a chevron that expands the location list below. This is
 *  the filter's own row — the tall [StudioAccordionCard] stays on the favorites
 *  StudioSelection screen and is deliberately not reused here. */
@Composable
private fun CompactStudioFilterRow(
    name: String,
    locationCount: Int,
    chosen: Boolean,
    expanded: Boolean,
    selectedLocationCount: Int,
    onToggle: () -> Unit,
    onToggleExpanded: () -> Unit,
) {
    val partial = !chosen && selectedLocationCount > 0
    val checkShape = RoundedCornerShape(6.dp)
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(Dur.Short, easing = Ease.Emphasized),
        label = "compactChevron",
    )
    val rowSource = remember { MutableInteractionSource() }
    val checkSource = remember { MutableInteractionSource() }
    val chevronSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(interactionSource = rowSource, indication = null, onClick = onToggleExpanded)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Square check — the compact filter's counterpart to the accordion's
        // round check. Labeled on the 40dp well, not the glyph (absent 2/3 of
        // the states), matching StudioAccordionCard.
        Box(
            modifier = Modifier
                .size(40.dp)
                .pressable(checkSource, pressedScale = 0.9f)
                .clip(CircleShape)
                .clickable(interactionSource = checkSource, indication = null, onClick = onToggle)
                .semantics { contentDescription = if (chosen) "Deselect $name" else "Select $name" },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(checkShape)
                    .then(
                        when {
                            chosen -> Modifier.background(Lime)
                            partial -> Modifier.border(2.dp, Lime, checkShape)
                            else -> Modifier.border(2.dp, Mist, checkShape)
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (chosen) {
                    // decorative — the well above carries the label.
                    StrokeIcon(ArcanaIcons.Check, size = 14.dp, tint = Ink)
                } else if (partial) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(Lime))
                }
            }
        }
        // Name + inline "· N locations" on one line; the name ellipsizes first.
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BodyText(
                text = name,
                size = 14,
                color = Ink,
                weight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            val locationsWord = if (locationCount == 1) "location" else "locations"
            Caption(
                text = if (partial) "  ·  $selectedLocationCount of $locationCount $locationsWord"
                       else "  ·  $locationCount $locationsWord",
                size = 12,
                color = if (partial) Moss else Ash,
            )
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .pressable(chevronSource, pressedScale = 0.9f)
                .clip(CircleShape)
                .clickable(interactionSource = chevronSource, indication = null, onClick = onToggleExpanded),
            contentAlignment = Alignment.Center,
        ) {
            StrokeIcon(
                icon = ArcanaIcons.ChevronDown,
                size = 16.dp,
                tint = Moss,
                modifier = Modifier.graphicsLayer { rotationZ = chevronRotation },
                contentDescription = if (expanded) "Hide $name locations" else "Show $name locations",
            )
        }
    }
}

/** The Favorites ⟷ All Studios scope toggle — a connected two-segment control
 *  (exactly one active). Favorites segment hidden when the member has none. */
@Composable
private fun ScopeToggle(
    hasFavorites: Boolean,
    favoritesActive: Boolean,
    onFavorites: () -> Unit,
    onAllStudios: () -> Unit,
) {
    // No favorites → no toggle, just the "All Studios" bar. Still tappable:
    // it opens/closes the studio accordion exactly like the toggle's segment
    // (scope is already AllStudios, so onAllStudios just flips the panel).
    if (!hasFavorites) {
        val source = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .pressable(source, pressedScale = 0.97f)
                .controlShadow(ArcanaShapes.Pill)
                .clip(CircleShape)
                .background(Moss)
                .clickable(interactionSource = source, indication = null, onClick = onAllStudios)
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) { ScopeLabel("ALL STUDIOS", onInk = true) }
        return
    }

    // Thumb-tracking toggle: a single Moss highlight slides under the finger as
    // you drag (Favorites at the left half, All Studios at the right), and
    // animates/commits to whichever side it lands on when you lift. Taps on
    // either label still switch (or expand the active panel).
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    // The gesture block below is keyed on Unit (never restarts), so read the
    // latest scope + callbacks through rememberUpdatedState — otherwise the drag
    // commits against a stale favoritesActive and only the first slide "sticks".
    val currentFavoritesActive by rememberUpdatedState(favoritesActive)
    val currentOnFavorites by rememberUpdatedState(onFavorites)
    val currentOnAllStudios by rememberUpdatedState(onAllStudios)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(CircleShape)
            .border(1.dp, Ash, CircleShape),
    ) {
        val halfPx = with(density) { maxWidth.toPx() } / 2f
        var dragging by remember { mutableStateOf(false) }
        // Highlight's left-edge position in px: 0 = Favorites, halfPx = All Studios.
        val offset = remember { Animatable(if (favoritesActive) 0f else halfPx) }
        // Follow external scope changes (favorites saved/cleared) when not dragging.
        LaunchedEffect(favoritesActive, halfPx) {
            if (!dragging) offset.animateTo(if (favoritesActive) 0f else halfPx, animationSpec = Springs.Settle)
        }
        val favHighlighted by remember(halfPx) {
            derivedStateOf { offset.value < halfPx / 2f }
        }

        // The sliding Moss highlight — drawn behind the labels, tracks the thumb.
        // Wrapped in a matchParentSize box so the pill fills the toggle's real
        // height (set by the labels) rather than the incoming constraints — which
        // may be unbounded here (scrolling parent), collapsing fillMaxHeight to 0.
        Box(modifier = Modifier.matchParentSize()) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(offset.value.roundToInt(), 0) }
                    .width(with(density) { halfPx.toDp() })
                    .fillMaxHeight()
                    // 2.dp inset (not 3): with the segment's 12.dp vpad this makes the
                    // Moss fill the same height + width as the Time/Modalities pills.
                    .padding(2.dp)
                    .controlShadow(ArcanaShapes.Pill)
                    .clip(CircleShape)
                    .background(Moss),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = true },
                        onHorizontalDrag = { change, delta ->
                            change.consume()
                            coroutineScope.launch {
                                offset.snapTo((offset.value + delta).coerceIn(0f, halfPx))
                            }
                        },
                        onDragEnd = {
                            dragging = false
                            val toFavorites = offset.value < halfPx / 2f
                            coroutineScope.launch {
                                offset.animateTo(if (toFavorites) 0f else halfPx, animationSpec = Springs.Settle)
                            }
                            // Commit only on an actual side change — a no-cross
                            // drag just springs back (and never toggles a panel).
                            if (toFavorites && !currentFavoritesActive) currentOnFavorites()
                            else if (!toFavorites && currentFavoritesActive) currentOnAllStudios()
                        },
                        onDragCancel = {
                            dragging = false
                            coroutineScope.launch {
                                offset.animateTo(if (currentFavoritesActive) 0f else halfPx, animationSpec = Springs.Settle)
                            }
                        },
                    )
                },
        ) {
            ScopeSegment("FAVORITES", onInk = favHighlighted, modifier = Modifier.weight(1f), onClick = onFavorites)
            ScopeSegment("ALL STUDIOS", onInk = !favHighlighted, modifier = Modifier.weight(1f), onClick = onAllStudios)
        }
    }
}

/** One tappable half of the scope toggle. The Ink highlight is drawn separately
 *  (it slides), so this stays transparent — [onInk] only flips the text color
 *  (Stone when the highlight is under it, else Ink). */
@Composable
private fun ScopeSegment(
    label: String,
    onInk: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            // 12.dp (vs the pills' 10.dp) so that, after the 2.dp thumb inset, the
            // Moss fill matches the Time/Modalities pill height exactly.
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        ScopeLabel(label, onInk)
    }
}

private val SCOPE_LABEL_SIZE = 12.sp
private const val SCOPE_LABEL_TRACKING_EM = 0.10f

@Composable
private fun ScopeLabel(label: String, onInk: Boolean) {
    Text(
        text = label,
        modifier = Modifier.opticallyCentredCaps(SCOPE_LABEL_SIZE, SCOPE_LABEL_TRACKING_EM),
        maxLines = 1, softWrap = false,
        style = TextStyle(
            fontFamily = Arcana.fonts.display,
            fontWeight = FontWeight.SemiBold,
            fontSize = SCOPE_LABEL_SIZE,
            letterSpacing = SCOPE_LABEL_TRACKING_EM.em,
            color = if (onInk) Stone else Ink,
        ),
    )
}

/** Time-of-day picker: quick presets (Morning/Afternoon/Evening) + a compact
 *  custom From–To range slider ([TimeRangeSlider], half-hour ticks, min 1h gap).
 *  Applying sets a single TimeFilter overlay. */
@Composable
private fun TimeFilterPanel(
    active: TimeFilter?,
    onApply: (TimeFilter) -> Unit,
    onClear: () -> Unit,
    onDone: () -> Unit,
) {
    val minMinute = TIME_SLIDER_MIN_MINUTE
    val maxMinute = TIME_SLIDER_MAX_MINUTE
    val minGap = TIME_SLIDER_MIN_GAP_MINUTES

    // Seed from the active custom range if any, else the full span. Snapping
    // matters for the presets, whose bounds are off-tick by design (11:59/16:59).
    fun seed(hhmm: String?, default: Int): Int =
        hhmmToMinutes(hhmm)?.let { snapMinutes(it) } ?: default

    var from by remember(active) { mutableStateOf(seed(active?.startGte, minMinute)) }
    var to by remember(active) { mutableStateOf(seed(active?.startLte, maxMinute)) }

    // Horizontal inset comes from the popover card (contentHorizontalPadding);
    // this column only owns its vertical rhythm.
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Overline(text = "QUICK", size = 11, color = Ash)
        FlowChipRow {
            TimePreset.entries.forEach { preset ->
                SelectablePill(
                    label = preset.label,
                    selected = active?.label == preset.label,
                    onClick = { onApply(preset.toFilter()) },
                )
            }
        }
        Overline(text = "CUSTOM RANGE", size = 11, color = Ash)
        BodyText(
            text = "${formatTime12h(minutesToHhmm(from))}  –  ${formatTime12h(minutesToHhmm(to))}",
            size = 14, color = Ink,
        )
        TimeRangeSlider(
            from = from, to = to,
            minValue = minMinute, maxValue = maxMinute, minGap = minGap,
            onChange = { f, t -> from = f; to = t },
        )
        FilterDoneButton(
            onClick = {
                // A full-span selection means "no custom time filter".
                if (from <= minMinute && to >= maxMinute) onDone()
                else onApply(customTimeFilter(minutesToHhmm(from), minutesToHhmm(to)))
            },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}

/** A compact dual-handle time range slider. Values are minutes-since-midnight;
 *  drags snap to the nearest [TIME_SLIDER_STEP_MINUTES] tick (half-hours).
 *  Enforces a [minGap] between the handles and grabs the NEAREST handle to the
 *  touch (so the end handle is easy to grab even at the extreme). 36dp tall. */
@Composable
private fun TimeRangeSlider(
    from: Int,
    to: Int,
    minValue: Int,
    maxValue: Int,
    minGap: Int,
    onChange: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val curFrom by rememberUpdatedState(from)
    val curTo by rememberUpdatedState(to)
    var active by remember { mutableStateOf(-1) }  // 0 = from handle, 1 = to handle
    val span = (maxValue - minValue).toFloat()

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(Unit) {
                val thumbR = 11.dp.toPx()
                fun usable() = (size.width - 2 * thumbR).coerceAtLeast(1f)
                fun valueToX(v: Int) = thumbR + (v - minValue) / span * usable()
                fun xToValue(x: Float): Int =
                    snapMinutes(
                        (minValue + ((x - thumbR) / usable() * span)).roundToInt(),
                        min = minValue,
                        max = maxValue,
                    )
                detectDragGestures(
                    onDragStart = { off ->
                        active = if (abs(off.x - valueToX(curFrom)) <= abs(off.x - valueToX(curTo))) 0 else 1
                    },
                    onDragEnd = { active = -1 },
                    onDragCancel = { active = -1 },
                    onDrag = { change, _ ->
                        change.consume()
                        val v = xToValue(change.position.x)
                        // curTo/curFrom and minGap are all step-aligned, so the
                        // clamped result lands on a tick too.
                        when (active) {
                            0 -> onChange(v.coerceAtMost(curTo - minGap), curTo)
                            1 -> onChange(curFrom, v.coerceAtLeast(curFrom + minGap))
                        }
                    },
                )
            },
    ) {
        val thumbR = 11.dp.toPx()
        val usable = (size.width - 2 * thumbR).coerceAtLeast(1f)
        fun valueToX(v: Int) = thumbR + (v - minValue) / span * usable
        val cy = size.height / 2
        val trackH = 4.dp.toPx()
        drawLine(Mist, Offset(thumbR, cy), Offset(size.width - thumbR, cy), strokeWidth = trackH, cap = StrokeCap.Round)
        drawLine(Moss, Offset(valueToX(from), cy), Offset(valueToX(to), cy), strokeWidth = trackH, cap = StrokeCap.Round)
        listOf(from, to).forEach { v ->
            drawCircle(Stone, radius = thumbR, center = Offset(valueToX(v), cy))
            drawCircle(Moss, radius = thumbR, center = Offset(valueToX(v), cy), style = Stroke(width = 3.dp.toPx()))
        }
    }
}

/** A small selectable pill for presets / hour options: Moss-filled when
 *  selected, hairline otherwise. */
@Composable
private fun SelectablePill(label: String, selected: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val fill by animateColorAsState(
        targetValue = if (selected) Moss else Surface,
        animationSpec = tween(Dur.Short),
        label = "presetPillFill",
    )
    val border by animateColorAsState(
        targetValue = if (selected) Moss else Ash,
        animationSpec = tween(Dur.Short),
        label = "presetPillBorder",
    )
    Box(
        modifier = Modifier
            .pressable(source, pressedScale = 0.97f)
            .then(if (selected) Modifier.controlShadow(ArcanaShapes.Pill) else Modifier.softShadow(ArcanaShapes.Pill))
            .clip(CircleShape)
            .background(fill)
            .border(1.dp, border, CircleShape)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        BodyText(text = label, size = 12, color = if (selected) Stone else Ink)
    }
}

/** One read-only favorited studio/location in the Favorites panel. */
@Composable
private fun FavoriteEntryRow(name: String, detail: String) {
    // A small Moss dot, not an icon — these rows are read-only, so we avoid the
    // tappable-looking flag Felicia flagged while still anchoring each line.
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(Moss),
        )
        BodyText(text = name, size = 14, color = Ink, modifier = Modifier.weight(1f))
        Caption(text = detail, size = 11, color = Ash2)
    }
}

/** The Time / Modalities overlay-filter buttons. Moss-filled when active, an Ash
 *  outline otherwise. Pass `Modifier.weight(1f)` to size two pills equally; the
 *  label centers. */
@Composable
private fun FilterPill(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val source = remember { MutableInteractionSource() }
    val fill by animateColorAsState(
        targetValue = if (active) Moss else Surface,
        animationSpec = tween(Dur.Short),
        label = "filterPillFill",
    )
    val border by animateColorAsState(
        targetValue = if (active) Moss else Ash,
        animationSpec = tween(Dur.Short),
        label = "filterPillBorder",
    )
    Row(
        modifier = modifier
            .pressable(source, pressedScale = 0.97f)
            .then(if (active) Modifier.controlShadow(ArcanaShapes.Pill) else Modifier.softShadow(ArcanaShapes.Pill))
            .clip(CircleShape)
            .background(fill)
            .border(1.dp, border, CircleShape)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.opticallyCentredCaps(FILTER_PILL_LABEL_SIZE, FILTER_PILL_LABEL_TRACKING_EM),
            maxLines = 1, softWrap = false,
            style = TextStyle(
                fontFamily = Arcana.fonts.display,
                fontWeight = FontWeight.SemiBold,
                fontSize = FILTER_PILL_LABEL_SIZE,
                letterSpacing = FILTER_PILL_LABEL_TRACKING_EM.em,
                color = if (active) Stone else Ink,
            ),
        )
    }
}

private val FILTER_PILL_LABEL_SIZE = 12.sp
private const val FILTER_PILL_LABEL_TRACKING_EM = 0.10f

/** Moss-filled "DONE" button that collapses an expanded filter section — the
 *  same effect as tapping the active pill again, but reachable from the bottom
 *  of a long favorites list / studio accordion without scrolling back up. */
@Composable
private fun FilterDoneButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    val pressed by rememberPressed(source)
    val fill by animateColorAsState(
        targetValue = if (pressed) Moss.pressedShade() else Moss,
        animationSpec = tween(Dur.Quick),
        label = "filterDoneFill",
    )
    Row(
        modifier = modifier
            .pressable(source, pressedScale = 0.97f)
            .controlShadow(ArcanaShapes.Pill)
            .clip(CircleShape)
            .background(fill)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "DONE",
            modifier = Modifier.opticallyCentredCaps(FILTER_PILL_LABEL_SIZE, FILTER_PILL_LABEL_TRACKING_EM),
            maxLines = 1, softWrap = false,
            style = TextStyle(
                fontFamily = Arcana.fonts.display,
                fontWeight = FontWeight.SemiBold,
                fontSize = FILTER_PILL_LABEL_SIZE,
                letterSpacing = FILTER_PILL_LABEL_TRACKING_EM.em,
                color = Stone,
            ),
        )
    }
}

// ── Class row -----------------------------------------------------------------

@Composable
internal fun ClassRow(
    session: ScheduleSessionDto,
    onClick: () -> Unit = {},
    /** Live booking status for this session (requested/confirmed/…), or null
     *  when the member holds no booking on it. Non-null ⇒ a status pill on the
     *  title line. */
    bookedStatus: String? = null,
    /** Samples the host page's shared elapsed-ms clock for the capacity bar's
     *  first-draw fill; null (Search's rows) draws the bar at its final fill. */
    barClock: (() -> Float)? = null,
    modifier: Modifier = Modifier,
) {
    // Display the class's local wall-clock — the session's own location
    // timezone, not the device's (see [sessionTimeZone]).
    val time = Instant.parse(session.startAt)
        .toLocalDateTime(sessionTimeZone(session.location.timezone))
        .time
    val studio = session.location.studio
    val sc = studioColorFor(studio.primaryColor)
    val available = session.arcanaSpotsAvailable
    val offered = session.arcanaSpotsOffered
    val isFull = available <= 0
    // A Mariana Tek class whose booking window hasn't opened yet. Takes
    // precedence over FULL (the server zeroes spots until it opens) — we render
    // "NOT OPEN", no progress bar, but keep the row viewable/tappable.
    val notOpen = isNotOpenYet(session.bookableAt, Clock.System.now())
    val tier = session.capacityTier(notOpen = notOpen)
    // Hidden-capacity studios (e.g. ID Hot Yoga) can't truthfully show a
    // fill progress bar — we don't know how many spots are booked. Suppress
    // the bar, the scarce shading, and any AlmostFull treatment for them;
    // the AVAILABLE / FULL overline carries the full signal. Not-open classes
    // also suppress the bar (no meaningful fill before the window opens).
    val showsCapacityVisuals = studio.publishesCapacity && !notOpen
    val isScarce = showsCapacityVisuals && !isFull && available <= SCARCE_THRESHOLD
    val fill = if (showsCapacityVisuals && offered > 0) {
        ((offered - available).toFloat() / offered).coerceIn(0f, 1f)
    } else 0f
    val instructorName = session.instructors.firstOrNull()?.name ?: ""
    val rowSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressable(rowSource, pressedScale = 0.99f)
            .clickable(interactionSource = rowSource, indication = null, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Time column. The booking-status pill (REQUESTED / CONFIRMED) sits
        // above the time: the left edge has spare vertical room, and keeping the
        // pill out of the title row lets a long class name use the row's full
        // width instead of being shoved into a wrap.
        Column(modifier = Modifier.width(SCHEDULE_TIME_COL_WIDTH)) {
            if (bookedStatus != null) {
                StatusPillFitted(bookedStatus)
                Spacer(Modifier.height(8.dp))
            }
            Text(
                text = time.hhmm(),
                maxLines = 1, softWrap = false,
                style = TextStyle(
                    fontFamily = Arcana.fonts.display,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    letterSpacing = (-0.01).em,
                    color = Ink,
                ),
            )
            Spacer(Modifier.height(4.dp))
            Overline(text = "${session.durationMinutes}min", size = 10, color = Ash)
        }
        // Studio color bar
        Box(
            Modifier
                .width(4.dp)
                .height(64.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (isFull) sc.copy(alpha = 0.35f) else sc)
        )
        // Class info
        Column(modifier = Modifier.weight(1f)) {
            // Meta line: BRAND · LOCATION    ·    INSTRUCTOR
            // Brand + location read as one studio-color unit; instructor is
            // neutral ash. Location takes flex (ellipsises first); instructor
            // also flexes so neither alone consumes the row. Brand stays
            // intrinsic so we never lose studio identity.
            MetaLine(
                brand = studio.name.uppercase(),
                location = session.location.shortLabel(),
                studioColor = sc,
            )
            Spacer(Modifier.height(4.dp))
            // Title line. Always a single line — a name too long to fit
            // ellipsizes rather than wrapping (mirrors the brand · location meta
            // line above). The booking-status pill lives above the time, so the
            // title gets the row's full width.
            BodyText(
                text = session.template.name,
                size = 16,
                // Full/not-open rows stay a touch softer than a bookable Ink title,
                // but Graphite (not Ash) keeps the name readable over the atmosphere.
                color = if (isFull) Graphite else Ink,
                weight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            // Instructor on its own row. Previously it shared the meta line with
            // brand · location and was the first to ellipsize when the location
            // ran long; a dedicated line guarantees it always reads in full.
            if (instructorName.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Overline(text = "WITH $instructorName", size = 10, color = Ash)
            }
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Fill bar is rendered only when the studio publishes capacity.
                // For hidden-capacity studios the bar would either always read
                // as empty (booked=0 by inference) or imply a precision we
                // don't actually have; the AVAILABLE / FULL overline alone
                // carries the signal.
                if (showsCapacityVisuals) {
                    Box(
                        Modifier
                            .width(56.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Mist),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                // Sampled from the page's shared clock, not a per-row
                                // Animatable, so a row's fill is a pure function of
                                // elapsed and never replays on scroll-back.
                                .graphicsLayer {
                                    val clock = barClock
                                    scaleX = when {
                                        fill <= 0f -> 0f
                                        clock == null -> fill
                                        else -> fill * Ease.Emphasized.transform(
                                            (clock.invoke() / Dur.Medium).coerceIn(0f, 1f),
                                        )
                                    }
                                    transformOrigin = TransformOrigin(0f, 0.5f)
                                }
                                .background(
                                    when {
                                        isScarce -> Warning
                                        isFull -> Ash2
                                        else -> MossLight
                                    }
                                )
                        )
                    }
                }
                Overline(
                    text = tier.label,
                    size = 10,
                    color = when (tier) {
                        CapacityTier.NotOpen -> Ash
                        CapacityTier.Full -> Ash
                        CapacityTier.AlmostFull -> Warning
                        CapacityTier.FillingUp -> MossLight
                        CapacityTier.Available -> Ash
                    },
                )
            }
        }
    }
}

// ── Row meta line -------------------------------------------------------------

/**
 * Single-line metadata stamp: `BRAND · LOCATION`.
 *
 * Visual subtleties from the design handoff:
 * - Brand is fully saturated studio color, weight 700.
 * - The dot between brand and location is the same color at 55% alpha — visually
 *   linking the two as one unit.
 * - Location is the same color at 78% alpha but weight 500 (slightly demoted).
 *
 * Overflow: brand is intrinsic and never truncates; location flexes with
 * ellipsis if a row is ever too narrow. The instructor is no longer part of
 * this stamp — it renders on its own line under the class title (see ClassRow).
 */
@Composable
private fun MetaLine(
    brand: String,
    location: String,
    studioColor: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // BRAND — intrinsic width, never truncated.
        Text(
            text = brand,
            maxLines = 1, softWrap = false,
            style = TextStyle(
                fontFamily = Arcana.fonts.body,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 0.20.em,
                color = studioColor,
            ),
        )
        if (location.isNotEmpty()) {
            // brand→location separator: same color, alpha 0.55
            Box(
                Modifier
                    .size(3.dp)
                    .clip(CircleShape)
                    .background(studioColor.copy(alpha = 0.55f)),
            )
            // LOCATION — flexes + ellipsises only if the row is too narrow.
            Text(
                text = location,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
                style = TextStyle(
                    fontFamily = Arcana.fonts.body,
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.sp,
                    letterSpacing = 0.20.em,
                    color = studioColor.copy(alpha = 0.78f),
                ),
            )
        }
    }
}
