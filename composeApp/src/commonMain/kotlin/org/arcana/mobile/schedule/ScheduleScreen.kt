package org.arcana.mobile.schedule

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
import org.arcana.mobile.theme.Ash
import org.arcana.mobile.theme.Ash2
import org.arcana.mobile.theme.Ink
import org.arcana.mobile.theme.Lime
import org.arcana.mobile.theme.Mist
import org.arcana.mobile.theme.Moss
import org.arcana.mobile.theme.MossLight
import org.arcana.mobile.theme.Paper
import org.arcana.mobile.theme.Stone
import org.arcana.mobile.theme.Warning
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import org.arcana.mobile.ui.ArcanaIcons
import org.arcana.mobile.ui.BodyText
import org.arcana.mobile.ui.Caption
import org.arcana.mobile.ui.Display
import org.arcana.mobile.ui.DotMatrixLoader
import org.arcana.mobile.ui.DotMatrixLoaderCompact
import org.arcana.mobile.ui.FilterChip
import org.arcana.mobile.ui.FlowChipRow
import org.arcana.mobile.ui.FullScreenError
import org.arcana.mobile.ui.Heading2
import org.arcana.mobile.ui.IconCircle
import org.arcana.mobile.ui.Overline
import org.arcana.mobile.ui.SectionRule
import org.arcana.mobile.ui.StatusPillFitted
import org.arcana.mobile.ui.StrokeIcon
import org.arcana.mobile.ui.StudioAccordionCard
import org.arcana.mobile.ui.StudioLocationRow
import org.arcana.mobile.ui.safeContentPadding
import org.koin.compose.viewmodel.koinViewModel

// ── Constants -----------------------------------------------------------------

private val FALLBACK_STUDIO_COLOR = Moss

/** Sessions with <= 2 remaining spots are visually marked as "scarce". */
private const val SCARCE_THRESHOLD = 2

/** Fetch the next page once the user scrolls within this many items of the
 *  bottom — early enough that pages usually land before the footer loader
 *  is even visible. */
private const val LOAD_MORE_LOOKAHEAD = 10

/** Minimum horizontal drag distance to flip days via a swipe. */
private val DAY_SWIPE_THRESHOLD = 56.dp

/** Fixed width of the Schedule row's left (time) column. Holds the HH:MM time
 *  and the width-filling booking-status pill, so every row's content starts at
 *  the same x whether or not it carries a REQUESTED / CONFIRMED pill. */
private val SCHEDULE_TIME_COL_WIDTH = 64.dp

/** Class-list fade on day change: start alpha + duration. */
private const val DAY_FADE_FROM = 0.4f
private const val DAY_FADE_MS = 200

/**
 * The day a horizontal swipe lands on, or null at the window's edges.
 * `forward` (a left swipe) advances one day; otherwise steps back one. Pure
 * so the bounds logic is unit-testable without the gesture plumbing.
 */
internal fun dayAfterSwipe(
    days: List<LocalDate>,
    selected: LocalDate,
    forward: Boolean,
): LocalDate? {
    val idx = days.indexOf(selected)
    if (idx < 0) return null
    return days.getOrNull(if (forward) idx + 1 else idx - 1)
}

// ── Display helpers -----------------------------------------------------------

private fun titleCase(name: String): String =
    name.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

private fun LocalDate.weekdayAbbr(): String = dayOfWeek.name.take(3)

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

/**
 * Resolve a session's location timezone for display: a class shows its own
 * local wall-clock (a 6 PM Williamsburg class reads "18:00" wherever the
 * device is). Server timezone strings are IANA ids, but `TimeZone.of` throws
 * on an unknown id — fall back to the schedule's anchor timezone rather than
 * crash the screen on a bad row. Top-level + internal so it's unit-testable
 * from commonTest.
 */
internal fun sessionTimeZone(id: String): TimeZone = try {
    TimeZone.of(id)
} catch (_: IllegalTimeZoneException) {
    ScheduleViewModel.ScheduleTimeZone
}

// ── Capacity tier -------------------------------------------------------------

internal enum class CapacityTier(val label: String) {
    // The class's Mariana Tek booking window hasn't opened yet — distinct from
    // FULL (the server reports 0 spots until the window opens, but it isn't full).
    NotOpen("NOT OPEN"),
    Full("FULL"),
    AlmostFull("ALMOST FULL"),
    FillingUp("FILLING UP"),
    Available("AVAILABLE"),
}

