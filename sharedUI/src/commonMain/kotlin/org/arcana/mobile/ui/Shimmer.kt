package org.arcana.mobile.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import org.arcana.mobile.theme.InkAlpha04
import org.arcana.mobile.theme.InkAlpha10

/** A subtle left-to-right shimmer brush for skeleton placeholders. Translucent
 *  Ink darkens whatever sits beneath, so it reads on Stone and on the atmosphere
 *  alike; pass light colours for a dark surface. */
@Composable
fun shimmerBrush(
    base: Color = InkAlpha10,
    highlight: Color = InkAlpha04,
): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translate by transition.animateFloat(
        initialValue = -300f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerTranslate",
    )
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(translate - 250f, 0f),
        end = Offset(translate + 250f, 0f),
    )
}

/** [shimmerBrush] frozen mid-sweep: same look, no [rememberInfiniteTransition]
 *  running. For a skeleton page a member cannot currently see (kept composed
 *  by beyondViewportPageCount) but that still needs to paint something. */
fun staticShimmerBrush(base: Color = InkAlpha10, highlight: Color = InkAlpha04): Brush =
    Brush.linearGradient(colors = listOf(base, highlight, base), start = Offset(-50f, 0f), end = Offset(450f, 0f))

/** A shimmering placeholder block. Size it with the modifier; pass a Shape for
 *  rounded blocks, or a pre-built [brush] to share one shimmer transition
 *  across many boxes instead of animating each independently. */
@Composable
fun ShimmerBox(modifier: Modifier = Modifier, shape: Shape = RectangleShape, brush: Brush? = null) {
    Box(modifier.clip(shape).background(brush ?: shimmerBrush()))
}
