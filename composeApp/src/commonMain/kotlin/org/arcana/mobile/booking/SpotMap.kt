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
import org.arcana.mobile.theme.BurntNectar
import org.arcana.mobile.theme.Mist
import org.arcana.mobile.theme.Mist2
import org.arcana.mobile.theme.Moss
import org.arcana.mobile.theme.Paper
import org.arcana.mobile.theme.Stone
import org.arcana.mobile.theme.Wood
import org.arcana.mobile.ui.Caption
import kotlin.math.max

// ─────────────────────────────────────────────────────────────────────────
// Pure core (no Compose usage below — exercised directly by commonTest).
// ─────────────────────────────────────────────────────────────────────────

/** A spot placed in unit space: [nx],[ny] ∈ [0,1] after bbox-normalization. */
internal data class NormalizedSpot(val spot: SpotDto, val nx: Float, val ny: Float)

/**
 * Result of laying spots into unit space, plus the metadata the composable
 * needs to size the map: the spot bounding-box aspect ratio (w/h) and the
 * distinct row/column counts (for cell sizing + degenerate handling).
 */
internal data class SpotLayout(
    val spots: List<NormalizedSpot>,
    val bboxAspect: Float,
    val rows: Int,
    val cols: Int,
)

private const val EPS = 1e-6
// A single-row room draws as a short wide band rather than a hairline / a tall
// sliver; a single-column room is its transpose.
private const val WIDE_STRIP_ASPECT = 6f

/**
 * Normalize raw spot coordinates into unit space off their bounding box. This
 * single path handles BOTH coordinate systems the server emits:
 *  - Mariana Tek: absolute grid units (e.g. x 1–23, y 0–5).
 *  - Arketa: fractional 0–1 (`xPercent`/`yPercent`) in the same fields.
 * Because we normalize off the observed min/max, we need no knowledge of real
 * room dimensions. Aspect is derived from the actual data span so a wide-shallow
 * room (Barry's ≈ 22×5) renders wide, not square.
 */
internal fun normalizeSpots(spots: List<SpotDto>, marginFraction: Float = 0.05f): SpotLayout {
    val placed = spots.filter { it.positionX != null && it.positionY != null }
    if (placed.isEmpty()) return SpotLayout(emptyList(), 1f, 0, 0)

    val xs = placed.map { it.positionX!! }
    val ys = placed.map { it.positionY!! }
    val minX = xs.min(); val maxX = xs.max()
    val minY = ys.min(); val maxY = ys.max()
    val spanX = maxX - minX
    val spanY = maxY - minY
    val hasX = spanX > EPS
    val hasY = spanY > EPS

    val normalized = placed.map { s ->
        // Degenerate axis (all-equal) → center at 0.5 instead of dividing by zero.
        val rawX = if (hasX) ((s.positionX!! - minX) / spanX).toFloat() else 0.5f
        val rawY = if (hasY) ((s.positionY!! - minY) / spanY).toFloat() else 0.5f
        // Inset a live axis by the margin so edge spots aren't flush to the frame.
        val nx = if (hasX) marginFraction + rawX * (1 - 2 * marginFraction) else rawX
        val ny = if (hasY) marginFraction + rawY * (1 - 2 * marginFraction) else rawY
        NormalizedSpot(s, nx, ny)
    }

    val bboxAspect = when {
        hasX && hasY -> (spanX / spanY).toFloat().coerceIn(0.25f, 8f)
        hasX -> WIDE_STRIP_ASPECT           // single row → wide band
        hasY -> 1f / WIDE_STRIP_ASPECT       // single column → tall band
        else -> 1f                            // single spot → square
    }

    return SpotLayout(
        spots = normalized,
        bboxAspect = bboxAspect,
        rows = ys.distinct().size,
        cols = xs.distinct().size,
    )
}

// ─────────────────────────────────────────────────────────────────────────
// Composables
// ─────────────────────────────────────────────────────────────────────────

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
        selected -> BurntNectar
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
