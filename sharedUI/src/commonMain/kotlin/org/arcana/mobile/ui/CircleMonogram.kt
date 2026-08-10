package org.arcana.mobile.ui

import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.arcana.mobile.theme.Arcana

/**
 * A short monogram or number rendered dead-center for a circle (avatar
 * initials, the numbered favorite badges, etc.). The caller supplies the
 * circle — a `Box(contentAlignment = Center)` with the background/border — and
 * this just draws the glyphs centered inside it.
 *
 * Two things make a naive `Box(center) { Text }` read off-center, and both are
 * handled here so every circle badge looks the same:
 *  1. The display font reserves descent space the all-caps glyphs never fill,
 *     seating them optically high — so we trim the line box and nudge down a
 *     hair proportional to the size.
 *  2. `letterSpacing` adds a trailing gap that shifts centered text left, so a
 *     monogram carries none.
 */
@Composable
fun CircleMonogram(
    text: String,
    fontSize: Int,
    color: Color,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.Bold,
) {
    Text(
        text = text,
        modifier = modifier.offset(y = (fontSize * 0.09f).dp),
        style = TextStyle(
            fontFamily = Arcana.fonts.display,
            fontWeight = fontWeight,
            fontSize = fontSize.sp,
            lineHeight = fontSize.sp,
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both,
            ),
            color = color,
        ),
    )
}
