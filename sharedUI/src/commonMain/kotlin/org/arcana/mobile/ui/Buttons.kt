package org.arcana.mobile.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.arcana.mobile.theme.Arcana
import org.arcana.mobile.theme.ArcanaShapes
import org.arcana.mobile.theme.Ash2
import org.arcana.mobile.theme.Dur
import org.arcana.mobile.theme.Ink
import org.arcana.mobile.theme.Lime
import org.arcana.mobile.theme.Moss
import org.arcana.mobile.theme.Springs
import org.arcana.mobile.theme.Stone
import org.jetbrains.compose.resources.DrawableResource

/** Brings a bare trailing slot up to the label's 24dp inset (Row adds 8dp). */
private val TRAILING_SLOT_END_INSET = 16.dp

private val CTA_LABEL_SIZE = 14.sp
private const val CTA_LABEL_TRACKING_EM = 0.14f

/** Shared by [TextLink]'s style and its optical nudge, which is derived from the
 *  type size: separate literals would drift and silently un-centre the label. */
private val LINK_LABEL_SIZE = 13.sp

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
    containerColor: Color = Moss,
    accentColor: Color = Lime,
    trailing: (@Composable () -> Unit)? = null,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by rememberPressed(source)
    val fill by animateColorAsState(
        targetValue = when {
            !enabled -> Ash2
            pressed -> containerColor.pressedShade()
            else -> containerColor
        },
        animationSpec = tween(Dur.Quick),
        label = "ctaFill",
    )
    val kick by animateDpAsState(
        targetValue = if (pressed && enabled) 2.dp else 0.dp,
        animationSpec = Springs.kick(),
        label = "ctaKick",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressable(source, enabled)
            .then(if (enabled) Modifier.controlShadow(ArcanaShapes.Pill) else Modifier)
            .clip(ArcanaShapes.Pill)
            .background(fill)
            .then(if (enabled) Modifier.innerHighlight(ArcanaShapes.Pill) else Modifier)
            .clickable(enabled = enabled, interactionSource = source, indication = null, onClick = onClick)
            .padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label.uppercase(),
            modifier = Modifier.opticallyCentredCaps(
                fontSize = CTA_LABEL_SIZE,
                letterSpacingEm = CTA_LABEL_TRACKING_EM,
            ),
            style = TextStyle(
                fontFamily = Arcana.fonts.display,
                fontWeight = FontWeight.Bold,
                fontSize = CTA_LABEL_SIZE,
                letterSpacing = CTA_LABEL_TRACKING_EM.em,
                color = Stone,
            ),
        )
        if (trailing != null) {
            // The default arrow well is a filled 40dp circle, so end = 8dp reads
            // fine. A bare slot has no such mass and needs the extra inset.
            Box(modifier = Modifier.padding(end = TRAILING_SLOT_END_INSET)) {
                trailing()
            }
        } else {
            Box(
                modifier = Modifier
                    .offset { IntOffset(kick.roundToPx(), 0) }
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (enabled) accentColor else Stone.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                // decorative — the CTA's own label is the accessible name.
                StrokeIcon(icon = ArcanaIcons.ArrowRight, size = 18.dp, tint = Ink)
            }
        }
    }
}

/**
 * Display-type text link with a trailing arrow, optionally underlined.
 * Its label is nudged down alone so the caps read centred against the arrow.
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
            // Nudge on the label, not the Row: the arrow must stay where it is.
            // Vertical only, so the label keeps the gutter it shares with the
            // column above it.
            modifier = Modifier
                .opticallyCentredCapsVertical(LINK_LABEL_SIZE)
                .then(
                    if (underline) {
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
                ),
            style = TextStyle(
                fontFamily = Arcana.fonts.display,
                fontWeight = FontWeight.SemiBold,
                fontSize = LINK_LABEL_SIZE,
                letterSpacing = 0.14.em,
                color = color,
            ),
        )
        Spacer(Modifier.width(8.dp))
        // decorative — the link's own label is the accessible name.
        StrokeIcon(icon = icon, size = 14.dp, tint = color)
    }
}

/**
 * Circular icon affordance — the recurring round well seen on cards, rows,
 * and modal headers. Provide [background] for a filled well or [borderColor]
 * for a hairline-outlined one.
 *
 * **Pass [contentDescription] whenever [onClick] is non-null** — a tappable
 * well contains nothing but a glyph, so without one it is an unlabeled control
 * to TalkBack and an anonymous clickable to `android layout`. Leave it null for
 * the decorative (non-tappable) wells. See [StrokeIcon]'s doc for the rule.
 *
 * [diameter] is visual only. Compose expands any pointer-input node to
 * `ViewConfiguration.minimumTouchTargetSize` (48dp, both platforms), so a 36dp
 * well is measured 48dp tappable — don't inflate diameters for reach.
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
    contentDescription: String? = null,
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
        StrokeIcon(
            icon = icon,
            size = iconSize.dp,
            tint = contentColor,
            contentDescription = contentDescription,
        )
    }
}
