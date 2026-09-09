package org.arcana.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.arcana.mobile.theme.ArcanaShapes
import org.arcana.mobile.theme.Moss
import org.arcana.mobile.theme.Stone

/**
 * An applied-filter chip: a Moss pill with the [label] and a circular × that
 * calls [onRemove] — the "bubble with an x" pattern from the web waitlist
 * MultiSelect. Used in the schedule filter chip rail for the Time + Modality
 * overlays.
 */
@Composable
fun FilterChip(
    label: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val removeSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .controlShadow(ArcanaShapes.Pill)
            .clip(ArcanaShapes.Pill)
            .background(Moss)
            .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BodyText(text = label, size = 12, color = Stone)
        Box(
            modifier = Modifier
                .size(18.dp)
                .pressable(removeSource, pressedScale = 0.9f)
                .clip(CircleShape)
                .background(Stone.copy(alpha = 0.20f))
                .clickable(interactionSource = removeSource, indication = null, onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            StrokeIcon(
                ArcanaIcons.Close,
                size = 11.dp,
                tint = Stone,
                contentDescription = "Remove $label filter",
            )
        }
    }
}

/** Minimal wrapping flow layout (avoids the experimental-FlowRow opt-in). Chips
 *  flow left-to-right and wrap to new rows as needed. */
@Composable
fun FlowChipRow(
    modifier: Modifier = Modifier,
    horizontalSpacing: Dp = 8.dp,
    verticalSpacing: Dp = 8.dp,
    content: @Composable () -> Unit,
) {
    androidx.compose.ui.layout.Layout(content = content, modifier = modifier) { measurables, constraints ->
        val hGap = horizontalSpacing.roundToPx()
        val vGap = verticalSpacing.roundToPx()
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
        var x = 0
        var y = 0
        var rowH = 0
        val positions = placeables.map { p ->
            if (x + p.width > constraints.maxWidth && x > 0) {
                x = 0
                y += rowH + vGap
                rowH = 0
            }
            val pos = x to y
            x += p.width + hGap
            rowH = maxOf(rowH, p.height)
            pos
        }
        layout(constraints.maxWidth, y + rowH) {
            placeables.forEachIndexed { i, p -> p.place(positions[i].first, positions[i].second) }
        }
    }
}
