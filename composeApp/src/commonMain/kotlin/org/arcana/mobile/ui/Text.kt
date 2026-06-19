package org.arcana.mobile.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.arcana.mobile.theme.Arcana
import org.arcana.mobile.theme.Ash
import org.arcana.mobile.theme.Ink

/**
 * Arcana text primitives — the type half of the mobile design system.
 * Every screen composes from these instead of hand-rolling TextStyles, so the
 * League Spartan / DM Sans / Cormorant / JetBrains Mono hierarchy stays uniform.
 */

/** Display headline — League Spartan, ALL CAPS, tight tracking. Splash/hero scale. */
@Composable
fun Display(
    text: String,
    modifier: Modifier = Modifier,
    size: Int = 44,
    color: Color = Ink,
    weight: FontWeight = FontWeight.Bold,
    textAlign: TextAlign = TextAlign.Unspecified,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = TextStyle(
            fontFamily = Arcana.fonts.display,
            fontWeight = weight,
            fontSize = size.sp,
            lineHeight = (size * 0.94f).sp,
            letterSpacing = (-0.025).em,
            color = color,
            textAlign = textAlign,
        ),
    )
}

/** H2 — League Spartan, ALL CAPS, slightly open tracking, softer than Display. */
@Composable
fun Heading2(
    text: String,
    modifier: Modifier = Modifier,
    size: Int = 28,
    color: Color = Ink,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = TextStyle(
            fontFamily = Arcana.fonts.display,
            fontWeight = FontWeight.SemiBold,
            fontSize = size.sp,
            lineHeight = (size * 1.02f).sp,
            letterSpacing = 0.02.em,
            color = color,
        ),
    )
}

/** H3 — DM Sans Bold, sentence case. Card titles. */
@Composable
fun Heading3(
    text: String,
    modifier: Modifier = Modifier,
    size: Int = 22,
    color: Color = Ink,
) {
    Text(
        text = text,
        modifier = modifier,
        style = TextStyle(
            fontFamily = Arcana.fonts.body,
            fontWeight = FontWeight.Bold,
            fontSize = size.sp,
            lineHeight = (size * 1.2f).sp,
            letterSpacing = 0.025.em,
            color = color,
        ),
    )
}

/** Overline — DM Sans, ALL CAPS, wide tracking. Section labels. */
@Composable
fun Overline(
    text: String,
    modifier: Modifier = Modifier,
    size: Int = 11,
    color: Color = Ash,
    // Defaults to a single line (the stamp/label use). Raise it (e.g. Int.MAX_VALUE)
    // for longer label text that should wrap to new lines instead of truncating.
    maxLines: Int = 1,
    // Defaults to clipping (the design's stamps are sized to fit). Pass Ellipsis
    // for a flexible label that should truncate with "…" when space runs out.
    overflow: TextOverflow = TextOverflow.Clip,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        maxLines = maxLines,
        overflow = overflow,
        style = TextStyle(
            fontFamily = Arcana.fonts.body,
            fontWeight = FontWeight.Bold,
            fontSize = size.sp,
            letterSpacing = 0.22.em,
            color = color,
        ),
    )
}

/** Body — DM Sans, sentence case, 1.5 line-height. */
@Composable
fun BodyText(
    text: String,
    modifier: Modifier = Modifier,
    size: Int = 15,
    color: Color = Ink,
    weight: FontWeight = FontWeight.Normal,
    textAlign: TextAlign = TextAlign.Unspecified,
    // Default to unbounded wrapping (the prose use). Pass maxLines = 1 +
    // overflow = Ellipsis for single-line labels that must truncate (e.g. the
    // schedule row title).
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    Text(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        overflow = overflow,
        style = TextStyle(
            fontFamily = Arcana.fonts.body,
            fontWeight = weight,
            fontSize = size.sp,
            lineHeight = (size * 1.5f).sp,
            color = color,
            textAlign = textAlign,
        ),
    )
}

/** Accent — Cormorant Garamond italic. The one emotional line per screen. */
@Composable
fun AccentText(
    text: String,
    modifier: Modifier = Modifier,
    size: Int = 20,
    color: Color = Ink,
    textAlign: TextAlign = TextAlign.Unspecified,
) {
    Text(
        text = text,
        modifier = modifier,
        style = TextStyle(
            fontFamily = Arcana.fonts.accent,
            fontWeight = FontWeight.Medium,
            fontStyle = FontStyle.Italic,
            fontSize = size.sp,
            lineHeight = (size * 1.32f).sp,
            letterSpacing = 0.005.em,
            color = color,
            textAlign = textAlign,
        ),
    )
}

/** Caption — DM Sans Medium, sentence case, slight tracking. Metadata / timestamps. */
@Composable
fun Caption(
    text: String,
    modifier: Modifier = Modifier,
    size: Int = 12,
    color: Color = Ash,
) {
    Text(
        text = text,
        modifier = modifier,
        maxLines = 1,
        style = TextStyle(
            fontFamily = Arcana.fonts.body,
            fontWeight = FontWeight.Medium,
            fontSize = size.sp,
            letterSpacing = 0.01.em,
            color = color,
        ),
    )
}
