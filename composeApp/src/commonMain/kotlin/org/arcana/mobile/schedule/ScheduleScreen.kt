package org.arcana.mobile.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.datetime.toLocalDateTime
import org.arcana.mobile.data.LocationBriefDto
import org.arcana.mobile.data.ScheduleSessionDto
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
import androidx.compose.ui.text.style.TextOverflow
import org.arcana.mobile.ui.ArcanaIcons
import org.arcana.mobile.ui.BodyText
import org.arcana.mobile.ui.Display
import org.arcana.mobile.ui.Heading2
import org.arcana.mobile.ui.IconCircle
import org.arcana.mobile.ui.Overline
import org.arcana.mobile.ui.SectionRule
import org.arcana.mobile.ui.StrokeIcon
import org.arcana.mobile.ui.safeContentPadding
import org.koin.compose.viewmodel.koinViewModel

// ── Constants -----------------------------------------------------------------

private val FALLBACK_STUDIO_COLOR = Moss

/** Sessions with <= 2 remaining spots are visually marked as "scarce". */
private const val SCARCE_THRESHOLD = 2

/** ISO-string Saver for LocalDate so the selected day survives navigation
 *  to a detail screen and back (and process death). The auto-saver in
 *  rememberSaveable only handles primitives + Strings, so non-primitive
 *  state types need an explicit Saver. */
private val LocalDateSaver: Saver<LocalDate, String> = Saver(
    save = { it.toString() },
    restore = { LocalDate.parse(it) },
)

// ── Display helpers -----------------------------------------------------------

private fun titleCase(name: String): String =
    name.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

private fun Month.abbr(): String = name.take(3)
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

// ── Capacity tier -------------------------------------------------------------

private enum class CapacityTier(val label: String) {
    Full("FULL"),
    AlmostFull("ALMOST FULL"),
    FillingUp("FILLING UP"),
    Available("AVAILABLE"),
}

private fun ScheduleSessionDto.capacityTier(): CapacityTier {
    val available = arcanaSpotsAvailable
    return when {
        // <= 0 mirrors the defensive guard on `isFull` in ClassRow so over-
        // booked sessions (negative available) consistently render as Full
        // across label, color, and CTA.
        available <= 0 -> CapacityTier.Full
        available <= 2 -> CapacityTier.AlmostFull
        arcanaSpotsOffered > 0 &&
            available.toFloat() / arcanaSpotsOffered <= 0.4f -> CapacityTier.FillingUp
        else -> CapacityTier.Available
    }
}

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

@Composable
fun ScheduleScreen(
    modifier: Modifier = Modifier,
    viewModel: ScheduleViewModel = koinViewModel(),
    onOpenClassDetail: (Int) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Stone)
            .safeContentPadding(),
    ) {
        when (val s = state) {
            is ScheduleUiState.Loading -> LoadingPlaceholder()
            is ScheduleUiState.Error -> ErrorBlock(message = s.message, onRetry = viewModel::reload)
            is ScheduleUiState.Success -> SuccessContent(state = s, viewModel = viewModel, onOpenClassDetail = onOpenClassDetail)
        }
    }
}

@Composable
private fun LoadingPlaceholder() {
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    Column {
        Display(
            text = "${titleCase(today.month.name)}.",
            size = 56, color = Ink,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp),
        )
        Spacer(Modifier.height(24.dp))
        Row(modifier = Modifier.padding(horizontal = 24.dp)) {
            Overline(text = "LOADING SCHEDULE…", size = 12, color = Ash)
        }
    }
}

