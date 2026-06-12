package org.arcana.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import org.arcana.mobile.theme.Arcana
import org.arcana.mobile.theme.Ash
import org.arcana.mobile.theme.Ink
import org.arcana.mobile.theme.Lime
import org.arcana.mobile.theme.Mist
import org.arcana.mobile.theme.Moss
import org.arcana.mobile.theme.Paper
import org.arcana.mobile.theme.Stone
import org.arcana.mobile.theme.StoneAlpha55

/**
 * Display-friendly location label: the studio prefix is stripped so a row
 * reads "Williamsburg", not "YO BK Williamsburg" under the YO BK card. Shared
 * by the favorites manager and the schedule filter so both label identically.
 * (Title Case — distinct from `org.arcana.mobile.schedule.locationShortLabel`,
 * which uppercases for the row meta line.)
 */
fun studioLocationLabel(studioName: String, locationName: String): String {
    val raw = locationName.removePrefix(studioName).trim()
        .removePrefix("·").trim()
        .removePrefix("-").trim()
    return raw.ifEmpty { locationName }
}

/**
 * Expandable studio row used by the favorites manager and the schedule filter.
 * Tap model: the card body expands/collapses the location list; ONLY the check
 * circle selects/deselects the whole studio (the higher-consequence action gets
 * the deliberate, smaller target). Decoupled from any DTO — callers pass
 * primitives and render the location rows themselves below the card.
 *
 * @param name studio display name (rendered uppercased).
 * @param locationCount total locations under this studio.
 * @param chosen whole-studio selected.
 * @param expanded location list visible.
 * @param selectedLocationCount individually-selected locations (for the partial state).
 */
@Composable
fun StudioAccordionCard(
    name: String,
    locationCount: Int,
    chosen: Boolean,
    expanded: Boolean,
    selectedLocationCount: Int,
    onToggle: () -> Unit,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Some-but-not-all selection: individual locations are picked without the
    // whole studio — surfaced via a partial check ring + "N of M locations".
    val partial = !chosen && selectedLocationCount > 0
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (chosen) Ink else Paper)
            .border(1.dp, if (chosen) Ink else Mist, RoundedCornerShape(16.dp))
            .clickable(onClick = onToggleExpanded),
    ) {
        if (chosen) {
            DotField(modifier = Modifier.matchParentSize(), color = Lime, alpha = 0.08f, spacing = 14)
        }
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .then(
                            when {
                                chosen -> Modifier.background(Lime)
                                partial -> Modifier.border(2.dp, Lime, CircleShape)
                                else -> Modifier.border(2.dp, Mist, CircleShape)
                            }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (chosen) {
                        StrokeIcon(ArcanaIcons.Check, size = 18.dp, tint = Ink)
                    } else if (partial) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(Lime))
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name.uppercase(),
                    style = TextStyle(
                        fontFamily = Arcana.fonts.display,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        letterSpacing = (-0.02).em,
                        color = if (chosen) Stone else Ink,
                    ),
                )
                Spacer(Modifier.height(4.dp))
                val locationsWord = if (locationCount == 1) "location" else "locations"
                Overline(
                    text = if (partial) {
                        "$selectedLocationCount of $locationCount $locationsWord"
                    } else {
                        "$locationCount $locationsWord"
                    },
                    size = 10,
                    color = when {
                        chosen -> StoneAlpha55
                        partial -> Moss
                        else -> Ash
                    },
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onToggleExpanded),
                contentAlignment = Alignment.Center,
            ) {
                StrokeIcon(
                    icon = ArcanaIcons.ChevronDown,
                    size = 18.dp,
                    tint = if (chosen) Lime else Moss,
                    modifier = Modifier.rotate(if (expanded) 180f else 0f),
                )
            }
        }
    }
}

/**
 * Expanded location row. [implied] means the whole studio is selected — the
 * check renders at reduced opacity to hint that tapping narrows to just this
 * location.
 */
@Composable
fun StudioLocationRow(
    label: String,
    checked: Boolean,
    implied: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onTap)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val checkTint = if (implied) Lime.copy(alpha = 0.45f) else Lime
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .then(
                    if (checked) Modifier.background(checkTint)
                    else Modifier.border(2.dp, Mist, CircleShape)
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                StrokeIcon(ArcanaIcons.Check, size = 12.dp, tint = Ink)
            }
        }
        BodyText(text = label, size = 14, color = Ink)
    }
}
