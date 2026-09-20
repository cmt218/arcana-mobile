package org.arcana.mobile.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.arcana.mobile.theme.Arcana
import org.arcana.mobile.theme.ArcanaShapes
import org.arcana.mobile.theme.Dur
import org.arcana.mobile.theme.Ink
import org.arcana.mobile.theme.Moss
import org.arcana.mobile.theme.MossLight
import org.arcana.mobile.theme.Stone

// Marks and words share one content height, so a row of marks alone stands as
// tall as a row of words beside it.
private val CONTENT_HEIGHT = 20.dp

/**
 * One answer from a short list: equal-width pills, outlined until picked, the
 * pick filled Moss, so the only filled shapes are the member's answers.
 * Sentence-case labels, so no optical caps nudge. Radio semantics.
 */
@Composable
fun ChoicePills(
    options: List<String>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** A mark before the label (an emoji, an icon), by option index. */
    leading: (@Composable (Int) -> Unit)? = null,
    /** False draws the [leading] mark alone; the label is still what a screen reader hears. */
    showLabels: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.6f),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEachIndexed { index, label ->
            val source = remember { MutableInteractionSource() }
            val selected = index == selectedIndex
            val fill by animateColorAsState(if (selected) Moss else Color.Transparent, tween(Dur.Short), label = "choiceFill")
            val edge by animateColorAsState(if (selected) Moss else MossLight, tween(Dur.Short), label = "choiceEdge")
            val ink by animateColorAsState(if (selected) Stone else Ink, tween(Dur.Short), label = "choiceInk")
            Box(
                modifier = Modifier
                    .weight(1f)
                    .pressable(source, enabled = enabled, pressedScale = 0.97f)
                    .clip(ArcanaShapes.Pill)
                    .background(fill)
                    .border(1.dp, edge, ArcanaShapes.Pill)
                    .clickable(
                        enabled = enabled, interactionSource = source, indication = null,
                        role = Role.RadioButton, onClick = { onSelect(index) },
                    )
                    .semantics {
                        this.selected = selected
                        if (!showLabels) contentDescription = label
                    }
                    .padding(horizontal = 8.dp, vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    modifier = Modifier.heightIn(min = CONTENT_HEIGHT),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    leading?.invoke(index)
                    if (showLabels) Text(
                        text = label,
                        maxLines = 1,
                        softWrap = false,
                        style = TextStyle(
                            fontFamily = Arcana.fonts.body,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = ink,
                        ),
                    )
                }
            }
        }
    }
}

/** A 1 to [max] scale as numbered dots; every dot up to the pick fills. */
@Composable
fun ScoreDots(
    value: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    max: Int = 5,
    enabled: Boolean = true,
    label: String = "",
) {
    Row(
        modifier = modifier.alpha(if (enabled) 1f else 0.6f),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (score in 1..max) {
            val source = remember { MutableInteractionSource() }
            val filled = value != null && score <= value
            val selected = value == score
            val fill by animateColorAsState(if (filled) Moss else Color.Transparent, tween(Dur.Short), label = "scoreFill")
            val edge by animateColorAsState(if (filled) Moss else MossLight, tween(Dur.Short), label = "scoreEdge")
            val ink by animateColorAsState(if (filled) Stone else Ink, tween(Dur.Short), label = "scoreInk")
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .pressable(source, enabled = enabled, pressedScale = 0.9f)
                    .clip(CircleShape)
                    .background(fill)
                    .border(1.dp, edge, CircleShape)
                    .clickable(
                        enabled = enabled, interactionSource = source, indication = null,
                        role = Role.RadioButton, onClick = { onSelect(score) },
                    )
                    .semantics {
                        this.selected = selected
                        contentDescription = if (label.isBlank()) "$score of $max" else "$label $score of $max"
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = score.toString(),
                    // No caps nudge: DM Sans figures measure centred as laid out
                    // (tools/regression/measure_centering.py, under 0.5pt).
                    style = TextStyle(
                        fontFamily = Arcana.fonts.body,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = ink,
                    ),
                )
            }
        }
    }
}
