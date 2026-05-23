package org.arcana.mobile.studios

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.arcana.mobile.theme.Arcana
import org.arcana.mobile.theme.Ash
import org.arcana.mobile.theme.Ash2
import org.arcana.mobile.theme.Ink
import org.arcana.mobile.theme.Lime
import org.arcana.mobile.theme.Mist
import org.arcana.mobile.theme.Mist2
import org.arcana.mobile.theme.Moss
import org.arcana.mobile.theme.Paper
import org.arcana.mobile.theme.Stone
import org.arcana.mobile.theme.StoneAlpha55
import org.arcana.mobile.theme.StoneAlpha65
import org.arcana.mobile.ui.AccentText
import org.arcana.mobile.ui.ArcanaIcons
import org.arcana.mobile.ui.BodyText
import org.arcana.mobile.ui.Display
import org.arcana.mobile.ui.DotField
import org.arcana.mobile.ui.IconCircle
import org.arcana.mobile.ui.Overline
import org.arcana.mobile.ui.PrimaryCta
import org.arcana.mobile.ui.StrokeIcon
import org.arcana.mobile.ui.safeContentPadding
import org.arcana.mobile.ui.safeBottomBarPadding

private data class Studio(
    val id: String,
    val name: String,
    val city: String,
    val distance: String,
    val tag: String,
    val classes: String,
)

private val ALL_STUDIOS = listOf(
    Studio("form", "FORM", "Tribeca", "0.4 mi", "Reformer & mat", "32 / wk"),
    Studio("rise", "RISE", "Venice", "1.1 mi", "Boxing & conditioning", "28 / wk"),
    Studio("apex", "APEX", "Marylebone", "2.6 mi", "Strength & lift", "24 / wk"),
    Studio("core", "CORE", "Dumbo", "3.2 mi", "Mat & mobility", "36 / wk"),
    Studio("pace", "PACE", "Williamsburg", "4.5 mi", "Run · row · ride", "30 / wk"),
    Studio("arc", "ARC", "Soho", "5.1 mi", "Strength", "22 / wk"),
)

private const val MAX_PICKS = 3

/**
 * Studio selection flow — pick three studios, locked for the month.
 * Launched from Profile (and at first sign-in). [onClose] dismisses it.
 */
@Composable
fun StudioSelectionScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val picked = remember { mutableStateListOf("form", "rise") }
    val count = picked.size
    val complete = count == MAX_PICKS

    Box(modifier = modifier.fillMaxSize().background(Stone)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeContentPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 128.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconCircle(
                    icon = ArcanaIcons.Close,
                    diameter = 36,
                    iconSize = 16,
                    borderColor = Mist,
                    contentColor = Ink,
                    onClick = onClose,
                )
                Overline(text = "Step 02 of 03", size = 10, color = Moss)
                Overline(
                    text = "Skip",
                    size = 10,
                    color = Ash2,
                    modifier = Modifier.clickable(onClick = onClose),
                )
            }

            // Title
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Overline(text = "Your network", color = Moss)
                Display(text = "Pick\nthree\nrooms.", size = 48, color = Ink)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AccentText(text = "Locked for the month.", size = 18, color = Ash)
                    AccentText(text = "One swap per cycle.", size = 18, color = Moss)
                }
            }

            // Progress — 3-slot scoreboard
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                (1..MAX_PICKS).forEach { i ->
                    ProgressCell(index = i, filled = i <= count, modifier = Modifier.weight(1f))
                }
            }

            // Studio list
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ALL_STUDIOS.forEach { studio ->
                    val chosen = studio.id in picked
                    SelectableStudioCard(
                        studio = studio,
                        chosen = chosen,
                        onToggle = {
                            if (chosen) picked.remove(studio.id)
                            else if (count < MAX_PICKS) picked.add(studio.id)
                        },
                    )
                }
            }
        }

        // Sticky CTA — fades the bottom of the scroll under the bar.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Stone, Stone))
                )
                .safeBottomBarPadding()
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp),
        ) {
            PrimaryCta(
                label = if (complete) "Confirm selection" else "Pick ${MAX_PICKS - count} more",
                onClick = { if (complete) onClose() },
                enabled = complete,
                trailing = if (complete) null else {
                    { Overline(text = "$count / $MAX_PICKS", size = 12, color = StoneAlpha55) }
                },
            )
        }
    }
}

@Composable
private fun ProgressCell(index: Int, filled: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (filled) Moss else Mist2),
        contentAlignment = Alignment.Center,
    ) {
        if (filled) {
            DotField(modifier = Modifier.matchParentSize(), color = Lime, alpha = 0.12f, spacing = 12)
        }
        Text(
            text = "0$index",
            style = TextStyle(
                fontFamily = Arcana.fonts.display,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                letterSpacing = (-0.01).em,
                color = if (filled) Lime else Ash2,
            ),
        )
    }
}

@Composable
private fun SelectableStudioCard(
    studio: Studio,
    chosen: Boolean,
    onToggle: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (chosen) Ink else Paper)
            .border(1.dp, if (chosen) Ink else Mist, RoundedCornerShape(16.dp))
            .clickable(onClick = onToggle),
    ) {
        if (chosen) {
            DotField(modifier = Modifier.matchParentSize(), color = Lime, alpha = 0.08f, spacing = 14)
        }
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Check / empty marker
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .then(
                        if (chosen) Modifier.background(Lime)
                        else Modifier.border(2.dp, Mist, CircleShape)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (chosen) {
                    StrokeIcon(ArcanaIcons.Check, size = 18.dp, tint = Ink)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = studio.name,
                        style = TextStyle(
                            fontFamily = Arcana.fonts.display,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            letterSpacing = (-0.02).em,
                            color = if (chosen) Stone else Ink,
                        ),
                    )
                    Overline(
                        text = studio.city,
                        size = 10,
                        color = if (chosen) StoneAlpha55 else Ash,
                    )
                }
                Spacer(Modifier.height(4.dp))
                BodyText(
                    text = "${studio.tag} · ${studio.classes}",
                    size = 12,
                    color = if (chosen) StoneAlpha65 else Ash,
                )
            }
            Overline(
                text = studio.distance,
                size = 10,
                color = if (chosen) Lime else Moss,
            )
        }
    }
}
