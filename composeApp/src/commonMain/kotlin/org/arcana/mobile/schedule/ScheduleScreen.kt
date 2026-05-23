package org.arcana.mobile.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
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
import org.arcana.mobile.ui.ArcanaIcons
import org.arcana.mobile.ui.BodyText
import org.arcana.mobile.ui.Display
import org.arcana.mobile.ui.Heading2
import org.arcana.mobile.ui.IconCircle
import org.arcana.mobile.ui.Overline
import org.arcana.mobile.ui.SectionRule
import org.arcana.mobile.ui.StrokeIcon
import org.arcana.mobile.ui.safeContentPadding

// ── Mock class data — server-driven later.
private data class ClassItem(
    val time: String,
    val dur: String,
    val name: String,
    val studio: String,
    val instructor: String,
    val spots: Int,
    val total: Int,
    val booked: Boolean = false,
    val full: Boolean = false,
    val scarce: Boolean = false,
)

private data class Period(val name: String, val items: List<ClassItem>)

private val TODAY_CLASSES = listOf(
    Period("MORNING", listOf(
        ClassItem("06:15", "60", "Reformer · Foundations", "FORM", "Reyna Alvarez", 4, 14),
        ClassItem("07:00", "50", "Reformer Flow", "FORM", "Reyna Alvarez", 2, 14, booked = true),
        ClassItem("08:30", "45", "Boxing · Technique", "RISE", "Marcus Tate", 6, 12),
        ClassItem("09:30", "60", "Strength · Lower", "APEX", "Jules Kwon", 8, 10),
    )),
    Period("AFTERNOON", listOf(
        ClassItem("12:30", "45", "Power Boxing", "RISE", "Marcus Tate", 0, 12, booked = true, full = true),
        ClassItem("13:30", "50", "Mat · Restorative", "FORM", "Helena Park", 11, 16),
    )),
    Period("EVENING", listOf(
        ClassItem("17:30", "60", "Strength · Push", "APEX", "Jules Kwon", 1, 10, scarce = true),
        ClassItem("19:00", "45", "Conditioning · Sweat", "RISE", "Marcus Tate", 9, 12),
    )),
)

private fun studioColor(studio: String): Color = when (studio) {
    "FORM" -> Moss
    "RISE" -> Lime
    "APEX" -> MossLight
    else -> Moss
}

// ── Date helpers — kotlinx-datetime in commonMain works on Android + iOS.
private data class ScheduleDay(val date: LocalDate, val label: String, val count: Int)

private fun buildSchedule(today: LocalDate, count: Int = 14): List<ScheduleDay> =
    List(count) { i ->
        val d = today.plus(i, DateTimeUnit.DAY)
        ScheduleDay(
            date = d,
            label = when (i) { 0 -> "TODAY"; 1 -> "TMR"; else -> "" },
            // Stand-in class count — until the schedule endpoint exists.
            count = 3 + (d.dayOfWeek.ordinal % 5),
        )
    }

private fun titleCase(name: String): String =
    name.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

private fun Month.abbr(): String = name.take(3)
private fun LocalDate.weekdayAbbr(): String = dayOfWeek.name.take(3)

@Composable
fun ScheduleScreen(modifier: Modifier = Modifier) {
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val days = remember(today) { buildSchedule(today) }
    val totalClasses = TODAY_CLASSES.sumOf { it.items.size }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Stone)
            .safeContentPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        // Header — month only; the rest is inferable from the day rail.
        Display(
            text = "${titleCase(today.month.name)}.",
            size = 56,
            color = Ink,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp),
        )

        // Day rail
        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            days.forEachIndexed { i, day -> DayChip(day, active = i == 0) }
        }

        // Selected day banner
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.padding(horizontal = 24.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Heading2(text = titleCase(today.dayOfWeek.name), size = 22, color = Ink)
            Overline(
                text = "${today.day} ${today.month.abbr()} · $totalClasses classes",
                size = 12,
                color = Ash,
            )
        }

        // Filter chips
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(label = "ALL", active = true)
            FilterChip(label = "FORM", dotColor = studioColor("FORM"))
            FilterChip(label = "RISE", dotColor = studioColor("RISE"))
            FilterChip(label = "APEX", dotColor = studioColor("APEX"))
            FilterChip(label = "AVAILABLE")
        }

        // Classes by period
        Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp)) {
            TODAY_CLASSES.forEachIndexed { i, period ->
                if (i > 0) Spacer(Modifier.height(24.dp))
                SectionRule(label = period.name)
                Spacer(Modifier.height(8.dp))
                period.items.forEach { ClassRow(it) }
            }
        }
    }
}

