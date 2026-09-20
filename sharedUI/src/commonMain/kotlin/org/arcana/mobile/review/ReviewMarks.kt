package org.arcana.mobile.review

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import org.arcana.mobile.theme.MarkAmber
import org.arcana.mobile.theme.MarkBang
import org.arcana.mobile.theme.MarkRed
import org.arcana.mobile.theme.Plate

const val MAX_INTENSITY = 3

private const val CHECK = "✅"
private const val FLAME = "🔥"
private const val EMOJI_LINE = 1.25f

/**
 * "Would you take it again" as a mark: the green check emoji for yes, a warning
 * triangle for maybe, a minus on a red disc for no. The last two are drawn:
 * Compose draws the warning sign as a bare text outline on iOS, and the pair
 * has to match. Decorative: whatever it sits in carries the words.
 */
@Composable
fun AgainMark(again: String, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        if (again == "yes") {
            // Sized in dp like its drawn siblings, so the three stay one size at any text scale.
            val emojiSize = with(LocalDensity.current) { size.toSp() }
            Emoji(CHECK, emojiSize, Modifier.wrapContentSize(unbounded = true))
        } else {
            Canvas(Modifier.fillMaxSize()) { if (again == "no") minusDisc() else warning() }
        }
    }
}

private fun DrawScope.minusDisc() {
    val side = size.minDimension
    drawCircle(MarkRed, radius = side / 2f)
    val half = side * 0.25f
    drawLine(Plate, Offset(center.x - half, center.y), Offset(center.x + half, center.y), side * 0.15f, StrokeCap.Round)
}

private fun DrawScope.warning() {
    val side = size.minDimension
    // The corners are rounded by a stroke, so the path is inset by half of it.
    val round = side * 0.16f
    val inset = round / 2f
    val triangle = Path().apply {
        moveTo(side / 2f, inset + side * 0.03f)
        lineTo(side - inset, side - inset - side * 0.04f)
        lineTo(inset, side - inset - side * 0.04f)
        close()
    }
    drawPath(triangle, MarkAmber)
    drawPath(triangle, MarkAmber, style = Stroke(width = round, join = StrokeJoin.Round))
    val x = side / 2f
    drawLine(MarkBang, Offset(x, side * 0.38f), Offset(x, side * 0.62f), side * 0.11f, StrokeCap.Round)
    drawCircle(MarkBang, radius = side * 0.065f, center = Offset(x, side * 0.79f))
}

/** Intensity as one to three flames. Decorative. */
@Composable
fun IntensityFlames(intensity: Int, size: TextUnit, modifier: Modifier = Modifier) {
    Emoji(FLAME.repeat(intensity.coerceIn(1, MAX_INTENSITY)), size, modifier.clearAndSetSemantics { })
}

/**
 * System emoji through the platform's default family (Apple Color Emoji, Noto
 * Color Emoji). The line is fixed: Apple's emoji line runs a point taller than
 * Noto's, which made a pill of emoji taller than a pill of words on iOS only.
 */
@Composable
private fun Emoji(text: String, size: TextUnit, modifier: Modifier) {
    Text(
        text = text,
        maxLines = 1,
        softWrap = false,
        style = TextStyle(
            fontSize = size,
            fontFamily = FontFamily.Default,
            lineHeight = size * EMOJI_LINE,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        ),
        modifier = modifier,
    )
}
