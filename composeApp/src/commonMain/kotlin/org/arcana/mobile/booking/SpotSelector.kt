package org.arcana.mobile.booking

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.arcana.mobile.data.SpotDto

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
internal fun shouldUseMap(spots: List<SpotDto>, selectionMode: String): Boolean {
    if (selectionMode != "grid") return false
    if (spots.isEmpty()) return false
    val withCoords = spots.count { it.positionX != null && it.positionY != null }
    return withCoords >= spots.size * MIN_COORD_FRACTION
}

/**
 * Chooses the spot-selection UI: the visual room map when the data supports it,
 * else the chip row. Drop-in replacement for [SpotPicker] with one extra
 * argument (the template's spot-selection mode).
 */
@Composable
fun SpotSelector(
    spots: List<SpotDto>,
    selectionMode: String,
    selected: SpotDto?,
    onSelect: (SpotDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (shouldUseMap(spots, selectionMode)) {
        SpotMap(spots = spots, selected = selected, onSelect = onSelect, modifier = modifier)
    } else {
        SpotPicker(spots = spots, selected = selected, onSelect = onSelect, modifier = modifier)
    }
}
