package org.arcana.mobile.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.arcana.mobile.data.SpotDto
import org.arcana.mobile.theme.ArcanaTheme
import org.arcana.mobile.theme.Stone

/**
 * Preview fixture mirroring the verified Barry's Bootcamp Arlington layout pulled
 * live from the Mariana Tek customer API: a wide, shallow room — treadmills on
 * the back row (y=0), two floor rows in front (y=3, y=5), x spanning 1–23, with a
 * mix of Floor / Double Floor / Treadmill tiers and available/taken statuses so
 * every SpotNode state renders.
 */
private fun barrysSampleSpots(): List<SpotDto> {
    var id = 1
    val out = mutableListOf<SpotDto>()
    // Back row: treadmills, alternating taken/available.
    for (x in 1..23 step 2) {
        out += SpotDto(
            id = id++, label = "T${x}", positionX = x.toDouble(), positionY = 0.0,
            tier = "Treadmill", status = if (x % 4 == 1) "booked" else "available",
        )
    }
    // Middle floor row.
    for (x in 2..22 step 2) {
        out += SpotDto(
            id = id++, label = "F${x}", positionX = x.toDouble(), positionY = 3.0,
            tier = if (x % 6 == 0) "Double Floor" else "Floor",
            status = if (x % 3 == 0) "booked" else "available",
        )
    }
    // Front floor row.
    for (x in 2..22 step 2) {
        out += SpotDto(
            id = id++, label = "G${x}", positionX = x.toDouble(), positionY = 5.0,
            tier = if (x % 8 == 0) "Double Floor" else "Floor",
            status = if (x % 5 == 0) "blocked" else "available",
        )
    }
    return out
}

@Preview(widthDp = 360)
@Composable
private fun SpotMapBarrysPreview() {
    val spots = remember { barrysSampleSpots() }
    var selected by remember { mutableStateOf<SpotDto?>(null) }
    ArcanaTheme {
        SpotMap(
            spots = spots,
            selected = selected,
            onSelect = { selected = it },
            modifier = Modifier.background(Stone).fillMaxWidth().padding(16.dp),
        )
    }
}

@Preview(widthDp = 360)
@Composable
private fun SpotMapMidSelectionPreview() {
    val spots = remember { barrysSampleSpots() }
    val selected = remember { spots.first { it.positionY == 3.0 && it.status == "available" } }
    ArcanaTheme {
        SpotMap(
            spots = spots,
            selected = selected,
            onSelect = {},
            modifier = Modifier.background(Stone).fillMaxWidth().padding(16.dp),
        )
    }
}

@Preview(widthDp = 360)
@Composable
private fun SpotMapTreadmillSelectionPreview() {
    val spots = remember { barrysSampleSpots() }
    val selected = remember { spots.first { it.positionY == 0.0 && it.status == "available" } }
    ArcanaTheme {
        SpotMap(
            spots = spots,
            selected = selected,
            onSelect = {},
            modifier = Modifier.background(Stone).fillMaxWidth().padding(16.dp),
        )
    }
}

@Preview(widthDp = 360)
@Composable
private fun SpotMapSingleRowPreview() {
    // Degenerate single-row room → short wide band, dots vertically centered.
    val spots = remember {
        (1..12).map { SpotDto(id = it, label = "$it", positionX = it.toDouble(), positionY = 1.0) }
    }
    var selected by remember { mutableStateOf<SpotDto?>(null) }
    ArcanaTheme {
        SpotMap(
            spots = spots,
            selected = selected,
            onSelect = { selected = it },
            modifier = Modifier.background(Stone).fillMaxWidth().padding(16.dp),
        )
    }
}

@Preview(widthDp = 360, heightDp = 780)
@Composable
private fun SpotMapFullScreenPreview() {
    val spots = remember { barrysSampleSpots() }
    var selected by remember { mutableStateOf<SpotDto?>(null) }
    ArcanaTheme {
        SpotMapFullScreen(spots = spots, selected = selected, onSelect = { selected = it }, onClose = {})
    }
}

@Preview(widthDp = 360, heightDp = 780)
@Composable
private fun SpotMapFullScreenSelectedPreview() {
    val spots = remember { barrysSampleSpots() }
    val selected = remember { spots.first { it.positionY == 3.0 && it.status == "available" } }
    ArcanaTheme {
        SpotMapFullScreen(spots = spots, selected = selected, onSelect = {}, onClose = {})
    }
}

@Preview(widthDp = 360)
@Composable
private fun SpotSelectorChipFallbackPreview() {
    // list mode → the dispatcher falls back to the chip SpotPicker.
    val spots = remember {
        listOf("Bike 1", "Bike 2", "Bike 3", "Bike 4").mapIndexed { i, l ->
            SpotDto(id = i + 1, label = l)
        }
    }
    var selected by remember { mutableStateOf<SpotDto?>(null) }
    ArcanaTheme {
        SpotSelector(
            spots = spots,
            selectionMode = "list",
            selected = selected,
            onSelect = { selected = it },
            modifier = Modifier.background(Stone).fillMaxWidth().padding(16.dp),
        )
    }
}
