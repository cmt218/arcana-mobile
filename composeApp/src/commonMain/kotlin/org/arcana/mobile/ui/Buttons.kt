package org.arcana.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import org.arcana.mobile.theme.Arcana
import org.arcana.mobile.theme.Ash2
import org.arcana.mobile.theme.Ink
import org.arcana.mobile.theme.Lime
import org.arcana.mobile.theme.Moss
import org.arcana.mobile.theme.Stone
import org.jetbrains.compose.resources.DrawableResource

/**
 * Primary call-to-action — full-width Moss pill with a Lime arrow well.
 * Disabled state drops to a muted Ash fill (matches the studio-selection
 * "pick N more" state). Pass [trailing] to swap the arrow for custom content.
 */
@Composable
fun PrimaryCta(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(if (enabled) Moss else Ash2)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label.uppercase(),
            style = TextStyle(
                fontFamily = Arcana.fonts.display,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                letterSpacing = 0.14.em,
                color = Stone,
            ),
        )
        if (trailing != null) {
            trailing()
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (enabled) Lime else Stone.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                StrokeIcon(icon = ArcanaIcons.ArrowRight, size = 18.dp, tint = Ink)
            }
        }
    }
}

/**
 * Underlined display-type text link with a trailing icon — used for the
 * "Sign up →" / "Sign in →" footer actions.
 */
@Composable
fun TextLink(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Moss,
    icon: DrawableResource = ArcanaIcons.ArrowRight,
    underline: Boolean = true,
) {
    Row(
        modifier = modifier.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label.uppercase(),
            modifier = if (underline) {
                Modifier.drawBehind {
                    val y = size.height - 1.dp.toPx()
                    drawLine(
                        color = color,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.5.dp.toPx(),
                    )
                }
            } else Modifier,
            style = TextStyle(
                fontFamily = Arcana.fonts.display,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                letterSpacing = 0.14.em,
                color = color,
            ),
        )
        Spacer(Modifier.width(8.dp))
        StrokeIcon(icon = icon, size = 14.dp, tint = color)
    }
}

/**
 * Circular icon affordance — the recurring round well seen on cards, rows,
 * and modal headers. Provide [background] for a filled well or [borderColor]
 * for a hairline-outlined one.
 */
@Composable
fun IconCircle(
    icon: DrawableResource,
    modifier: Modifier = Modifier,
    diameter: Int = 36,
    iconSize: Int = 18,
    background: Color = Color.Transparent,
    borderColor: Color? = null,
    contentColor: Color = Ink,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .size(diameter.dp)
            .clip(CircleShape)
            .background(background)
            .then(if (borderColor != null) Modifier.border(1.dp, borderColor, CircleShape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        StrokeIcon(icon = icon, size = iconSize.dp, tint = contentColor)
    }
}
