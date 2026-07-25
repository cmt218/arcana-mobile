package org.arcana.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
 * The shared error-state system. One classifier ([ErrorType]) drives one set of
 * components, so a failure is described the same way everywhere in the app.
 *
 * Three surfaces:
 * - [FullScreenError] — cold load, nothing cached to show.
 * - [InlineError] — one section failed inside an otherwise-live screen.
 * - [RefreshFailedToast] — a background refresh failed; stale content stays.
 *
 * Design source: Claude Design "Error State System" handoff (see
 * `docs/error-states-design-brief.md`). Copy lives in [ErrorCopy] and nowhere
 * else — screens must never hand-write an error string.
 *
 * **Theming:** the app is currently light-only ([org.arcana.mobile.theme.ArcanaTheme]
 * installs a `lightColorScheme` and nothing reads `isSystemInDarkTheme`). The
 * handoff also specifies dark variants; colors here are resolved in the single
 * [accentFor]/[overlineColorFor] + surface constants below, so adding dark is a
 * change to those resolvers rather than to every composable.
 */

// ---- Copy -------------------------------------------------------------------

internal data class ErrorStateCopy(
    val overline: String,
    val headline: String,
    val body: String,
)

/**
 * Every member-facing error string in the app, in one place. Note the
 * connection copy never says "server error": that was the original bug, where a
 * request that never reached the server blamed the server.
 *
 * No em/en dashes: brand rule for anything a human reads.
 */
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
            body = "This one's on us, not you. Give it a moment and try again.",
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

    const val REFRESH_FAILED = "Couldn't refresh. Showing your last update."
}

// ---- Color resolution (single point of theming) ------------------------------

/** Category accent: Lime signals "environmental", Burnt Nectar owns the fault. */
private fun accentFor(type: ErrorType): Color =
    if (type == ErrorType.SERVER) BurntNectar else Lime

/** The overline reads muted for Connection and accented for Server. */
private fun overlineColorFor(type: ErrorType): Color =
    if (type == ErrorType.SERVER) BurntNectar else Ash

// ---- Retry affordances -------------------------------------------------------

/**
 * Solid retry CTA. Not [PrimaryCta]: the handoff specifies a compact, centered,
 * 14dp-radius button with no trailing arrow well, sized to sit under a
 * left-aligned error block rather than span the screen.
 *
 * While [retrying] the label is replaced by the dot-matrix loader and taps are
 * ignored, so a Member can't stack retries.
 */
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
                style = TextStyle(
                    fontFamily = Arcana.fonts.display,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    lineHeight = 15.sp,
                    letterSpacing = 0.1.em,
                    color = Stone,
                ),
            )
        }
    }
}

/**
 * Underlined text retry, for the inline card and the toast. Sentence case and
 * DM Sans (not [TextLink], which is display-type caps with a trailing arrow).
 */
@Composable
fun RetryLink(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    retrying: Boolean = false,
    label: String = "Retry",
    color: Color = Moss,
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
            fontSize = 14.sp,
            color = color,
        ),
    )
}

// ---- Motif -------------------------------------------------------------------

@Composable
private fun MotifBar(width: Int, color: Color) {
    Box(
        Modifier
            .width(width.dp)
            .height(2.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color),
    )
}

/**
 * The category tell, kept whisper-quiet per the "type-forward minimal"
 * direction: Connection is an *interrupted* line with one lit segment, Server is
 * a single solid bar. Geometry is otherwise identical between the two.
 */
@Composable
private fun ErrorMotif(type: ErrorType, full: Boolean) {
    when (type) {
        ErrorType.CONNECTION -> Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (full) {
                MotifBar(16, Mist)
                MotifBar(8, Mist)
                MotifBar(20, Lime)
                MotifBar(8, Mist)
            } else {
                MotifBar(12, Mist)
                MotifBar(4, Mist)
                MotifBar(16, Lime)
            }
        }
        ErrorType.SERVER -> MotifBar(if (full) 52 else 32, BurntNectar)
    }
}

// ---- Surfaces ----------------------------------------------------------------

/**
 * Cold-load failure with nothing cached to show. The block sits in the lower
 * third and is left-aligned: the brand favors offset composition over centered
 * symmetry.
 */
@Composable
fun FullScreenError(
    type: ErrorType,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    retrying: Boolean = false,
) {
    val copy = ErrorCopy.fullScreen(type)
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Stone)
            .padding(start = 32.dp, end = 32.dp, top = 56.dp, bottom = 44.dp),
    ) {
        Spacer(Modifier.weight(1.35f))
        Column(modifier = Modifier.widthIn(max = 252.dp)) {
            ErrorMotif(type = type, full = true)
            Spacer(Modifier.height(32.dp))
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
        Spacer(Modifier.weight(1f))
    }
}

/**
 * One section failed while the rest of the screen is live. Replaces only the
 * failed block, so a Member keeps everything that did load.
 */
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
            .clip(shape)
            .background(Paper)
            .border(1.dp, Mist, shape)
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        ErrorMotif(type = type, full = false)
        Spacer(Modifier.height(16.dp))
        Heading2(text = copy.headline, size = 20, color = Ink)
        Spacer(Modifier.height(8.dp))
        BodyText(text = copy.body, size = 13, color = Ash)
        Spacer(Modifier.height(16.dp))
        RetryLink(onClick = onRetry, retrying = retrying)
    }
}

/**
 * Non-blocking notice for a failed background refresh: the content already on
 * screen is still good, so we keep it and say so rather than wiping the screen
 * for a full-screen error. One shared treatment, not category-specific: the
 * Member's action is the same either way.
 *
 * Ink surface in both themes per the handoff.
 */
@Composable
fun RefreshFailedToast(
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    retrying: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Ink)
            .padding(horizontal = 16.dp, vertical = 14.dp),
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
            text = ErrorCopy.REFRESH_FAILED,
            size = 13,
            color = Stone,
            modifier = Modifier.weight(1f),
        )
        if (onRetry != null) {
            Spacer(Modifier.width(12.dp))
            RetryLink(onClick = onRetry, retrying = retrying, color = Lime)
        }
    }
}