@Composable
private fun ErrorBlock(message: String, onRetry: () -> Unit) {
    Column(modifier = Modifier.padding(24.dp)) {
        Heading2(text = "Couldn't load schedule", size = 22, color = Ink)
        Spacer(Modifier.height(8.dp))
        BodyText(text = message, size = 14, color = Ash)
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(Ink)
                .clickable(onClick = onRetry)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Overline(text = "RETRY", size = 12, color = Stone)
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
) {
    val tz = remember { TimeZone.currentSystemDefault() }
    val today = remember { Clock.System.todayIn(tz) }
    // rememberSaveable (vs plain remember) so the selected day survives
    // navigation into ClassDetail and back — NavController preserves the
    // saved-state bundle for each entry on the back stack.
    var selectedDate by rememberSaveable(stateSaver = LocalDateSaver) {
        mutableStateOf(today)
    }

    val sessionsForSelected = state.sessionsByDay[selectedDate].orEmpty()
    // Recompute the time-of-day bucketing only when the selected day's
    // session list actually changes (otherwise every recomposition reparses).
    val byBand: Map<TimeBand, List<ScheduleSessionDto>> = remember(sessionsForSelected) {
        sessionsForSelected.groupBy {
            Instant.parse(it.startAt).toLocalDateTime(tz).time.timeBand()
        }
    }
    val activeBands = remember(byBand) {
        TimeBand.values().filter { byBand[it]?.isNotEmpty() == true }
    }

    val totalForSelected = state.totalCountByDay[selectedDate] ?: sessionsForSelected.size
    // The soloed brand drives the tier-2 sub-row's accent color (hairline + pin).
    val soloedStudio = remember(state.filters.studioSlugs, state.knownStudios) {
        if (state.filters.studioSlugs.size == 1) {
            val slug = state.filters.studioSlugs.single()
            state.knownStudios.firstOrNull { it.slug == slug }
        } else null
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        // Top capsule — "SCHEDULE · 14 DAYS" plus a paper chip with the live
        // studio/site counts from the unfiltered fetch.
        item("header-capsule") {
            HeaderCapsule(
                studioCount = state.knownStudios.size,
                siteCount = state.siteCount,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp),
            )
        }

        item("title") {
            // Track the *selected* day so the header flips when the user taps
            // a day in a different month (e.g. May 27 → June 1 on the rail).
            Display(
                text = "${titleCase(selectedDate.month.name)}.",
                size = 56, color = Ink,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp),
            )
        }

        item("subline") {
            val studioList = state.knownStudios.joinToString(" · ") { titleCase(it.name) }
            Spacer(Modifier.height(8.dp))
            BodyText(
                text = if (studioList.isEmpty()) "Book ahead 14 days."
                else "Book ahead 14 days · $studioList",
                size = 14, color = Ash,
                modifier = Modifier.padding(horizontal = 24.dp),
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
                    val count = state.sessionsByDay[date]?.size ?: 0
                    DayChip(
                        date = date,
                        label = when (i) { 0 -> "TODAY"; 1 -> "TMR"; else -> "" },
                        count = count,
                        active = date == selectedDate,
                        onClick = { selectedDate = date },
                    )
                }
            }
        }

        item("day-banner") {
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Heading2(text = titleCase(selectedDate.dayOfWeek.name), size = 22, color = Ink)
                // "5 of 10 classes" when filters narrow the day; just "10 classes"
                // when nothing's filtered out. Totals come from the unfiltered cache
                // so the second number doesn't tick down as the user toggles chips.
                val countText = if (sessionsForSelected.size != totalForSelected) {
                    "${sessionsForSelected.size} of $totalForSelected classes"
                } else {
                    "$totalForSelected classes"
                }
                Overline(
                    text = "${selectedDate.day} ${selectedDate.month.abbr()} · $countText",
                    size = 12, color = Ash,
                )
            }
        }

        // Tier 1: brand chips. Each carries a leading studio-color dot + a
        // small caret hinting it's drillable into the tier-2 sub-row.
        item("filter-chips") {
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BrandFilterChip(
                    label = "ALL",
                    active = state.filters.studioSlugs.isEmpty(),
                    onClick = { viewModel.clearStudios() },
                )
                state.knownStudios.forEach { studio ->
                    BrandFilterChip(
                        label = studio.name.uppercase(),
                        active = studio.slug in state.filters.studioSlugs,
                        dotColor = studioColorFor(studio.primaryColor),
                        showCaret = true,
                        onClick = { viewModel.toggleStudio(studio.slug) },
                    )
                }
                BrandFilterChip(
                    label = "AVAILABLE",
                    active = state.filters.availableOnly,
                    onClick = { viewModel.toggleAvailableOnly() },
                )
            }
        }

        // Tier 2: location sub-row. Only renders when exactly one brand is
        // soloed — outside that window the chip set is empty and the row
        // collapses out of the LazyColumn entirely.
        if (state.knownLocationsForBrand.isNotEmpty() && soloedStudio != null) {
            item("location-subrow") {
                Spacer(Modifier.height(10.dp))
                LocationSubRow(
                    brandColor = studioColorFor(soloedStudio.primaryColor),
                    locations = state.knownLocationsForBrand,
                    activeLocationIds = state.filters.locationIds,
                    onClickAll = { viewModel.clearLocations() },
                    onToggleLocation = { id -> viewModel.toggleLocation(id) },
                )
            }
        }

        item("filter-trailing-space") { Spacer(Modifier.height(16.dp)) }

        if (sessionsForSelected.isEmpty()) {
            item("empty") {
                Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp)) {
                    BodyText(
                        text = "No classes match your filters for this day.",
                        size = 14, color = Ash,
                    )
                }
            }
        } else {
            activeBands.forEachIndexed { bandIdx, band ->
                item("band-header-$band") {
                    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                        if (bandIdx > 0) Spacer(Modifier.height(24.dp))
                        SectionRule(label = band.label)
                        Spacer(Modifier.height(8.dp))
                    }
                }
                items(
                    items = byBand[band].orEmpty(),
                    key = { session -> "row-${session.id}" },
                ) { session ->
                    Box(modifier = Modifier.padding(horizontal = 24.dp)) {
                        ClassRow(
                            session, tz,
                            onClick = { onOpenClassDetail(session.id) },
                        )
                    }
                }
            }
        }
    }
}

