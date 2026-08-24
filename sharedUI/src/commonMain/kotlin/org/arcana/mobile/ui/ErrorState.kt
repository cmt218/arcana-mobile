package org.arcana.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.arcana.mobile.networking.ErrorType
import org.arcana.mobile.theme.Arcana
import org.arcana.mobile.theme.Ash
import org.arcana.mobile.theme.BurntNectar
import org.arcana.mobile.theme.Ink
import org.arcana.mobile.theme.Lime
import org.arcana.mobile.theme.Mist
import org.arcana.mobile.theme.Moss
import org.arcana.mobile.theme.Paper
import org.arcana.mobile.theme.Stone

/**
 * The shared error-state system: [ErrorType] drives [FullScreenError],
 * [InlineError], and [ErrorSnackbar] so a failure looks the same
 * everywhere. Copy lives only in [ErrorCopy]. Design source:
 * `docs/error-states-design-brief.md`. Light-only today; colors resolve
 * through [accentFor]/[overlineColorFor] so dark mode later touches only those two.
 */

// ---- Copy -------------------------------------------------------------------

internal data class ErrorStateCopy(
    val overline: String,
    val headline: String,
    val body: String,
)

/** Every member-facing error string, in one place. Connection copy must never
 *  say "server error" — the server may be perfectly healthy. No em/en dashes:
 *  brand rule for anything a human reads. */
internal object ErrorCopy {
    fun fullScreen(type: ErrorType): ErrorStateCopy = when (type) {
        ErrorType.CONNECTION -> ErrorStateCopy(
            overline = "Connection",
            headline = "Can't reach Arcana.",
            // Deliberately not "You're offline": this category also covers a
            // flaky or timed-out connection, where the phone is online.
            body = "Check your connection and try again.",
        )
        ErrorType.SERVER -> ErrorStateCopy(
            overline = "Server",
            headline = "Something's off on our end.",
            body = "Give it a moment and try again.",
        )
    }

    fun inline(type: ErrorType): ErrorStateCopy = when (type) {
        ErrorType.CONNECTION -> ErrorStateCopy(
            overline = "Connection",
            headline = "Can't load this right now.",
            body = "Check your connection.",
        )
        ErrorType.SERVER -> ErrorStateCopy(
            overline = "Server",
            headline = "This didn't load.",
            body = "On our end. Try again.",
        )
    }

    const val REFRESH_FAILED = "Couldn't refresh"

    /** Content description for the toast's dismiss control (icon-only, so it
     *  needs an accessible name; there is no visible label to merge with). */
    const val DISMISS_REFRESH_FAILED = "Dismiss notice"
}

// ---- Color resolution (single point of theming) ------------------------------

/** Category accent: Lime signals "environmental", Burnt Nectar owns the fault. */
private fun accentFor(type: ErrorType): Color =
    if (type == ErrorType.SERVER) BurntNectar else Lime

private fun overlineColorFor(type: ErrorType): Color =
    if (type == ErrorType.SERVER) BurntNectar else Ash

/** Shared by the label's style and its optical-centring offset, so the two can
 *  never drift apart — the offset is derived from the type size, not guessed. */
private val RETRY_LABEL_SIZE = 15.sp
private const val RETRY_LABEL_TRACKING_EM = 0.1f

// ---- Retry affordances -------------------------------------------------------

/** Solid retry CTA. Not [PrimaryCta]: the handoff specifies a compact,
 *  centered, 14dp-radius button with no arrow well. While [retrying] the
 *  label becomes the dot-matrix loader and taps are ignored. */
@Composable
fun RetryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    retrying: Boolean = false,
    label: String = "Try again",
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Moss)
            .clickable(enabled = !retrying, onClick = onClick)
            .defaultMinSize(minWidth = 152.dp, minHeight = 56.dp)
            .padding(horizontal = 32.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (retrying) {
            DotMatrixLoaderCompact()
        } else {
            Text(
                text = label.uppercase(),
                modifier = Modifier.opticallyCentredCaps(
                    fontSize = RETRY_LABEL_SIZE,
                    letterSpacingEm = RETRY_LABEL_TRACKING_EM,
                ),
                style = TextStyle(
                    fontFamily = Arcana.fonts.display,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = RETRY_LABEL_SIZE,
                    lineHeight = RETRY_LABEL_SIZE,
                    letterSpacing = RETRY_LABEL_TRACKING_EM.em,
                    color = Stone,
                ),
            )
        }
    }
}

/** Underlined text retry, for the inline card and the toast. Sentence case and
 *  DM Sans (not [TextLink], which is display-type caps with a trailing arrow). */
@Composable
fun RetryLink(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    retrying: Boolean = false,
    label: String = "Retry",
    color: Color = Moss,
    // Match the text this sits beside: centring differently-sized boxes aligns
    // their centres, not their baselines.
    fontSize: TextUnit = 14.sp,
) {
    if (retrying) {
        DotMatrixLoaderCompact(modifier = modifier)
        return
    }
    Text(
        text = label,
        modifier = modifier
            .clickable(onClick = onClick)
            .drawBehind {
                val y = size.height - 1.dp.toPx()
                drawLine(
                    color = color,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.5.dp.toPx(),
                )
            },
        style = TextStyle(
            fontFamily = Arcana.fonts.body,
            fontWeight = FontWeight.SemiBold,
            fontSize = fontSize,
            color = color,
        ),
    )
}

