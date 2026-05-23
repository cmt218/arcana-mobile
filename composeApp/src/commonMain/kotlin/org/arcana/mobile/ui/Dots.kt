package org.arcana.mobile.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.arcana.mobile.theme.Ash
import org.arcana.mobile.theme.Lime
import org.arcana.mobile.theme.Mist
import org.arcana.mobile.theme.Moss

/**
 * The dot half of the design system — the brand's repeating gesture.
 * DottedDivider, SectionRule, Pulse, and DotField all draw from it.
 */

/** A small lit status dot with a soft halo — "live" / "active" signal. */
@Composable
fun Pulse(
    modifier: Modifier = Modifier,
    color: Color = Lime,
    size: Int = 8,
) {
    val halo = size * 2f
    Box(modifier = modifier.size(halo.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(halo.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.13f))
        )
        Box(
            Modifier
                .size(size.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}

/** Scoreboard divider — a run of dots, the first [accentCount] lit in [accent]. */
@Composable
fun DottedDivider(
    modifier: Modifier = Modifier,
    dots: Int = 24,
    color: Color = Mist,
    accent: Color = Lime,
    accentCount: Int = 3,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        repeat(dots) { i ->
            Box(
                Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(if (i < accentCount) accent else color)
            )
        }
    }
}

/** Section header — overline label followed by a hairline rule to the edge. */
@Composable
fun SectionRule(
    label: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Overline(text = label, color = if (accent) Moss else Ash)
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(Mist)
        )
    }
}

/**
 * Large-scale dot-grid texture for dark surfaces (profile hero, cards).
 * Drop into a Box as a sibling and pass `Modifier.matchParentSize()` so it
 * picks up the surface's measured bounds.
 */
@Composable
fun DotField(
    modifier: Modifier = Modifier,
    color: Color = Moss,
    alpha: Float = 0.10f,
    spacing: Int = 14,
) {
    Canvas(modifier = modifier) {
        val step = spacing.dp.toPx()
        val dot = color.copy(alpha = alpha)
        var y = step / 2f
        while (y < size.height) {
            var x = step / 2f
            while (x < size.width) {
                drawCircle(color = dot, radius = 1.1.dp.toPx(), center = Offset(x, y))
                x += step
            }
            y += step
        }
    }
}
