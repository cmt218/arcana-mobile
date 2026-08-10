package org.arcana.mobile.booking

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.arcana.mobile.data.SpotDto

// shouldUseMap lives in :sharedLogic booking/SpotLayout.kt (same package).

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
