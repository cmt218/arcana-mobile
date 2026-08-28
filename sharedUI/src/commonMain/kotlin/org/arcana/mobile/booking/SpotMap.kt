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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.arcana.mobile.data.SpotDto
import org.arcana.mobile.theme.Ash
import org.arcana.mobile.theme.Mist
import org.arcana.mobile.theme.Moss
import org.arcana.mobile.theme.Paper
import org.arcana.mobile.theme.Plate
import org.arcana.mobile.theme.Stone
import org.arcana.mobile.theme.Wood
import org.arcana.mobile.ui.Caption
import org.arcana.mobile.ui.captionStyle
import kotlin.math.max

// Pure layout core (NormalizedSpot/SpotLayout/normalizeSpots/spotContentSize/
// maxSpotDot) lives in :sharedLogic booking/SpotLayout.kt — same package, so
// references below resolve unqualified.

/** Padding a dot keeps inside its own box: the drawn circle is twice this
 *  smaller than the box the layout math places. */
internal val DOT_INSET = 2.dp
/** Visible space to keep between two drawn circles in the inline map. */
private val MIN_CLEAR = 2.dp
private val MIN_DOT = 14.dp
private val MAX_DOT = 40.dp
/** Floor for a shrunk-to-fit label; below this the map is the wrong surface. */
internal const val LABEL_MIN_SIZE = 7

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
        // The map sits on its own white plate: the taken fill is nearly invisible
        // against the sheet's Stone. Padding is vertical only so the map keeps the
        // full sheet width and dot sizing is unaffected.
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Plate)
                .padding(vertical = 12.dp),
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val wDp = maxWidth
                val hDp = (wDp / layout.bboxAspect).coerceIn(96.dp, 300.dp)
                // Start from the per-column / per-row spacing, clamped so a sparse room
                // isn't cartoonishly large and a dense one stays legible. That estimate
                // assumes an even grid and real rooms are not, so shrink it to a dot the
                // closest actual pair can still clear.
                val even = minOf(wDp / max(layout.cols, 1), hDp / max(layout.rows, 1))
                    .coerceIn(MIN_DOT, MAX_DOT)
                val cell = remember(layout, wDp, hDp, even) {
                    maxSpotDot(
                        layout, wDp.value, hDp.value,
                        drawnInset = DOT_INSET.value, clear = MIN_CLEAR.value,
                        minDot = MIN_DOT.value, maxDot = even.value,
                    ).dp
                }
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
        else -> Mist // taken / reserved / blocked
    }
    val labelColor = when {
        selected -> Stone
        selectable -> Wood
        // Ash, not Ash2: the taken fill is dark enough that the lighter tone
        // drops the station number to 1.9:1 and it stops being readable.
        else -> Ash
    }
    Box(
        modifier = Modifier
            .size(size)
            .padding(DOT_INSET) // micro-gap between adjacent dots
            .clip(CircleShape)
            .background(fill)
            .then(if (selectable && !selected) Modifier.border(1.dp, Mist, CircleShape) else Modifier)
            .then(if (onSelect != null) Modifier.clickable(onClick = onSelect) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (showLabel) {
            // Studios name their own stations and some names ("10,BENCH") do not
            // fit a circle at the design size. Only those shrink; a label that
            // already fits renders untouched at [labelSize].
            val measurer = rememberTextMeasurer(cacheSize = 2)
            val natural = measurer.measure(
                label, captionStyle(labelSize), softWrap = false,
            ).size.width.toFloat()
            val inner = with(LocalDensity.current) { (size - DOT_INSET * 2).toPx() }
            Caption(
                label,
                size = fitLabelSize(natural, inner, labelSize, LABEL_MIN_SIZE),
                color = labelColor,
            )
        }
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
