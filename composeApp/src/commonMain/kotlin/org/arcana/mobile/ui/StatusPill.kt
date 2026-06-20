package org.arcana.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.arcana.mobile.theme.Arcana
import org.arcana.mobile.theme.Moss

/** Tone for a booking status pill (mirrors the server ops console pill tones). */
enum class PillTone(val bg: Color, val fg: Color) {
    // MossLight (#537F26) is a medium-dark foreground green, not a pale tint.
    // Good.bg uses the server ops console's light-moss hex hand-coded; Moss for text.
    Good(Color(0xFFE3EAD3), Moss),
    Warn(Color(0xFFFBEECC), Color(0xFF8A6A16)),
    Bad(Color(0xFFFBE0D8), Color(0xFFC0461A)),
}

/** Map a server booking status to label + tone. */
fun statusPill(status: String): Pair<String, PillTone> = when (status) {
    "requested" -> "Requested" to PillTone.Warn
    "confirmed" -> "Confirmed" to PillTone.Good
    "completed" -> "Completed" to PillTone.Good
    "no_show" -> "No-show" to PillTone.Bad
    "cancelled" -> "Cancelled" to PillTone.Bad
    "unfulfilled" -> "Couldn't book" to PillTone.Bad
    else -> status to PillTone.Warn
}

@Composable
fun StatusPill(status: String, modifier: Modifier = Modifier) {
    val (label, tone) = statusPill(status)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(tone.bg)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Overline(label, size = 10, color = tone.fg)
    }
}

/** Largest / smallest font the fitted pill will use (DM Sans Bold). */
private val PILL_MAX_FONT = 10f
private val PILL_MIN_FONT = 5.5f

/**
 * Width-filling status pill for the Schedule / Home rows, where it sits above
 * the time in a fixed-width column. The label shrinks (DM Sans Bold, 5.5–10sp)
 * to fit the column width so the **whole** word ("REQUESTED" / "CONFIRMED" /
 * "COULDN'T BOOK") stays legible without widening the column or truncating.
 *
 * The fit is done manually via `onTextLayout`: render at the max size, and while
 * the single line overflows the fixed width, step the font down. (The built-in
 * `TextAutoSize` did not shrink reliably in this Compose-Multiplatform build —
 * it left the label at full size and clipped to "REQUE…".) Keyed on the label so
 * it re-fits when the status changes; the column width is fixed so it never
 * needs to grow back.
 */
@Composable
fun StatusPillFitted(status: String, modifier: Modifier = Modifier) {
    val (rawLabel, tone) = statusPill(status)
    val label = rawLabel.uppercase()
    var fontSize by remember(label) { mutableStateOf(PILL_MAX_FONT) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(tone.bg)
            .padding(horizontal = 5.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            maxLines = 1,
            softWrap = false,
            color = ColorProducer { tone.fg },
            style = TextStyle(
                fontFamily = Arcana.fonts.body,
                fontWeight = FontWeight.Bold,
                fontSize = fontSize.sp,
                // Tighter than the standard overline tracking so the shrunk label
                // packs into the narrow column without needing an even smaller font.
                letterSpacing = 0.06.em,
            ),
            onTextLayout = { result ->
                if (result.hasVisualOverflow && fontSize > PILL_MIN_FONT) {
                    fontSize = (fontSize - 0.5f).coerceAtLeast(PILL_MIN_FONT)
                }
            },
        )
    }
}