// ── Day chip ------------------------------------------------------------------

@Composable
private fun DayChip(
    date: LocalDate,
    label: String,
    count: Int,
    active: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .size(width = 56.dp, height = 76.dp)
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
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(minOf(count, 5)) {
                Box(
                    Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background((if (active) Lime else MossLight).copy(alpha = if (active) 1f else 0.6f))
                )
            }
        }
    }
}

// ── Header capsule ------------------------------------------------------------

/** Top header row: "SCHEDULE · 14 DAYS" on the left, a paper-filled
 *  "(gear) N STUDIOS · M SITES" chip on the right. Pure decoration — the
 *  chip isn't tappable in Phase 3.5 (no studios-management surface yet). */
@Composable
private fun HeaderCapsule(
    studioCount: Int,
    siteCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Overline(text = "SCHEDULE · 14 DAYS", size = 11, color = Ash)
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(Paper)
                .border(1.dp, Mist, CircleShape)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StrokeIcon(icon = ArcanaIcons.Settings, size = 14.dp, tint = Ash)
            Overline(
                text = "$studioCount STUDIOS · $siteCount SITES",
                size = 10, color = Ash,
            )
        }
    }
}

// ── Brand filter chip (tier 1) ------------------------------------------------

@Composable
private fun BrandFilterChip(
    label: String,
    active: Boolean = false,
    dotColor: Color? = null,
    showCaret: Boolean = false,
    onClick: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (active) Ink else Color.Transparent)
            .border(1.dp, if (active) Ink else Mist, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (dotColor != null) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor))
        }
        Text(
            text = label,
            maxLines = 1, softWrap = false,
            style = TextStyle(
                fontFamily = Arcana.fonts.display,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                letterSpacing = 0.10.em,
                color = if (active) Stone else Ink,
            ),
        )
        if (showCaret) {
            StrokeIcon(
                icon = ArcanaIcons.ChevronDown,
                size = 12.dp,
                tint = if (active) Stone.copy(alpha = 0.75f) else Ash2,
            )
        }
    }
}

// ── Location sub-row (tier 2) -------------------------------------------------

/** Tier-2 location drill-down. Hairline + pin on the left chain it visually
 *  back to the soloed brand chip above. Location chips are filled with the
 *  brand color when active (not Ink) — continues the visual hierarchy from
 *  brand-chip (ink) → location-chip (brand-tinted). */
