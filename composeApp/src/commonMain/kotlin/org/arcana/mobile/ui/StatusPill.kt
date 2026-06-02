package org.arcana.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
