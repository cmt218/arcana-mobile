package org.arcana.mobile.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.arcana.mobile.data.SpotDto
import org.arcana.mobile.theme.BurntNectar
import org.arcana.mobile.theme.Mist
import org.arcana.mobile.theme.Stone
import org.arcana.mobile.theme.Wood
import org.arcana.mobile.ui.Overline

/** v1 spot picker: a wrapping row of selectable spot chips. Renders only
 *  `status == "available"` spots plus the current selection. A precise grid
 *  layout keyed off template.layout_metadata is a future enhancement. */
@Composable
fun SpotPicker(
    spots: List<SpotDto>,
    selected: SpotDto?,
    onSelect: (SpotDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pickable = spots.filter { it.status == "available" || it.id == selected?.id }
    FlowRowCompat(modifier = modifier, spacing = 8.dp) {
        pickable.forEach { spot ->
            val isSel = spot.id == selected?.id
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .then(if (isSel) Modifier.background(BurntNectar) else Modifier.border(1.dp, Mist, RoundedCornerShape(8.dp)))
                    .clickable { onSelect(spot) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Overline(spot.label, size = 12, color = if (isSel) Stone else Wood)
            }
        }
    }
}

/** Minimal wrapping flow layout (avoids the experimental-FlowRow opt-in churn). */
@Composable
private fun FlowRowCompat(modifier: Modifier = Modifier, spacing: Dp, content: @Composable () -> Unit) {
    androidx.compose.ui.layout.Layout(content = content, modifier = modifier) { measurables, constraints ->
        val gap = spacing.roundToPx()
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
        var x = 0; var y = 0; var rowH = 0
        val positions = placeables.map { p ->
            if (x + p.width > constraints.maxWidth && x > 0) { x = 0; y += rowH + gap; rowH = 0 }
            val pos = x to y; x += p.width + gap; rowH = maxOf(rowH, p.height); pos
        }
        layout(constraints.maxWidth, y + rowH) {
            placeables.forEachIndexed { i, p -> p.place(positions[i].first, positions[i].second) }
        }
    }
}
