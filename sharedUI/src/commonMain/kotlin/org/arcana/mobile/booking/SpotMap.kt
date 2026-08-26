package org.arcana.mobile.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.arcana.mobile.data.SpotDto
import org.arcana.mobile.theme.Ash2
import org.arcana.mobile.theme.Mist
import org.arcana.mobile.theme.Mist2
import org.arcana.mobile.theme.Moss
import org.arcana.mobile.theme.Paper
import org.arcana.mobile.theme.Stone
import org.arcana.mobile.theme.Wood
import org.arcana.mobile.ui.Caption
import kotlin.math.max

// Pure layout core (NormalizedSpot/SpotLayout/normalizeSpots) lives in :sharedLogic
// booking/SpotLayout.kt — same package, so references below resolve unqualified.


/**
 * Visual room-layout spot picker: draws each spot as a dot at its true relative
 * position in the room, colored by availability, with the current pick in Burnt
 * Nectar (matching [SpotPicker]'s selection language). Taken spots are muted and
 * inert. If no spot carries coordinates it falls back to the chip [SpotPicker].
 *
 * The inline map is fit-to-width and static (no pan/zoom) — the quick picker. The
 * expand affordance (rendered in the sheet header) opens [SpotMapFullScreen], the
 * comprehensive, zoomable view.
 */
@Composable
fun SpotMap(
    spots: List<SpotDto>,
    selected: SpotDto?,
    onSelect: (SpotDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = remember(spots) { normalizeSpots(spots) }
    if (layout.spots.isEmpty()) {
        // Defensive: SpotSelector already gates on coordinate coverage, but if a
        // map render is ever requested for coordinate-less data, degrade to chips.
        SpotPicker(spots = spots, selected = selected, onSelect = onSelect, modifier = modifier)
        return
    }

    Column(modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wDp = maxWidth
            val hDp = (wDp / layout.bboxAspect).coerceIn(96.dp, 300.dp)
            // Cell = the smaller of the per-column / per-row spacing, clamped so a
            // sparse room isn't cartoonishly large and a dense one stays legible.
            val cell = minOf(wDp / max(layout.cols, 1), hDp / max(layout.rows, 1))
                .coerceIn(14.dp, 40.dp)
            val showLabel = cell >= 26.dp

            SpotScatter(
                width = wDp,
                height = hDp,
                positions = layout.spots.map { Offset(it.nx, it.ny) },
            ) {
                layout.spots.forEach { ns ->
                    val s = ns.spot
                    val isSel = s.id == selected?.id
                    val selectable = s.status == "available" || isSel
                    SpotDot(
                        label = s.label,
                        selected = isSel,
                        selectable = selectable,
                        size = cell,
                        showLabel = showLabel,
                        labelSize = 10,
                        onSelect = if (selectable) ({ onSelect(s) }) else null,
                    )
                }
            }
        }
        if (selected != null) {
            Spacer(Modifier.height(8.dp))
            Caption("Spot ${selected.label}", size = 13, color = Moss)
        }
    }
}

/**
 * A single spot circle, shared by the inline map and [SpotMapFullScreen]:
 * Burnt Nectar when selected, outlined Paper when open, muted when taken. Pass
 * [onSelect] to make it tappable (the inline map); leave it null when the parent
 * handles taps (the zoomable full-screen map).
 */
@Composable
internal fun SpotDot(
    label: String,
    selected: Boolean,
    selectable: Boolean,
    size: Dp,
    showLabel: Boolean,
    labelSize: Int,
    onSelect: (() -> Unit)? = null,
) {
    val fill = when {
        selected -> Moss
        selectable -> Paper
        else -> Mist2 // taken / reserved / blocked
    }
    val labelColor = when {
        selected -> Stone
        selectable -> Wood
        else -> Ash2
    }
    Box(
        modifier = Modifier
            .size(size)
            .padding(2.dp) // micro-gap between adjacent dots
            .clip(CircleShape)
            .background(fill)
            .then(if (selectable && !selected) Modifier.border(1.dp, Mist, CircleShape) else Modifier)
            .then(if (onSelect != null) Modifier.clickable(onClick = onSelect) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (showLabel) Caption(label, size = labelSize, color = labelColor)
    }
}

/**
 * Fixed-size scatter layout: sizes itself to [width]×[height] and places each
 * equally-sized child at its normalized ([positions] nx,ny ∈ [0,1]), insetting
 * by the child's own size so dots never clip at the frame edge. Child order must
 * match [positions] order (the caller emits them in lockstep).
 */
@Composable
internal fun SpotScatter(
    width: Dp,
    height: Dp,
    positions: List<Offset>,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = Modifier.size(width, height)) { measurables, constraints ->
        val wPx = constraints.maxWidth
        val hPx = constraints.maxHeight
        val placeables = measurables.map {
            it.measure(Constraints(minWidth = 0, minHeight = 0, maxWidth = wPx, maxHeight = hPx))
        }
        layout(wPx, hPx) {
            placeables.forEachIndexed { i, p ->
                val availX = max(0, wPx - p.width)
                val availY = max(0, hPx - p.height)
                val x = (positions[i].x * availX).toInt().coerceIn(0, availX)
                val y = (positions[i].y * availY).toInt().coerceIn(0, availY)
                p.place(x, y)
            }
        }
    }
}