/**
 * Pure helper for the Schedule row overline + Detail availability block.
 * Extracted as a top-level function so the logic is unit-testable in
 * `commonTest` without spinning up a full `ScheduleSessionDto` graph.
 *
 * When `publishesCapacity` is false the studio doesn't expose a real
 * capacity number (e.g. ID Hot Yoga on Mindbody). We collapse to binary
 * AVAILABLE / FULL — we can't show "FILLING UP" or "ALMOST FULL"
 * truthfully because we don't know what fraction is booked.
 */
internal fun computeCapacityTier(
    available: Int,
    offered: Int,
    publishesCapacity: Boolean,
    notOpen: Boolean = false,
): CapacityTier {
    // A not-open booking window wins over everything: the server zeroes spots
    // until it opens, so without this the row would mislabel as FULL.
    if (notOpen) return CapacityTier.NotOpen
    if (!publishesCapacity) {
        return if (available <= 0) CapacityTier.Full else CapacityTier.Available
    }
    return when {
        // <= 0 mirrors the defensive guard on `isFull` in ClassRow so over-
        // booked sessions (negative available) consistently render as Full
        // across label, color, and CTA.
        available <= 0 -> CapacityTier.Full
        available <= 2 -> CapacityTier.AlmostFull
        offered > 0 && available.toFloat() / offered <= 0.4f -> CapacityTier.FillingUp
        else -> CapacityTier.Available
    }
}

private fun ScheduleSessionDto.capacityTier(notOpen: Boolean = false): CapacityTier = computeCapacityTier(
    available = arcanaSpotsAvailable,
    offered = arcanaSpotsOffered,
    publishesCapacity = location.studio.publishesCapacity,
    notOpen = notOpen,
)

// ── Time-of-day grouping ------------------------------------------------------

private enum class TimeBand(val label: String) {
    MORNING("MORNING"), AFTERNOON("AFTERNOON"), EVENING("EVENING")
}