// ---- Surfaces ----------------------------------------------------------------

/** Cold-load failure, nothing cached. iOS: bottom-bounded by its own 44dp
 *  padding + trailing `weight(1f)` spacer so retry clears the floating tab
 *  bar — a caller must NOT also apply `LocalFloatingBarInset` here, or it double-pads. */
@Composable
fun FullScreenError(
    type: ErrorType,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    retrying: Boolean = false,
) {
    val copy = ErrorCopy.fullScreen(type)
    // heightIn(min) + Arrangement.Center rather than weight(1f) spacers: weights
    // cannot go negative, so a short viewport (landscape) clipped the retry
    // button. Centres while there is room, scrolls once there isn't.
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(Stone)) {
        val viewport = maxHeight
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                // Between background and padding so Stone still bleeds to the edge
                // while no glyph lands under a landscape cutout. Consumes, so it is
                // a no-op where a caller already applied it.
                .safeHorizontalPadding()
                .heightIn(min = viewport)
                .padding(start = 32.dp, end = 32.dp, top = 56.dp, bottom = 44.dp),
            verticalArrangement = Arrangement.Center,
        ) {
        Column(modifier = Modifier.widthIn(max = 252.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accentFor(type)),
                )
                Spacer(Modifier.width(8.dp))
                Overline(text = copy.overline, size = 12, color = overlineColorFor(type))
            }
            Spacer(Modifier.height(16.dp))
            Heading2(text = copy.headline, size = 36, color = Ink)
            Spacer(Modifier.height(16.dp))
            BodyText(
                text = copy.body,
                size = 15,
                color = Ash,
                modifier = Modifier.widthIn(max = 232.dp),
            )
            Spacer(Modifier.height(32.dp))
            RetryButton(onClick = onRetry, retrying = retrying)
        }
        }
    }
}

/** One section failed while the rest of the screen is live. Replaces only the
 *  failed block, so a Member keeps everything that did load. */
@Composable
fun InlineError(
    type: ErrorType,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    retrying: Boolean = false,
) {
    val copy = ErrorCopy.inline(type)
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .safeHorizontalPadding()
            .clip(shape)
            .background(Paper)
            .border(1.dp, Mist, shape)
            // Equal gaps: card top, headline-to-retry, card bottom.
            .padding(horizontal = 20.dp, vertical = INLINE_CARD_GAP),
    ) {
        Heading2(text = copy.headline, size = 20, color = Ink)
        Spacer(Modifier.height(INLINE_CARD_MID_GAP))
        RetryLink(onClick = onRetry, retrying = retrying)
    }
}

private val INLINE_CARD_GAP = 20.dp

/** Smaller than [INLINE_CARD_GAP] on purpose. The headline's text box carries
 *  ~9dp of descent below its visible ink, so an equal spacer renders as a
 *  visibly larger gap. Measured so all three gaps READ equal. */
private val INLINE_CARD_MID_GAP = 11.dp
private val SNACKBAR_MIN_HEIGHT = 60.dp
private val SNACKBAR_SIDE_MARGIN = 16.dp

/** Non-blocking notice for a failed background refresh: content stays, we
 *  just flag that the refresh failed rather than wiping the screen.
 *  [onDismiss], when provided, adds a close control — without it, a Member
 *  with no connectivity has no way to clear the bar (retrying re-raises it). */
@Composable
fun ErrorSnackbar(
    text: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    retrying: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .safeHorizontalPadding()
            // Design margin, not an inset: safeHorizontalPadding covers cutouts
            // only, which are 0 in portrait. Matches the gap above the tab bar.
            .padding(horizontal = SNACKBAR_SIDE_MARGIN)
            // Height must not depend on which optional controls are present.
            // 60 = the 44dp dismiss target + 8dp either side.
            .defaultMinSize(minHeight = SNACKBAR_MIN_HEIGHT)
            .clip(RoundedCornerShape(14.dp))
            .background(Ink)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Lime),
        )
        Spacer(Modifier.width(12.dp))
        BodyText(
            text = text,
            size = 13,
            color = Stone,
            modifier = Modifier.weight(1f),
        )
        if (onRetry != null) {
            Spacer(Modifier.width(12.dp))
            RetryLink(onClick = onRetry, retrying = retrying, color = Lime, fontSize = 13.sp)
        }
        if (onDismiss != null) {
            Spacer(Modifier.width(12.dp))
            // No background/border, so only the 14dp glyph shows and the
            // diameter is pure layout; the offset pulls it back to where a
            // 28dp control would sit.
            IconCircle(
                icon = ArcanaIcons.Close,
                modifier = Modifier.offset(x = 8.dp),
                diameter = 44,
                iconSize = 14,
                contentColor = Stone,
                onClick = onDismiss,
                contentDescription = ErrorCopy.DISMISS_REFRESH_FAILED,
            )
        }
    }
}
