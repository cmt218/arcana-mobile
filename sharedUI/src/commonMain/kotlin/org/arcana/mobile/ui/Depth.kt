package org.arcana.mobile.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import org.arcana.mobile.theme.Ink
import org.arcana.mobile.theme.Stone

/** Warm Ink shadows so controls sit on the atmosphere instead of being drawn into it. */

/** Moss and Ink pills, the selected day chip. Pair with [innerHighlight] on dark fills. */
fun Modifier.controlShadow(shape: Shape): Modifier = dropShadow(shape) {
    radius = 18.dp.toPx()
    offset = Offset(0f, 6.dp.toPx())
    color = Ink.copy(alpha = 0.14f)
}

/** Home cards, the detail hero, the favourites nudge, sheets. */
fun Modifier.cardShadow(shape: Shape): Modifier = dropShadow(shape) {
    radius = 24.dp.toPx()
    offset = Offset(0f, 10.dp.toPx())
    color = Ink.copy(alpha = 0.08f)
}

/** The floating Android tab bar. */
fun Modifier.barShadow(shape: Shape): Modifier = dropShadow(shape) {
    radius = 24.dp.toPx()
    offset = Offset(0f, 8.dp.toPx())
    color = Ink.copy(alpha = 0.12f)
}

/** Paper pills and chips: a whisper, so they read as lying on the surface. */
fun Modifier.softShadow(shape: Shape): Modifier = dropShadow(shape) {
    radius = 8.dp.toPx()
    offset = Offset(0f, 2.dp.toPx())
    color = Ink.copy(alpha = 0.05f)
}

/** One-pixel Stone highlight along the top edge of a dark fill. */
fun Modifier.innerHighlight(shape: Shape): Modifier = innerShadow(shape) {
    radius = 0.dp.toPx()
    spread = 1.dp.toPx()
    offset = Offset(0f, 1.dp.toPx())
    color = Stone.copy(alpha = 0.10f)
}