private fun LocalTime.timeBand(): TimeBand = when {
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
) {
    val state by viewModel.uiState.collectAsState()
    val refreshing by viewModel.isRefreshing.collectAsState()

    // Re-fetch the "already booked" pills each time the Schedule returns to the
    // foreground — including popping back from ClassDetail after booking or
    // cancelling — so a just-cancelled pill clears without a manual refresh.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshBookings()
        onPauseOrDispose { }
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = viewModel::refresh,
        modifier = modifier
            .fillMaxSize()
            .background(Stone)
            .safeContentPadding(),
    ) {
        when (val s = state) {
            is ScheduleUiState.Loading -> LoadingPlaceholder()
            is ScheduleUiState.Error -> FullScreenError(type = s.type, onRetry = viewModel::reload)
            is ScheduleUiState.Success -> SuccessContent(
                state = s,
                viewModel = viewModel,
                onOpenClassDetail = onOpenClassDetail,
                onManageFavorites = onManageFavorites,
            )
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

/**
 * Success-state render: single LazyColumn so off-screen rows aren't composed
 * (only the visible window pays compose cost; rows recycle on scroll).
 * Class rows are keyed on `session.id` so that switching the selected day
 * doesn't churn nodes when sessions overlap between days (rare but cheap).
 *
 * Horizontal scrollers (day rail, filter chips) live inside `item {}` blocks
 * — that's idiomatic Compose and avoids the nested-scroll conflict you'd hit
 * with a `LazyColumn` inside a `verticalScroll` `Column`.
 */
@Composable
private fun SuccessContent(
    state: ScheduleUiState.Success,
    viewModel: ScheduleViewModel,
    onOpenClassDetail: (Int) -> Unit,
    onManageFavorites: () -> Unit = {},
) {
    // Day selection lives in the ViewModel: it survives navigation via the
    // session-scoped store, and the debounced refetch pipeline needs it.
    val selectedDate = state.selectedDate
    // Session-scoped dismissal of the "choose favorites" nudge — survives
    // navigation away and back, resets on process restart. Fine for a nudge.
    var nudgeDismissed by rememberSaveable { mutableStateOf(false) }

    val dayState = state.dayStates[selectedDate]
    val dayLoaded = dayState?.loaded == true
    val sessionsForSelected = dayState?.sessions.orEmpty()
    // Recompute the time-of-day bucketing only when the selected day's
    // session list actually changes (otherwise every recomposition reparses).
    // Bucketing uses each session's own location timezone — see
    // [sessionTimeZone].
    val byBand: Map<TimeBand, List<ScheduleSessionDto>> = remember(sessionsForSelected) {
        sessionsForSelected.groupBy {
            Instant.parse(it.startAt)
                .toLocalDateTime(sessionTimeZone(it.location.timezone))
                .time.timeBand()
        }
    }
    val activeBands = remember(byBand) {
        TimeBand.values().filter { byBand[it]?.isNotEmpty() == true }
    }

    // A quick fade-in of the class list whenever the day changes (swipe or
    // chip tap) — a lightweight cue that the content swapped. Applied to the
    // list items only, so the rail/chips never flicker.
    val dayFade = remember { Animatable(1f) }
    LaunchedEffect(state.selectedDate) {
        dayFade.snapTo(DAY_FADE_FROM)
        dayFade.animateTo(1f, tween(durationMillis = DAY_FADE_MS))
    }

    // Stale-while-refetch: from a chip tap until the debounced refetch settles
    // the class-list portion dims (rail/chips stay full-opacity and
    // interactive) and a compact loader pins between the chips and the list.
    // Composed with the day-change fade so both effects coexist on the list.
    val listAlpha = (if (state.refreshingFilters) 0.6f else 1f) * dayFade.value

    // Load-more trigger: when the user scrolls within LOAD_MORE_LOOKAHEAD
    // items of the bottom, ask for the next page. The VM fully guards
    // loadMore() (loaded page 1, non-null cursor, none in flight), so
    // over-calling from here is safe. Keyed on selectedDate so a day switch
    // restarts the collector against the new list shape.
    val listState = rememberLazyListState()
    LaunchedEffect(listState, state.selectedDate) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index to
                listState.layoutInfo.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (lastVisible, totalCount) ->
                if (lastVisible != null && lastVisible >= totalCount - LOAD_MORE_LOOKAHEAD) {
                    viewModel.loadMore()
                }
            }
    }

    // Side-to-side swipe over the list body navigates days. The horizontal
    // rails (day chips, filter chips) are deeper in the tree and consume their
    // own horizontal drags first; vertical drags go to the LazyColumn — so this
    // only fires on a clear horizontal swipe over the class list. Keyed on the
    // selected day so the closure always sees the current position.
    val daySwipe = Modifier.pointerInput(state.days, state.selectedDate) {
        val thresholdPx = DAY_SWIPE_THRESHOLD.toPx()
        var accumulated = 0f
        detectHorizontalDragGestures(
            onDragStart = { accumulated = 0f },
            onDragCancel = { accumulated = 0f },
            onDragEnd = {
                val forward = accumulated <= -thresholdPx
                val backward = accumulated >= thresholdPx
                if (forward || backward) {
                    dayAfterSwipe(state.days, state.selectedDate, forward)
                        ?.let { viewModel.selectDay(it, method = "swipe") }
                }
            },
        ) { _, dragAmount -> accumulated += dragAmount }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().then(daySwipe),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item("title") {
            // Track the *selected* day so the header flips when the user taps
            // a day in a different month (e.g. May 27 → June 1 on the rail).
            Display(
                text = "${titleCase(selectedDate.month.name)}.",
                size = 56, color = Ink,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp),
            )
        }

        item("day-rail") {
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.days.forEachIndexed { i, date ->
                    DayChip(
                        date = date,
                        label = if (i == 0) "TODAY" else "",
                        active = date == selectedDate,
                        onClick = { viewModel.selectDay(date) },
                    )
                }
            }
        }

        // Collapsed-by-default filter section: a summary bar that expands the
        // studio accordion in place (replaces the old two-tier chip rails).
        item("filter-section") {
            Spacer(Modifier.height(16.dp))
            ScheduleFilterSection(state = state, viewModel = viewModel, onManageFavorites = onManageFavorites)
        }

        // Nudge banner: members with no favorites yet get a one-tap path into
        // the favorites manager. Dismissable per-session via the close glyph.
        // `favoritesKnown` keeps it hidden when the favorites fetch failed —
        // never nudge a member who may already have favorites.
        if (state.favoritesKnown && !state.hasFavorites && !nudgeDismissed) {
            item("favorites-nudge") {
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Paper)
                        .border(1.dp, Mist, RoundedCornerShape(16.dp))
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
                        // Padding inside the clickable so the CTA's hit area
                        // clears the 40dp minimum despite the 11sp label.
                        Overline(
                            text = "CHOOSE FAVORITES",
                            size = 11, color = Moss,
                            modifier = Modifier
                                .clickable(onClick = onManageFavorites)
                                .padding(top = 12.dp, bottom = 12.dp, end = 12.dp),
                        )
                    }
                    // 40dp tap target around the 14dp glyph (house pattern —
                    // see StudioAccordionCard's chevron).
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable { nudgeDismissed = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        StrokeIcon(icon = ArcanaIcons.Close, size = 14.dp, tint = Ash2)
                    }
                }
            }
        }

        // Pinned between the chips and the list while a debounced filter
        // refetch is in flight — pairs with the list dim below.
        if (state.refreshingFilters) {
            item("refreshing-filters") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    DotMatrixLoaderCompact()
                }
            }
        }

        item("filter-trailing-space") { Spacer(Modifier.height(16.dp)) }

        if (!dayLoaded) {
            // Page 1 of this day hasn't landed under the current filter set —
            // loader in the list area only (header/rail/banner/chips stay).
            item("day-loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 64.dp)
                        .alpha(listAlpha),
                    contentAlignment = Alignment.Center,
                ) {
                    DotMatrixLoader()
                }
            }
        } else if (sessionsForSelected.isEmpty()) {
            item("empty") {
                Column(
                    modifier = Modifier
                        .padding(start = 24.dp, end = 24.dp, top = 16.dp)
                        .alpha(listAlpha),
                ) {
                    BodyText(
                        text = "No classes match your filters for this day.",
                        size = 14, color = Ash,
                    )
                }
            }
        } else {
            activeBands.forEachIndexed { bandIdx, band ->
                item("band-header-$band") {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .alpha(listAlpha),
                    ) {
                        if (bandIdx > 0) Spacer(Modifier.height(24.dp))
                        SectionRule(label = band.label)
                        Spacer(Modifier.height(8.dp))
                    }
                }
                items(
                    items = byBand[band].orEmpty(),
                    key = { session -> "row-${session.id}" },
                ) { session ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .alpha(listAlpha),
                    ) {
                        ClassRow(
                            session,
                            onClick = { onOpenClassDetail(session.id) },
                            bookedStatus = state.bookedSessions[session.id],
                        )
                    }
                }
            }
            // Footer loader — present while more pages exist for this day.
            // The scroll trigger above usually fetches before this scrolls
            // into view, so it mostly reads as "the page is arriving".
            if (dayState.nextCursor != null) {
                item("load-more") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                            .alpha(listAlpha),
                        contentAlignment = Alignment.Center,
                    ) {
                        DotMatrixLoaderCompact()
                    }
                }
            } else {
                // The full day is loaded — a quiet brand full-stop so the
                // bottom of the list reads as "that's all", not "still loading".
                item("end-of-day") {
                    EndOfDayMarker(
                        weekday = titleCase(selectedDate.dayOfWeek.name),
                        modifier = Modifier.alpha(listAlpha),
                    )
                }
            }
        }
    }
}

