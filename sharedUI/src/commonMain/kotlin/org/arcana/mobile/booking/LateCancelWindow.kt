package org.arcana.mobile.booking

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import org.arcana.mobile.theme.Wood

/**
 * Human label for a late-cancel window given in minutes, e.g. 1440 -> "24 hours",
 * 720 -> "12 hours", 60 -> "1 hour". Sub-hour or non-whole-hour windows fall back
 * to minutes ("90 minutes"). Windows are whole hours in practice; the minute
 * fallback just keeps the copy sensible for any value ops enters.
 */
fun lateCancelWindowLabel(minutes: Int): String = when {
    minutes == 60 -> "1 hour"
    minutes % 60 == 0 -> "${minutes / 60} hours"
    else -> "$minutes minutes"
}

/**
 * The booking-confirmation-sheet cancellation line. When the studio's resolved
 * window is known, it names the window concretely and **emphasizes it** (bold +
 * Wood) so the member can't miss how long they have — not gotcha fine print.
 * Older servers that send no window fall back to the prior generic copy.
 */
fun bookingCancelCopy(lateCancelCutoffMinutes: Int?): AnnotatedString = buildAnnotatedString {
    if (lateCancelCutoffMinutes == null) {
        append("Free to cancel until the studio cutoff. After that, cancelling still costs the credit.")
        return@buildAnnotatedString
    }
    append("Free to cancel up to ")
    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Wood)) {
        append(lateCancelWindowLabel(lateCancelCutoffMinutes))
    }
    append(" before class. After that, cancelling still costs the credit.")
}
