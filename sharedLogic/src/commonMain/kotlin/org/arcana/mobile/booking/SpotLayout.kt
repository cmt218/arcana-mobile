package org.arcana.mobile.booking

import org.arcana.mobile.data.SpotDto

/*
 * Pure spot-map layout math, shared by the SpotMap/SpotMapFullScreen
 * composables (:sharedUI, same package) and their unit tests.
 */

/** A spot placed in unit space: [nx],[ny] ∈ [0,1] after bbox-normalization. */
data class NormalizedSpot(val spot: SpotDto, val nx: Float, val ny: Float)

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