/** End-of-list footer for a fully-loaded day: three dots (center lit) over a
 *  caption. The dot is the brand's repeating gesture — a centered triad reads
 *  as a deliberate full-stop. */
@Composable
private fun EndOfDayMarker(weekday: String, modifier: Modifier = Modifier) {
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
        Overline(text = "That's everything for $weekday", size = 10, color = Ash)
    }
}

// ── Day chip ------------------------------------------------------------------

@Composable
private fun DayChip(
    date: LocalDate,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .size(width = 56.dp, height = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) Moss else Paper)
            .border(1.dp, if (active) Moss else Mist, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(top = 8.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label.ifEmpty { date.weekdayAbbr() },
            maxLines = 1, softWrap = false,
            style = TextStyle(
                fontFamily = Arcana.fonts.body,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 0.20.em,
                color = if (active) Lime else Ash,
            ),
        )
        Text(
            text = date.day.toString(),
            maxLines = 1, softWrap = false,
            style = TextStyle(
                fontFamily = Arcana.fonts.display,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                letterSpacing = (-0.02).em,
                color = if (active) Stone else Ink,
            ),
        )
    }
}

// ── Filter section (collapsed bar → expandable studio accordion) ─────────────

@Composable
private fun ScheduleFilterSection(
    state: ScheduleUiState.Success,
    viewModel: ScheduleViewModel,
    onManageFavorites: () -> Unit,
) {
    // Which section is expanded below the bars: "" none, "fav" favorites list,
    // "all" studio accordion, "time" time picker, "mod" modality list.
    var expandedSection by rememberSaveable { mutableStateOf("") }
    val expandedSlugs = rememberSaveable(
        saver = listSaver(save = { it.toList() }, restore = { it.toMutableStateList() }),
    ) { mutableStateListOf<String>() }

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
                    expandedSection = ""
                } else {
                    // Tapping the already-active scope toggles its panel.
                    expandedSection = if (expandedSection == "fav") "" else "fav"
                }
            },
            onAllStudios = {
                if (favoritesActive) {
                    viewModel.showAllStudios()
                    expandedSection = ""
                } else {
                    expandedSection = if (expandedSection == "all") "" else "all"
                }
            },
        )

        // Favorites panel — read-only, with a path into the Profile manager.
        if (expandedSection == "fav" && state.hasFavorites && state.favoriteEntries.isNotEmpty()) {
            LaunchedEffect(Unit) { viewModel.onFavoritesDropdownShown() }
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                state.favoriteEntries.forEach { entry ->
                    FavoriteEntryRow(name = entry.name, detail = entry.detail)
                }
                Overline(
                    text = "MANAGE IN PROFILE",
                    size = 11, color = Moss,
                    modifier = Modifier
                        .clickable {
                            viewModel.onManageFavoritesTapped()
                            onManageFavorites()
                        }
                        .padding(top = 4.dp, bottom = 8.dp, end = 12.dp),
                )
                FilterDoneButton(
                    onClick = { expandedSection = "" },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // All-Studios panel — the studio accordion for narrowing to a subset.
        if (expandedSection == "all") {
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.filterStudios.forEach { studio ->
                    val chosen = studio.slug in state.filters.studioSlugs
                    val expanded = studio.slug in expandedSlugs
                    Column {
                        StudioAccordionCard(
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
                                modifier = Modifier.padding(start = 32.dp, top = 12.dp, bottom = 8.dp),
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
                    onClick = { expandedSection = "" },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }

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
                onClick = { expandedSection = if (expandedSection == "time") "" else "time" },
            )
            if (hasModalities) {
                FilterPill(
                    label = "MODALITIES",
                    active = state.selectedModalitySlugs.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    onClick = { expandedSection = if (expandedSection == "mod") "" else "mod" },
                )
            }
        }

        // Time picker — presets + a custom From/To range.
        if (expandedSection == "time") {
            Spacer(Modifier.height(12.dp))
            TimeFilterPanel(
                active = state.timeFilter,
                onApply = { viewModel.setTimeFilter(it); expandedSection = "" },
                onClear = { viewModel.clearTimeFilter() },
                onDone = { expandedSection = "" },
            )
        }

        // Modalities picker — a flat multi-select list; picks become chips.
        if (expandedSection == "mod" && hasModalities) {
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                state.availableModalities.forEach { option ->
                    StudioLocationRow(
                        label = option.label,
                        checked = option.slug in state.selectedModalitySlugs,
                        implied = false,
                        onTap = { viewModel.toggleModality(option.slug) },
                    )
                }
                FilterDoneButton(
                    onClick = { expandedSection = "" },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clip(CircleShape)
                .clickable(onClick = onAllStudios)
                .background(Ink)
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) { ScopeLabel("ALL STUDIOS", onInk = true) }
        return
    }

    // Thumb-tracking toggle: a single Ink highlight slides under the finger as
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
            .border(1.dp, Mist, CircleShape),
    ) {
        val halfPx = with(density) { maxWidth.toPx() } / 2f
        var dragging by remember { mutableStateOf(false) }
        // Highlight's left-edge position in px: 0 = Favorites, halfPx = All Studios.
        val offset = remember { Animatable(if (favoritesActive) 0f else halfPx) }
        // Follow external scope changes (favorites saved/cleared) when not dragging.
        LaunchedEffect(favoritesActive, halfPx) {
            if (!dragging) offset.animateTo(if (favoritesActive) 0f else halfPx)
        }
        val favHighlighted by remember(halfPx) {
            derivedStateOf { offset.value < halfPx / 2f }
        }

        // The sliding Ink highlight — drawn behind the labels, tracks the thumb.
        // Wrapped in a matchParentSize box so the pill fills the toggle's real
        // height (set by the labels) rather than the incoming constraints — which
        // may be unbounded here (scrolling parent), collapsing fillMaxHeight to 0.
        Box(modifier = Modifier.matchParentSize()) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(offset.value.roundToInt(), 0) }
                    .width(with(density) { halfPx.toDp() })
                    .fillMaxHeight()
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(Ink),
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
                                offset.animateTo(if (toFavorites) 0f else halfPx)
                            }
                            // Commit only on an actual side change — a no-cross
                            // drag just springs back (and never toggles a panel).
                            if (toFavorites && !currentFavoritesActive) currentOnFavorites()
                            else if (!toFavorites && currentFavoritesActive) currentOnAllStudios()
                        },
                        onDragCancel = {
                            dragging = false
                            coroutineScope.launch {
                                offset.animateTo(if (currentFavoritesActive) 0f else halfPx)
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
    Box(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        ScopeLabel(label, onInk)
    }
}

@Composable
private fun ScopeLabel(label: String, onInk: Boolean) {
    Text(
        text = label,
        modifier = Modifier.offset(y = 1.dp),
        maxLines = 1, softWrap = false,
        style = TextStyle(
            fontFamily = Arcana.fonts.display,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            lineHeight = 12.sp,
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both,
            ),
            letterSpacing = 0.10.em,
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

    Column(
        modifier = Modifier.padding(horizontal = 24.dp),
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
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) Moss else Color.Transparent)
            .border(1.dp, if (selected) Moss else Mist, CircleShape)
            .clickable(onClick = onClick)
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

/** Pill toggle for the schedule filter (Favorites / All Studios). Ink-filled
 *  when active, hairline otherwise. Pass `Modifier.weight(1f)` to size two pills
 *  equally; the label centers. */
@Composable
private fun FilterPill(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(if (active) Ink else Color.Transparent)
            .border(1.dp, if (active) Ink else Mist, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            // Nudge down ~9% of the font size — the line-height trim centers the
            // box, but League Spartan caps still ride a touch high (mirrors the
            // CircleMonogram recipe).
            modifier = Modifier.offset(y = 1.dp),
            maxLines = 1, softWrap = false,
            style = TextStyle(
                fontFamily = Arcana.fonts.display,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                // Trim + center the line box so the all-caps League Spartan
                // glyphs sit vertically centered in the pill (they otherwise
                // ride high). Same fix used by CircleMonogram / the tab bar.
                lineHeight = 12.sp,
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both,
                ),
                letterSpacing = 0.10.em,
                color = if (active) Stone else Ink,
            ),
        )
    }
}

