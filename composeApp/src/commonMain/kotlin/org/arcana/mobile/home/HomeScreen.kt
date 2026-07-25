package org.arcana.mobile.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.datetime.toLocalDateTime
import org.arcana.mobile.booking.bookingInfoOrNull
import org.arcana.mobile.data.BookingDto
import org.arcana.mobile.theme.Arcana
import org.arcana.mobile.theme.Ash
import org.arcana.mobile.theme.Ash2
import org.arcana.mobile.theme.Ink
import org.arcana.mobile.theme.Lime
import org.arcana.mobile.theme.Mist
import org.arcana.mobile.theme.Moss
import org.arcana.mobile.theme.Stone
import org.arcana.mobile.theme.StoneAlpha55
import org.arcana.mobile.theme.StoneAlpha65
import org.arcana.mobile.theme.WordmarkLogo
import org.arcana.mobile.ui.AccentText
import org.arcana.mobile.ui.ArcanaIcons
import org.arcana.mobile.ui.BodyText
import org.arcana.mobile.ui.Caption
import org.arcana.mobile.ui.Display
import org.arcana.mobile.ui.FullScreenError
import org.arcana.mobile.ui.Heading2
import org.arcana.mobile.ui.IconCircle
import org.arcana.mobile.ui.Overline
import org.arcana.mobile.ui.RefreshFailedToast
import org.arcana.mobile.ui.SectionRule
import org.arcana.mobile.ui.ShimmerBox
import org.arcana.mobile.ui.StatusPill
import org.arcana.mobile.ui.StatusPillFitted
import org.arcana.mobile.ui.StrokeIcon
import org.arcana.mobile.ui.TextLink
import org.arcana.mobile.ui.safeContentPadding
import org.koin.compose.viewmodel.koinViewModel

// ── Max upcoming rows shown below the hero card ────────────────────────────────
private const val UPCOMING_PREVIEW_COUNT = 4

/** Fixed width of the upcoming row's left (time) column — holds the time and the
 *  width-filling booking-status pill so every row aligns, mirroring the Schedule
 *  row. Keeping the pill out of the title/meta line stops the studio · location
 *  stamp from being clipped. */