@Composable
private fun LocationSubRow(
    brandColor: Color,
    locations: List<LocationChipData>,
    activeLocationIds: Set<Int>,
    onClickAll: () -> Unit,
    onToggleLocation: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Hairline + pin lead-in — anchors the row to the soloed brand.
        Box(
            Modifier
                .width(20.dp)
                .height(1.dp)
                .background(brandColor),
        )
        StrokeIcon(icon = ArcanaIcons.Pin, size = 14.dp, tint = brandColor)
        LocationChip(
            label = "ALL",
            active = activeLocationIds.isEmpty(),
            brandColor = brandColor,
            onClick = onClickAll,
        )
        locations.forEach { loc ->
            LocationChip(
                label = loc.shortLabel,
                active = loc.id in activeLocationIds,
                brandColor = brandColor,
                onClick = { onToggleLocation(loc.id) },
            )
        }
    }
}

@Composable
private fun LocationChip(
    label: String,
    active: Boolean,
    brandColor: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (active) brandColor else Color.Transparent)
            .border(1.dp, if (active) brandColor else Mist, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            maxLines = 1, softWrap = false,
            style = TextStyle(
                fontFamily = Arcana.fonts.display,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                letterSpacing = 0.10.em,
                color = if (active) Stone else Ink,
            ),
        )
    }
}

// ── Class row -----------------------------------------------------------------

@Composable
private fun ClassRow(
    session: ScheduleSessionDto,
    tz: TimeZone,
    onClick: () -> Unit = {},
) {
    val time = Instant.parse(session.startAt).toLocalDateTime(tz).time
    val studio = session.location.studio
    val sc = studioColorFor(studio.primaryColor)
    val available = session.arcanaSpotsAvailable
    val offered = session.arcanaSpotsOffered
    val isFull = available <= 0
    val isScarce = !isFull && available <= SCARCE_THRESHOLD
    val fill = if (offered > 0) {
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
        // Time column
        Column(modifier = Modifier.widthIn(min = 64.dp).wrapContentWidth(Alignment.Start)) {
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
                instructor = instructorName.uppercase(),
                studioColor = sc,
            )
            Spacer(Modifier.height(4.dp))
            BodyText(
                text = session.template.name,
                size = 16,
                color = if (isFull) Ash else Ink,
                weight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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
                val tier = session.capacityTier()
                Overline(
                    text = tier.label,
                    size = 10,
                    color = when (tier) {
                        CapacityTier.Full -> Ash2
                        CapacityTier.AlmostFull -> Warning
                        CapacityTier.FillingUp -> MossLight
                        CapacityTier.Available -> Ash
                    },
                )
            }
        }
        // CTA — Phase-3 has no booking flow yet; the arrow is a placeholder
        // that becomes a real navigation target in Phase 5.
        if (isFull) {
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
 * Single-line metadata stamp: `BRAND · LOCATION    ·    INSTRUCTOR`.
 *
 * Visual subtleties from the design handoff:
 * - Brand is fully saturated studio color, weight 700.
 * - The dot between brand and location is the same color at 55% alpha — visually
 *   linking the two as one unit.
 * - Location is the same color at 78% alpha but weight 500 (slightly demoted).
 * - The dot between location and instructor is a heavier Ash2 — a *visual
 *   separator* between the studio unit and the neutral instructor stamp.
 * - Instructor sits in Ash, weight 500.
 *
 * Overflow: location and instructor both flex with ellipsis, so neither single
 * field can squeeze the other off-screen. Brand is intrinsic — it never
 * truncates, because brand identity matters more than a few extra characters
 * of location text.
 */
@Composable
private fun MetaLine(
    brand: String,
    location: String,
    instructor: String,
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
            // LOCATION — flexes + ellipsises before instructor.
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
        if (instructor.isNotEmpty()) {
            // location→instructor separator: heavier Ash2 — a visual break.
            Box(
                Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(Ash2),
            )
            Text(
                text = instructor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
                style = TextStyle(
                    fontFamily = Arcana.fonts.body,
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.sp,
                    letterSpacing = 0.20.em,
                    color = Ash,
                ),
            )
        }
    }
}
