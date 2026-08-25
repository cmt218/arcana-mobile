package org.arcana.mobile.ui

import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

// Compose centres a Text's LAYOUT box (ascent..descent), but all-caps ink only
// occupies cap-height..baseline, so the empty descent pushes glyphs visibly high.
// CMP exposes no cap-height, so these are measured, not derived. Em ratios, so
// they scale with type size and are density-independent.
// Re-measure if a label's font, weight, size or tracking changes:
//   tools/regression/measure_centering.py <shot.png> <fill_hex> 3 <x0 y0 x1 y1>
private const val CAP_NUDGE_EM = 0.0914f
private const val SIDE_BEARING_NUDGE_EM = 0.0193f

/** Down-nudge, in sp, that lands all-caps ink on its layout box's centre. */
internal fun capNudgeSp(fontSize: Float): Float = fontSize * CAP_NUDGE_EM

/** Right-nudge, in sp, cancelling the trailing letter-space a CENTRED label
 *  splits evenly. Meaningless for a start-aligned label, which never splits it. */
internal fun trailingSpaceNudgeSp(fontSize: Float, letterSpacingEm: Float): Float =
    fontSize * (letterSpacingEm / 2f + SIDE_BEARING_NUDGE_EM)

/**
 * Optically centres an ALL-CAPS label in a filled control: down to counter the
 * font's high cap-anchor, right to cancel the trailing letter-space. Use it on
 * every one, or the label sits about a point high and left.
 *
 * Draw-time `offset` only, so layout and touch targets are unaffected. Caps
 * only; never on sentence-case body text.
 */
@Composable
fun Modifier.opticallyCentredCaps(
    fontSize: TextUnit,
    letterSpacingEm: Float,
): Modifier {
    val density = LocalDensity.current
    return with(density) {
        offset(
            x = trailingSpaceNudgeSp(fontSize.value, letterSpacingEm).sp.toDp(),
            y = capNudgeSp(fontSize.value).sp.toDp(),
        )
    }
}

/**
 * Vertical half of [opticallyCentredCaps], for an all-caps label sitting BESIDE
 * a sibling glyph rather than centred in a filled box: a [TextLink] and its
 * arrow. Applying the horizontal nudge here would shift the label out of the
 * gutter it shares with the column above it, correcting nothing.
 */
@Composable
fun Modifier.opticallyCentredCapsVertical(fontSize: TextUnit): Modifier {
    val density = LocalDensity.current
    return with(density) { offset(y = capNudgeSp(fontSize.value).sp.toDp()) }
}