private val UPCOMING_TIME_COL_WIDTH = 64.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onSeeAllBookings: () -> Unit,
    onOpenClass: (Int) -> Unit,
) {
    val vm = koinViewModel<HomeViewModel>()
    LaunchedEffect(Unit) { vm.load() }
    val state by vm.uiState.collectAsState()
    val refreshing by vm.isRefreshing.collectAsState()
    val refreshFailed by vm.refreshFailed.collectAsState()
    val retrying by vm.retrying.collectAsState()

    val tz = remember { TimeZone.currentSystemDefault() }
    val today = remember(tz) { Clock.System.todayIn(tz) }
    val hour = remember(tz) { Clock.System.now().toLocalDateTime(tz).hour }
    val dateLabel = "${today.dayOfWeek.name.take(3)} · ${today.month.name.take(3)} ${today.day}"
    val greeting = timeOfDay(hour)

    val errorState = state as? HomeUiState.Error
    Box(modifier = Modifier.fillMaxSize()) {
    if (errorState != null) {
        // Cold load with nothing cached: the error owns the screen.
        FullScreenError(type = errorState.type, onRetry = vm::retry, retrying = retrying)
    } else {
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = vm::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Stone)
            .safeContentPadding(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
    ) {
        // ── Static chrome — always visible ─────────────────────────────────
        item { TopBar() }
        item { Spacer(Modifier.height(32.dp)) }

        // ── Hero header — name shimmer while loading ────────────────────────
        item {
            val displayName = when (val s = state) {
                is HomeUiState.Success -> firstName(s.displayName)
                else -> null // null = loading or error; HeroHeader renders a shimmer name slot
            }
            HeroHeader(
                dateLabel = dateLabel,
                greeting = greeting,
                displayName = displayName,
            )
        }
        item { Spacer(Modifier.height(28.dp)) }

        when (val s = state) {
            is HomeUiState.Loading -> {
                // ── Next section — shimmer card ─────────────────────────────
                item {
                    SectionRule(
                        label = "Next up",
                        accent = true,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
                item {
                    ShimmerBox(
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .fillMaxWidth()
                            .height(180.dp),
                        shape = RoundedCornerShape(20.dp),
                    )
                }
                item { Spacer(Modifier.height(32.dp)) }

                // ── Upcoming section — shimmer rows ─────────────────────────
                item {
                    SectionRule(
                        label = "Upcoming",
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
                repeat(3) {
                    item {
                        ShimmerBox(
                            modifier = Modifier
                                .padding(horizontal = 24.dp, vertical = 4.dp)
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                }
                item { Spacer(Modifier.height(32.dp)) }

                // ── ManifestoCard shimmer ───────────────────────────────────
                item {
                    ShimmerBox(
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .fillMaxWidth()
                            .height(120.dp),
                        shape = RoundedCornerShape(20.dp),
                    )
                }
            }

            // Cold-load failure takes over the whole screen (handled above the
            // LazyColumn) rather than sitting under a shimmering hero, which
            // would read as "still loading".
            is HomeUiState.Error -> Unit

            is HomeUiState.Success -> {
                val hero = s.upcoming.firstOrNull()
                val rest = s.upcoming.drop(1).take(UPCOMING_PREVIEW_COUNT)

                // ── Next-up card ──────────────────────────────────────────────
                if (hero != null) {
                    item {
                        val relLabel = remember(hero.session.startAt) {
                            relativeTime(hero.session.startAt, tz)
                        }
                        SectionRule(
                            label = "Next · $relLabel",
                            accent = true,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                    item {
                        NextUpCard(
                            booking = hero,
                            modifier = Modifier.padding(horizontal = 24.dp),
                            onClick = { onOpenClass(hero.session.id) },
                        )
                    }
                } else {
                    item {
                        SectionRule(
                            label = "Next up",
                            accent = true,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                    item {
                        Caption(
                            text = "No upcoming classes — browse the schedule.",
                            size = 13,
                            color = Ash,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                    }
                }

                item { Spacer(Modifier.height(32.dp)) }

                // ── Upcoming preview rows (items after the hero) ──────────────
                item { Spacer(Modifier.height(8.dp)) }

                if (rest.isEmpty() && hero == null) {
                    item {
                        Caption(
                            text = "Nothing booked yet.",
                            size = 13,
                            color = Ash,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                    }
                } else {
                    itemsIndexed(rest) { i, b ->
                        val day = b.session.startAt.take(10)
                        // Last row of its day: the next day's header rule already
                        // separates the groups, so drop this row's bottom hairline
                        // to avoid a doubled divider. The final row drops it too
                        // (nothing but "See all" follows).
                        val isLastOfDay =
                            i == rest.lastIndex || rest[i + 1].session.startAt.take(10) != day
                        UpcomingRow(
                            booking = b,
                            tz = tz,
                            showDayDivider = i == 0 || rest[i - 1].session.startAt.take(10) != day,
                            showBottomDivider = !isLastOfDay,
                            onClick = { onOpenClass(b.session.id) },
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                    }
                }

                // "See all" sits below the previewed rows, not in the header.
                item {
                    Spacer(Modifier.height(12.dp))
                    TextLink(
                        label = "See all",
                        onClick = onSeeAllBookings,
                        color = Moss,
                        underline = false,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }

                item { Spacer(Modifier.height(32.dp)) }

                // ── Manifesto / credits card ──────────────────────────────────
                item {
                    ManifestoCard(
                        creditsRemaining = s.creditsRemaining,
                        upcomingMonth = s.upcomingMonth,
                        upcomingCredits = s.upcomingCredits,
                        weekStreak = s.weekStreak,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }
        }
    }
    }
    }
    // A refresh failed while good content is on screen: keep the content and
    // say so, rather than wiping the screen for a full-screen error.
    if (refreshFailed) {
        RefreshFailedToast(
            onRetry = vm::retry,
            retrying = retrying,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 24.dp),
        )
    }
    }
}

// ── Relative-time helper ────────────────────────────────────────────────────────

/**
 * Returns a human-readable label relative to now:
 *   "in 18 min"  — less than 60 minutes away
 *   "in 3h"      — less than 24 hours away
 *   "Mon 6:00am" — further out
 */
internal fun relativeTime(startAtIso: String, tz: TimeZone): String {
    return try {
        val start = Instant.parse(startAtIso)
        val now = Clock.System.now()
        val diffSec = (start - now).inWholeSeconds
        val diffMin = diffSec / 60
        val diffHours = diffMin / 60
        when {
            diffMin < 0 -> {
                // Already started — show local time
                val local = start.toLocalDateTime(tz)
                formatLocalTime(local.hour, local.minute)
            }
            diffMin < 60 -> "in ${diffMin}min"
            diffHours < 24 -> "in ${diffHours}h"
            else -> {
                val local = start.toLocalDateTime(tz)
                val day = local.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.titlecase() }
                "$day ${formatLocalTime(local.hour, local.minute)}"
            }
        }
    } catch (_: Exception) {
        startAtIso.take(16).replace("T", " ")
    }
}

private fun formatLocalTime(hour: Int, minute: Int): String {
    val h = if (hour % 12 == 0) 12 else hour % 12
    val m = minute.toString().padStart(2, '0')
    val ampm = if (hour < 12) "am" else "pm"
    return "${h}:${m}${ampm}"
}

// ── Private time-of-day helper ──────────────────────────────────────────────────

/** Returns "morning" / "afternoon" / "evening" based on the local hour. */
private fun timeOfDay(hour: Int): String = when {
    hour < 5 -> "evening"
    hour < 12 -> "morning"
    hour < 17 -> "afternoon"
    else -> "evening"
}

// ── Components ──────────────────────────────────────────────────────────────────

@Composable
private fun TopBar() {
    WordmarkLogo(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .height(24.dp),
        tint = Moss,
    )
}

/**
 * Hero greeting header. Pass [displayName] = null while loading to render a
 * ShimmerBox in place of the name; pass "" to omit it (error state); pass the
 * resolved first name for success.
 */
@Composable
private fun HeroHeader(dateLabel: String, greeting: String, displayName: String?) {
    Column(
        modifier = Modifier.padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Overline(text = dateLabel, color = Moss)
        when {
            displayName == null -> {
                // Loading: show static greeting lines with a shimmer slot for the name
                Display(text = "Good\n$greeting,", size = 56, color = Ink)
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .height(52.dp),
                    shape = RoundedCornerShape(8.dp),
                )
            }
            displayName.isBlank() -> {
                Display(text = "Good\n$greeting.", size = 56, color = Ink)
            }
            else -> {
                Display(
                    text = "Good\n$greeting,\n$displayName.",
                    size = 56,
                    color = Ink,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AccentText(text = "Show up. Do the work.", size = 20, color = Ash)
            AccentText(text = "The rest takes care of itself.", size = 20, color = Moss)
        }
    }
}

@Composable
private fun NextUpCard(booking: BookingDto, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    val session = booking.session
    val tz = remember { TimeZone.currentSystemDefault() }
    val local = remember(session.startAt) {
        try {
            Instant.parse(session.startAt).toLocalDateTime(tz)
        } catch (_: Exception) { null }
    }
    val timeStr = local?.let {
        val h = if (it.hour % 12 == 0) 12 else it.hour % 12
        val m = it.minute.toString().padStart(2, '0')
        "$h:$m"
    } ?: "--"
    val amPm = local?.let { if (it.hour < 12) "am" else "pm" } ?: ""
    val spotLabel = booking.spot?.label ?: booking.fulfilledSpot?.label ?: booking.requestedSpot?.label

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .background(Moss),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                val studioLine = if (spotLabel != null) "${session.studio} · $spotLabel" else session.studio
                Overline(text = studioLine, size = 10, color = Lime)
                StatusPill(booking.status)
            }
            // Time row: baseline-align the large hour digit and the am/pm suffix
            // so the suffix sits on the digit's baseline rather than floating at
            // the top of the BodyText line box.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = timeStr,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.alignByBaseline(),
                    style = TextStyle(
                        fontFamily = Arcana.fonts.display,
                        fontWeight = FontWeight.Bold,
                        fontSize = 60.sp,
                        lineHeight = 60.sp,
                        letterSpacing = (-0.03).em,
                        color = Stone,
                    ),
                )
                Text(
                    text = amPm,
                    maxLines = 1,
                    modifier = Modifier.alignByBaseline(),
                    style = TextStyle(
                        fontFamily = Arcana.fonts.body,
                        fontWeight = FontWeight.Medium,
                        fontSize = 18.sp,
                        lineHeight = 18.sp,
                        color = StoneAlpha55,
                    ),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Heading2(text = session.name, size = 22, color = Stone)
                    val durationMin = remember(session.startAt, session.endAt) {
                        try {
                            val s = Instant.parse(session.startAt)
                            val e = Instant.parse(session.endAt)
                            "${(e - s).inWholeMinutes}min"
                        } catch (_: Exception) { "" }
                    }
                    // STUDIO · LOCATION · DURATION — location only when present.
                    val metaLine = buildString {
                        append(session.studio)
                        session.location?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                        if (durationMin.isNotEmpty()) append(" · ").append(durationMin)
                    }
                    BodyText(
                        text = metaLine,
                        size = 12,
                        color = StoneAlpha65,
                    )
                }
                IconCircle(
                    icon = ArcanaIcons.ArrowUpRight,
                    diameter = 44,
                    iconSize = 20,
                    background = Lime,
                    contentColor = Ink,
                )
            }
            // Member-facing booking note (e.g. a door code) — only when present.
            bookingInfoOrNull(booking)?.let { note ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Overline(text = "Booking info", size = 10, color = Lime)
                    BodyText(
                        text = note,
                        size = 13,
                        color = StoneAlpha65,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun UpcomingRow(
    booking: BookingDto,
    tz: TimeZone,
    showDayDivider: Boolean,
    showBottomDivider: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val session = booking.session
    val local = remember(session.startAt) {
        try { Instant.parse(session.startAt).toLocalDateTime(tz) } catch (_: Exception) { null }
    }
    val timeStr = local?.let {
        val h = if (it.hour % 12 == 0) 12 else it.hour % 12
        val m = it.minute.toString().padStart(2, '0')
        "$h:$m"
    } ?: "--:--"
    val durationStr = remember(session.startAt, session.endAt) {
        try {
            val s = Instant.parse(session.startAt)
            val e = Instant.parse(session.endAt)
            "${(e - s).inWholeMinutes}min"
        } catch (_: Exception) { "" }
    }
    val dayLabel = local?.let {
        val dow = it.dayOfWeek.name.take(3)
        val mon = it.month.name.take(3)
        "${dow} · ${mon} ${it.date.day}"
    } ?: ""

    Column(modifier = modifier) {
        if (showDayDivider && dayLabel.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Overline(text = dayLabel, size = 10, color = Moss)
                Box(Modifier.weight(1f).height(1.dp).background(Mist))
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Fixed-width left column. The booking-status pill sits above the
            // time (auto-shrunk to fit) so it's out of the meta/title line — the
            // studio · location stamp gets the row's full width instead of being
            // clipped. Mirrors the Schedule row.
            Column(modifier = Modifier.width(UPCOMING_TIME_COL_WIDTH)) {
                StatusPillFitted(booking.status)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = timeStr,
                    maxLines = 1,
                    softWrap = false,
                    style = TextStyle(
                        fontFamily = Arcana.fonts.display,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = Ink,
                    ),
                )
                Spacer(Modifier.height(4.dp))
                if (durationStr.isNotEmpty()) {
                    Overline(text = durationStr, size = 10, color = Ash)
                }
            }
            Box(Modifier.width(1.dp).height(40.dp).background(Mist))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Overline(text = session.studio, size = 10, color = Moss)
                    session.location?.takeIf { it.isNotBlank() }?.let { loc ->
                        Box(Modifier.size(4.dp).clip(CircleShape).background(Ash2))
                        // Studio stays full; the location flexes into the leftover
                        // space and ellipsizes rather than hard-clipping mid-word.
                        Overline(
                            text = loc,
                            size = 10,
                            color = Ash,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    booking.spot?.let { spot ->
                        Box(Modifier.size(4.dp).clip(CircleShape).background(Ash2))
                        Overline(text = spot.label, size = 10, color = Ash)
                    }
                }
                Spacer(Modifier.height(4.dp))
                BodyText(
                    text = session.name,
                    size = 16,
                    color = Ink,
                    weight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        // Bottom hairline between rows of the same day; suppressed on a day's
        // last row so the next day header's rule is the only separator.
        if (showBottomDivider) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Mist))
        }
    }
}

@Composable
private fun ManifestoCard(
    creditsRemaining: Int?,
    upcomingMonth: String?,
    upcomingCredits: Int?,
    weekStreak: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Ink),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (creditsRemaining == null) {
                // No live wallet — explicit, calm empty state (lapsed member or
                // a between-cohorts / pre-rolling lull). Stands alone: the streak
                // sub-line is suppressed so the tile reads only "No active
                // membership."
                Heading2(text = "No active membership.", size = 26, color = Stone)
            } else {
                Heading2(text = "$creditsRemaining classes remaining.", size = 26, color = Stone)
                // The "next period" wallet appears only when the member has bought
                // next month while still in the current month — labelled by month.
                if (upcomingCredits != null) {
                    BodyText(
                        text = "Next: ${upcomingMonth ?: "upcoming"} · $upcomingCredits credits",
                        size = 12,
                        color = StoneAlpha65,
                    )
                }
                val streakLine = if (weekStreak > 0) "$weekStreak-week streak. Keep it going." else "Build your streak."
                BodyText(
                    text = streakLine,
                    size = 12,
                    color = StoneAlpha65,
                )
            }
        }
    }
}
