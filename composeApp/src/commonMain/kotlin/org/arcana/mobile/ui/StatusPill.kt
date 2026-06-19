package org.arcana.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

/**
 * Width-filling status pill for the Schedule row, where it sits above the time
 * in a fixed-width column. The label auto-shrinks (DM Sans Bold, 7–10sp) to fit
 * the column width, so "REQUESTED" / "CONFIRMED" never widen the column or wrap
 * — every row's left column stays identical whether or not it has a booking.
 */
@Composable
fun StatusPillFitted(status: String, modifier: Modifier = Modifier) {
    val (label, tone) = statusPill(status)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(tone.bg)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label.uppercase(),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            // Shrinks the label to fit the fixed column width — no truncation.
            autoSize = TextAutoSize.StepBased(
                minFontSize = 7.sp,
                maxFontSize = 10.sp,
                stepSize = 0.25.sp,
            ),
            color = ColorProducer { tone.fg },
            style = TextStyle(
                fontFamily = Arcana.fonts.body,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.12.em,
            ),
        )
    }
}
