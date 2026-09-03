package org.arcana.mobile.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * The arcana dot-matrix wordmark, drawn from [wordmarkGrid] so it stays sharp
 * at any size.
 *
 * Constrain the height; the width follows from the grid's aspect. Dots are one
 * cell across, so the composable's box is exactly the mark's ink bounds — it
 * sits flush in a layout with no padding of its own.
 */
@Composable
fun WordmarkLogo(
    modifier: Modifier = Modifier,
    color: Color = Moss,
) {
    val grid = wordmarkGrid
    Canvas(
        modifier = modifier
            .aspectRatio(grid.cols.toFloat() / grid.rows.toFloat())
            .semantics { contentDescription = "Arcana" },
    ) {
        val cell = size.height / grid.rows
        val half = cell / 2f
        // Round caps of strokeWidth = cell give dots one cell across, tangent
        // to their neighbours — the same dot size the splash dance settles to.
        drawPoints(
            points = grid.lit.map { (col, row) ->
                Offset(col * cell + half, row * cell + half)
            },
            pointMode = PointMode.Points,
            color = color,
            strokeWidth = cell,
            cap = StrokeCap.Round,
        )
    }
}
