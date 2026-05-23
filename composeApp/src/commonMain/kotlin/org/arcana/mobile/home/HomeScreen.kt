package org.arcana.mobile.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.remember
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.datetime.toLocalDateTime
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
import org.arcana.mobile.ui.Display
import org.arcana.mobile.ui.DotField
import org.arcana.mobile.ui.Heading2
import org.arcana.mobile.ui.IconCircle
import org.arcana.mobile.ui.Overline
import org.arcana.mobile.ui.Pulse
import org.arcana.mobile.ui.SectionRule
import org.arcana.mobile.ui.StrokeIcon
import org.arcana.mobile.ui.safeContentPadding

// ── Mock content — mirrors the design handoff; swap for live reservations later.
private data class Reservation(
    val day: String,
    val weekday: String,
    val date: String,
    val time: String,
    val dur: String,
    val name: String,
    val studio: String,
    val instructor: String,
    val spots: String,
)

private val RESERVATIONS = listOf(
    Reservation("TODAY", "TUE", "15", "07:00", "50min", "Reformer Flow", "FORM", "Reyna A.", "2 left"),
    Reservation("TODAY", "TUE", "15", "12:30", "45min", "Power Boxing", "RISE", "Marcus T.", "Locked"),
    Reservation("WED", "WED", "16", "06:15", "60min", "Strength · Lower", "APEX", "Jules K.", "8 open"),
    Reservation("FRI", "FRI", "18", "07:00", "50min", "Reformer Flow", "FORM", "Reyna A.", "12 open"),
)

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val hero = RESERVATIONS.first()
    val rest = RESERVATIONS.drop(1)
    val tz = remember { TimeZone.currentSystemDefault() }
    val today = remember(tz) { Clock.System.todayIn(tz) }
    val hour = remember(tz) { Clock.System.now().toLocalDateTime(tz).hour }
    val dateLabel = "${today.dayOfWeek.name.take(3)} · ${today.month.name.take(3)} ${today.day}"
    val greeting = timeOfDay(hour)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Stone)
            .safeContentPadding(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
    ) {
        item { TopBar() }
        item { Spacer(Modifier.height(32.dp)) }
        item { HeroHeader(dateLabel = dateLabel, greeting = greeting) }
        item { Spacer(Modifier.height(28.dp)) }
        item {
            SectionRule(
                label = "Next · in 18 min",
                accent = true,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
        item { NextUpCard(hero, modifier = Modifier.padding(horizontal = 24.dp)) }
        item { Spacer(Modifier.height(32.dp)) }
        item {
            SectionRule(label = "Upcoming · 3", modifier = Modifier.padding(horizontal = 24.dp))
        }
        item { Spacer(Modifier.height(8.dp)) }

        // Day-grouped upcoming list. Once this is server-driven we'll group by date
        // here and emit a sticky-header item per day; for now the showDay flag does it.
        itemsIndexed(rest) { i, r ->
            UpcomingRow(
                r,
                showDay = i == 0 || rest[i - 1].date != r.date,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }

        item { Spacer(Modifier.height(32.dp)) }
        item { ManifestoCard(modifier = Modifier.padding(horizontal = 24.dp)) }
    }
}

@Composable
private fun TopBar() {
    WordmarkLogo(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .height(24.dp),
        tint = Moss,
    )
}

@Composable
private fun HeroHeader(dateLabel: String, greeting: String) {
    Column(
        modifier = Modifier.padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Overline(text = dateLabel, color = Moss)
        Display(text = "Good\n$greeting,\nFelicia.", size = 56, color = Ink)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AccentText(text = "Two sessions on the board today.", size = 20, color = Ash)
            AccentText(text = "The work makes the week.", size = 20, color = Moss)
        }
    }
}

/** Returns "morning" / "afternoon" / "evening" based on the local hour. */
private fun timeOfDay(hour: Int): String = when {
    hour < 5 -> "evening"
    hour < 12 -> "morning"
    hour < 17 -> "afternoon"
    else -> "evening"
}

@Composable
private fun NextUpCard(r: Reservation, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Moss),
    ) {
        DotField(modifier = Modifier.matchParentSize(), color = Lime, alpha = 0.10f, spacing = 16)
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Overline(text = "${r.studio} · Studio 3", size = 10, color = Lime)
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Lime)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Overline(text = "Booked", size = 10, color = Ink)
                }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = r.time,
                    maxLines = 1,
                    softWrap = false,
                    style = TextStyle(
                        fontFamily = Arcana.fonts.display,
                        fontWeight = FontWeight.Bold,
                        fontSize = 60.sp,
                        lineHeight = 60.sp,
                        letterSpacing = (-0.03).em,
                        color = Stone,
                    ),
                )
                BodyText(text = "am", size = 18, color = StoneAlpha55, weight = FontWeight.Medium)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Heading2(text = r.name, size = 22, color = Stone)
                    BodyText(
                        text = "${r.instructor} · ${r.dur} · ${r.spots}",
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
        }
    }
}

@Composable
private fun UpcomingRow(r: Reservation, showDay: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        if (showDay) {
            Row(
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Overline(text = "${r.day} · ${r.weekday} ${r.date}", size = 10, color = Moss)
                Box(Modifier.weight(1f).height(1.dp).background(Mist))
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(modifier = Modifier.widthIn(min = 64.dp).wrapContentWidth(Alignment.Start)) {
                Text(
                    text = r.time,
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
                Overline(text = r.dur, size = 10, color = Ash)
            }
            Box(Modifier.width(1.dp).height(40.dp).background(Mist))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Overline(text = r.studio, size = 10, color = Moss)
                    Box(Modifier.size(4.dp).clip(CircleShape).background(Ash2))
                    Overline(text = r.instructor, size = 10, color = Ash)
                }
                Spacer(Modifier.height(4.dp))
                BodyText(
                    text = r.name,
                    size = 16,
                    color = Ink,
                    weight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            StrokeIcon(icon = ArcanaIcons.ChevronRight, size = 20.dp, tint = Ash)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Mist))
    }
}

@Composable
private fun ManifestoCard(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Ink),
    ) {
        DotField(modifier = Modifier.matchParentSize(), color = Lime, alpha = 0.10f, spacing = 16)
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Overline(text = "Week 19 · ARC 2026", size = 10, color = Lime)
            Heading2(text = "7 sessions\non the books.", size = 26, color = Stone)
            BodyText(
                text = "Your monthly cap renews 31 May.",
                size = 12,
                color = StoneAlpha65,
            )
        }
    }
}
