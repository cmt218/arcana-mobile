package org.arcana.mobile.booking

import org.arcana.mobile.data.SpotDto
import kotlin.math.min
import kotlin.math.sqrt

/*
 * Pure spot-map layout math, shared by the SpotMap/SpotMapFullScreen
 * composables (:sharedUI, same package) and their unit tests.
 */

/** A spot placed in unit space: [nx],[ny] ∈ [0,1] after bbox-normalization. */
data class NormalizedSpot(val spot: SpotDto, val nx: Float, val ny: Float)

/** A spot placed in a concrete box: [cx],[cy] is its centre in that box's units. */
data class PlacedSpot(val spot: SpotDto, val cx: Float, val cy: Float)

/**
 * Result of laying spots into unit space, plus the metadata the composable
 * needs to size the map: the spot bounding-box aspect ratio (w/h) and the
 * distinct row/column counts (for cell sizing + degenerate handling).
 */
data class SpotLayout(
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
fun normalizeSpots(spots: List<SpotDto>, marginFraction: Float = 0.05f): SpotLayout {
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

/** Clear space between the two closest dots, as a fraction of a dot diameter. */
const val SPOT_GAP_FRACTION = 0.05f

/**
 * Where each dot's centre lands inside a [boxW]×[boxH] box. Mirrors the scatter
 * layout exactly: a dot is positioned by its top-left within `box - dot`, so the
 * span the normalized coordinates actually map onto is one dot narrower and
 * shorter than the box. Hit-testing and the spacing math both go through this so
 * they cannot drift apart.
 */
fun spotCenters(layout: SpotLayout, boxW: Float, boxH: Float, dot: Float): List<PlacedSpot> =
    layout.spots.map {
        PlacedSpot(it.spot, it.nx * (boxW - dot) + dot / 2f, it.ny * (boxH - dot) + dot / 2f)
    }

/** Closest centre-to-centre distance once the layout is drawn at this size. */
fun closestSpotGap(layout: SpotLayout, boxW: Float, boxH: Float, dot: Float): Float {
    val p = spotCenters(layout, boxW, boxH, dot)
    var best = Float.MAX_VALUE
    for (i in p.indices) for (j in i + 1 until p.size) {
        val dx = p[i].cx - p[j].cx
        val dy = p[i].cy - p[j].cy
        val d = sqrt(dx * dx + dy * dy)
        if (d < best) best = d
    }
    return if (best == Float.MAX_VALUE) Float.MAX_VALUE else best
}

/** Closest pair in unit space, x scaled by the aspect so both axes compare in
 *  the room's real proportions. Two spots on different rows are genuinely far
 *  apart even when they share an x, and must not drive the sizing. */
private fun closestUnitGap(layout: SpotLayout): Float {
    val p = layout.spots
    var best = Float.MAX_VALUE
    for (i in p.indices) for (j in i + 1 until p.size) {
        val dx = (p[i].nx - p[j].nx) * layout.bboxAspect
        val dy = p[i].ny - p[j].ny
        val d = sqrt(dx * dx + dy * dy)
        if (d > 1e-4f && d < best) best = d
    }
    return if (best == Float.MAX_VALUE) 1f else best // 0 or 1 spot
}

/**
 * Canvas size for the full-screen map: preserves the room's proportions while
 * placing the closest pair of fixed-size dots [SPOT_GAP_FRACTION] of a diameter
 * apart. Solved on the CENTRE span and the dot added back afterwards, because
 * that span — not the outer box — is what the normalized coordinates address.
 */
fun spotContentSize(layout: SpotLayout, dot: Float, maxSide: Float = 12000f): Pair<Float, Float> {
    val aspect = layout.bboxAspect
    var innerH = dot * (1f + SPOT_GAP_FRACTION) / closestUnitGap(layout)
    var innerW = innerH * aspect
    val room = (maxSide - dot).coerceAtLeast(0f)
    if (innerW > room) { innerW = room; innerH = innerW / aspect }
    if (innerH > room) { innerH = room; innerW = innerH * aspect }
    return (innerW + dot) to (innerH + dot)
}

/**
 * Inverse of [spotContentSize] for the inline map, whose canvas is fixed by the
 * sheet width: the largest dot ≤ [maxDot] that still leaves [clear] of visible
 * space between the drawn circles. [drawnInset] is the padding each dot keeps
 * inside its own box, so the circle is `dot - 2 * drawnInset` across.
 *
 * Bisection rather than a closed form: shrinking the dot widens the placement
 * span on both axes at once, and which pair is closest can change as it does.
 */
fun maxSpotDot(
    layout: SpotLayout,
    boxW: Float,
    boxH: Float,
    drawnInset: Float,
    clear: Float,
    minDot: Float,
    maxDot: Float,
): Float {
    fun fits(dot: Float) = closestSpotGap(layout, boxW, boxH, dot) >= dot - 2f * drawnInset + clear
    val hi0 = min(maxDot, min(boxW, boxH))
    if (hi0 <= minDot || fits(hi0)) return hi0.coerceAtLeast(minDot)
    if (!fits(minDot)) return minDot
    var lo = minDot
    var hi = hi0
    repeat(20) {
        val mid = (lo + hi) / 2f
        if (fits(mid)) lo = mid else hi = mid
    }
    return lo
}

/**
 * Largest size (sp) at which a label whose natural width is [naturalPx] at
 * [atSize] still fits [availablePx]. Glyph advances and em-based tracking both
 * scale with the font size, so one measurement settles it without a search.
 *
 * Do NOT go back to Compose's `TextAutoSize`: it decides a size fits by asking
 * whether the layout ellipsized, and `SkiaParagraph.isLineEllipsized` returns a
 * hardcoded false, so on iOS it never shrinks anything.
 */
fun fitLabelSize(naturalPx: Float, availablePx: Float, atSize: Int, minSize: Int): Int {
    if (naturalPx <= 0f || availablePx <= 0f || naturalPx <= availablePx) return atSize
    // Trim a hair off before flooring: advances round per glyph, so a size that
    // scales to exactly the available width can still measure a pixel over.
    val fitted = (atSize * (availablePx / naturalPx) * 0.97f).toInt()
    return fitted.coerceIn(minOf(minSize, atSize), atSize)
}

// Require most spots to actually carry coordinates before drawing a map (a
// partial map would mislead about where the open spots are).
private const val MIN_COORD_FRACTION = 0.8

/**
 * Pure predicate (unit-tested): use the visual [SpotMap] only for grid-mode
 * studios whose spots carry coordinates to render faithfully; otherwise fall
 * back to the proven chip [SpotPicker].
 *
 * Only `grid` studios (Mariana Tek) reach the map — Arketa reports `list`
 * because its API returns just the open spots. Mariana Tek returns the FULL
 * room (taken + available), so the map is shown regardless of how many spots
 * are still open; a full class simply never opens the picker.
 */
fun shouldUseMap(spots: List<SpotDto>, selectionMode: String): Boolean {
    if (selectionMode != "grid") return false
    if (spots.isEmpty()) return false
    val withCoords = spots.count { it.positionX != null && it.positionY != null }
    return withCoords >= spots.size * MIN_COORD_FRACTION
}

