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
import org.arcana.mobile.theme.Mist
import org.arcana.mobile.theme.Stone2

/** A subtle left-to-right shimmer brush for skeleton placeholders. Uses
 *  Mist as the base and Stone2 as the highlight — both read well on Stone. */
@Composable
fun shimmerBrush(
    base: Color = Mist,
    highlight: Color = Stone2,
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

/** A shimmering placeholder block. Size it with the modifier; pass a Shape for rounded blocks. */
@Composable
fun ShimmerBox(modifier: Modifier = Modifier, shape: Shape = RectangleShape) {
    Box(modifier.clip(shape).background(shimmerBrush()))
}