/** Moss-filled "DONE" button that collapses an expanded filter section — the
 *  same effect as tapping the active pill again, but reachable from the bottom
 *  of a long favorites list / studio accordion without scrolling back up. */
@Composable
private fun FilterDoneButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Moss)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "DONE",
            // Same vertical-centering recipe as FilterPill (League Spartan caps
            // ride high without the line-height trim + 1dp nudge).
            modifier = Modifier.offset(y = 1.dp),
            maxLines = 1, softWrap = false,
            style = TextStyle(
                fontFamily = Arcana.fonts.display,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                lineHeight = 12.sp,
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both,
                ),
                letterSpacing = 0.10.em,
                color = Stone,
            ),
        )
    }
}

// ── Class row -----------------------------------------------------------------

@Composable
private fun ClassRow(
    session: ScheduleSessionDto,
    onClick: () -> Unit = {},
    /** Live booking status for this session (requested/confirmed/…), or null
     *  when the member holds no booking on it. Non-null ⇒ a status pill on the
     *  title line. */
    bookedStatus: String? = null,
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
                color = if (isFull) Ash else Ink,
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
                                .fillMaxWidth(fill)
                                .height(4.dp)
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
                        CapacityTier.NotOpen -> Ash2
                        CapacityTier.Full -> Ash2
                        CapacityTier.AlmostFull -> Warning
                        CapacityTier.FillingUp -> MossLight
                        CapacityTier.Available -> Ash
                    },
                )
            }
        }
        // CTA — Phase-3 has no booking flow yet; the arrow is a placeholder
        // that becomes a real navigation target in Phase 5. Not-open classes
        // keep the arrow (they're viewable, not full); only genuinely-full
        // classes get the muted "+".
        if (isFull && !notOpen) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .border(1.dp, Mist, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+",
                    style = TextStyle(
                        fontFamily = Arcana.fonts.display,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = Ash,
                    ),
                )
            }
        } else {
            IconCircle(
                icon = ArcanaIcons.ArrowRight,
                diameter = 36, iconSize = 16,
                background = Ink, contentColor = Stone,
            )
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