@Composable
private fun DayChip(day: ScheduleDay, active: Boolean) {
    Column(
        modifier = Modifier
            .size(width = 56.dp, height = 76.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) Moss else Paper)
            .border(1.dp, if (active) Moss else Mist, RoundedCornerShape(16.dp))
            .padding(top = 8.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = day.label.ifEmpty { day.date.weekdayAbbr() },
            maxLines = 1,
            softWrap = false,
            style = TextStyle(
                fontFamily = Arcana.fonts.body,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 0.20.em,
                color = if (active) Lime else Ash,
            ),
        )
        Text(
            text = day.date.day.toString(),
            maxLines = 1,
            softWrap = false,
            style = TextStyle(
                fontFamily = Arcana.fonts.display,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                letterSpacing = (-0.02).em,
                color = if (active) Stone else Ink,
            ),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(minOf(day.count, 5)) {
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

@Composable
private fun FilterChip(label: String, active: Boolean = false, dotColor: Color? = null) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (active) Ink else Color.Transparent)
            .border(1.dp, if (active) Ink else Mist, CircleShape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (dotColor != null) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor))
        }
        Text(
            text = label,
            maxLines = 1,
            softWrap = false,
            style = TextStyle(
                fontFamily = Arcana.fonts.display,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                letterSpacing = 0.10.em,
                color = if (active) Stone else Ink,
            ),
        )
    }
}

@Composable
private fun ClassRow(c: ClassItem) {
    val sc = studioColor(c.studio)
    val fill = ((c.total - c.spots).toFloat() / c.total).coerceIn(0f, 1f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Time column — widthIn so the time never wraps.
        Column(modifier = Modifier.widthIn(min = 64.dp).wrapContentWidth(Alignment.Start)) {
            Text(
                text = c.time,
                maxLines = 1,
                softWrap = false,
                style = TextStyle(
                    fontFamily = Arcana.fonts.display,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    letterSpacing = (-0.01).em,
                    color = Ink,
                ),
            )
            Spacer(Modifier.height(4.dp))
            Overline(text = "${c.dur}min", size = 10, color = Ash)
        }
        // Studio color bar
        Box(
            Modifier
                .width(4.dp)
                .height(64.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (c.full) sc.copy(alpha = 0.35f) else sc)
        )
        // Class info
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Overline(
                    text = c.studio,
                    size = 10,
                    color = if (c.studio == "RISE") MossLight else sc,
                )
                Box(Modifier.size(4.dp).clip(CircleShape).background(Ash2))
                Overline(text = c.instructor, size = 10, color = Ash)
            }
            Spacer(Modifier.height(4.dp))
            BodyText(
                text = c.name,
                size = 16,
                color = if (c.full) Ash else Ink,
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
                                    c.scarce -> Warning
                                    c.full -> Ash2
                                    else -> MossLight
                                }
                            )
                    )
                }
                Overline(
                    text = when {
                        c.full -> "FULL · WAITLIST"
                        c.scarce -> "${c.spots} LEFT"
                        else -> "${c.spots} / ${c.total} OPEN"
                    },
                    size = 10,
                    color = if (c.scarce) Warning else Ash,
                )
            }
        }
        // CTA
        when {
            c.booked -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                StrokeIcon(ArcanaIcons.Check, size = 24.dp, tint = Moss)
                Overline(text = "Booked", size = 10, color = Moss)
            }
            c.full -> Box(
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
            else -> IconCircle(
                icon = ArcanaIcons.ArrowRight,
                diameter = 36,
                iconSize = 16,
                background = Ink,
                contentColor = Stone,
            )
        }
    }
}
