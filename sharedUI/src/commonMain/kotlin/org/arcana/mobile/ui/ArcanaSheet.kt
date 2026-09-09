package org.arcana.mobile.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.arcana.mobile.theme.ArcanaShapes
import org.arcana.mobile.theme.Ink
import org.arcana.mobile.theme.Mist
import org.arcana.mobile.theme.Springs
import org.arcana.mobile.theme.Surface

private const val SCRIM_ALPHA = 0.40f
private const val RECEDE_SCALE = 0.94f
private val RECEDE_RADIUS = 26.dp

/**
 * The app's one sheet: a Surface surface on [ArcanaShapes.Sheet]
 * corners, a Mist handle, an Ink scrim. Always fully expanded. The screen
 * that opens it applies [recedeBehindSheet] to its own content.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArcanaSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = ArcanaShapes.Sheet,
        containerColor = Surface,
        scrimColor = Ink.copy(alpha = SCRIM_ALPHA),
        dragHandle = { SheetHandle() },
        content = content,
    )
}

@Composable
private fun SheetHandle() {
    Box(
        Modifier
            .semantics { contentDescription = "Drag handle" }
            // 20/24 keeps the bar 2dp above centre, as the old 10/14 did, while
            // reaching the 48dp touch-target floor (docs/regression/inventory.md PLAT-10).
            .padding(top = 20.dp, bottom = 24.dp)
            .width(36.dp)
            .height(4.dp)
            .clip(ArcanaShapes.Pill)
            .background(Mist),
    )
}

/** Scales the screen content to 94% with rounded corners while a sheet is open. */
@Composable
fun Modifier.recedeBehindSheet(open: Boolean): Modifier {
    // One symmetric spring both directions. The caller drives `open` off the
    // sheet's targetValue, so it flips the instant a dismiss (or open) begins and
    // the page tracks the sheet down as it slides, not after it is gone.
    val scale by animateFloatAsState(
        targetValue = if (open) RECEDE_SCALE else 1f,
        animationSpec = Springs.Settle,
        label = "recede",
    )
    val radius = with(LocalDensity.current) { RECEDE_RADIUS.toPx() }
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
        val progress = (1f - scale) / (1f - RECEDE_SCALE)
        shape = RoundedCornerShape(radius * progress)
        clip = progress > 0f
    }
}
