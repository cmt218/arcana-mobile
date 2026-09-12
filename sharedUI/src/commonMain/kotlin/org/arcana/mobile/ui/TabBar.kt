package org.arcana.mobile.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import org.arcana.mobile.theme.Arcana
import org.arcana.mobile.theme.ArcanaShapes
import org.arcana.mobile.theme.Ash
import org.arcana.mobile.theme.Dur
import org.arcana.mobile.theme.Lime
import org.arcana.mobile.theme.Mist
import org.arcana.mobile.theme.Mist2
import org.arcana.mobile.theme.Moss
import org.arcana.mobile.theme.Paper
import org.arcana.mobile.theme.Springs
import org.jetbrains.compose.resources.DrawableResource

/** The four primary destinations. Profile renders as the member's avatar. */
enum class ArcanaTab(val label: String, val icon: DrawableResource, val isAvatar: Boolean = false) {
    Home("Home", ArcanaIcons.Home),
    Schedule("Book", ArcanaIcons.Calendar),
    Discover("Discover", ArcanaIcons.Compass),
    Profile("You", ArcanaIcons.User, isAvatar = true),
}

private val BAR_SIDE_INSET = 12.dp
private val BAR_BOTTOM_INSET = 10.dp
private const val BAR_ALPHA = 0.92f

// Measured pixel height of the pill (Modifier.border draws inside the node,
// so the border rows count). Re-measure on the Design system "Tab bar" sample if TabItem changes.
private val BAR_HEIGHT = 69.dp

// Shared by the icon slot's height and the avatar's size below — the two must
// match or the shorter one either floats in an oversized slot or drops the
// labels off their shared baseline.
private val ICON_SLOT = 26.dp

/**
 * Bottom clearance a tab-root scrollable needs to clear the floating bar: the
 * device's own system-bar/display-cutout inset plus the gap and pill height
 * this file draws. Runtime-computed because that inset varies by device;
 * deliberately not `safeDrawing`, which folds in the IME.
 */
val floatingBarInset: Dp
    @Composable get() = with(LocalDensity.current) {
        WindowInsets.systemBars.union(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Bottom).getBottom(this).toDp()
    } + BAR_BOTTOM_INSET + BAR_HEIGHT

/**
 * Bottom navigation, Android only: a floating Paper pill over the atmosphere.
 * One Lime dot travels between the three items on a spring; the active icon
 * bounces; the active item reads in Moss.
 */
@Composable
fun ArcanaTabBar(
    active: ArcanaTab,
    onSelect: (ArcanaTab) -> Unit,
    avatarInitials: String,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    // Kept as State (not `by`-unwrapped) so the spring's per-frame ticks are read
    // at layout/placement time in TravellingDot, not here at composition time.
    val dotPosition = animateFloatAsState(
        targetValue = active.ordinal.toFloat(),
        animationSpec = Springs.Snappy,
        label = "tabDot",
    )
    Column(
        modifier = modifier
            .safeBottomBarPadding()
            .padding(start = BAR_SIDE_INSET, end = BAR_SIDE_INSET, bottom = BAR_BOTTOM_INSET)
            .fillMaxWidth()
            .barShadow(ArcanaShapes.Pill)
            .clip(ArcanaShapes.Pill)
            .background(Paper.copy(alpha = BAR_ALPHA))
            .border(1.dp, Mist, ArcanaShapes.Pill),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                ArcanaTab.entries.forEach { tab ->
                    TabItem(
                        tab = tab,
                        active = tab == active,
                        avatarInitials = avatarInitials,
                        onClick = {
                            if (tab != active) haptics.selection()
                            onSelect(tab)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            TravellingDot(position = { dotPosition.value }, count = ArcanaTab.entries.size)
        }
    }
}

/** The Lime dot, drawn once and placed at the centre of item [position] (fractional while moving). */
@Composable
private fun TravellingDot(position: () -> Float, count: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(DOT_SIZE)
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = DOT_SIZE.roundToPx()))
                layout(constraints.maxWidth, placeable.height) {
                    val x = travellingDotOffsetX(position(), constraints.maxWidth, count, placeable.width)
                    placeable.placeRelative(x, 0)
                }
            }
            .size(DOT_SIZE)
            .clip(CircleShape)
            .background(Lime),
    )
}

/** X offset (px) that centres a [dotWidthPx]-wide dot over slot [position] of [count] equal slots spanning [barWidthPx]. */
internal fun travellingDotOffsetX(position: Float, barWidthPx: Int, count: Int, dotWidthPx: Int): Int {
    val slot = barWidthPx / count.toFloat()
    return ((position + 0.5f) * slot - dotWidthPx / 2f).roundToInt()
}

private val DOT_SIZE = 4.dp

@Composable
private fun TabItem(
    tab: ArcanaTab,
    active: Boolean,
    avatarInitials: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint by animateColorAsState(if (active) Moss else Ash, tween(Dur.Short), label = "tabTint")
    val bounce by animateFloatAsState(
        targetValue = if (active) 1.06f else 1f,
        animationSpec = Springs.Snappy,
        label = "tabBounce",
    )
    Column(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        // Reserves the dot's row; the dot itself is drawn once by TravellingDot.
        Spacer(Modifier.size(DOT_SIZE))
        // Fixed height so HOME/BOOK's 22dp icon and YOU's 26dp avatar share one
        // slot and the labels below them land on the same baseline.
        Box(
            modifier = Modifier
                .height(ICON_SLOT)
                .graphicsLayer { scaleX = bounce; scaleY = bounce },
            contentAlignment = Alignment.Center,
        ) {
            if (tab.isAvatar) {
                Box(
                    modifier = Modifier
                        .size(ICON_SLOT)
                        .clip(CircleShape)
                        .background(if (active) Moss else Mist2)
                        .then(
                            if (active) Modifier.border(1.5.dp, Lime, CircleShape) else Modifier
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = avatarInitials,
                        // Match the profile hero: trim the line box and nudge the
                        // all-caps initials down ~0.09em so they sit dead-center.
                        modifier = Modifier.offset(y = 1.dp),
                        style = TextStyle(
                            fontFamily = Arcana.fonts.display,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            lineHeight = 10.sp,
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.Both,
                            ),
                            letterSpacing = 0.02.em,
                            color = if (active) Lime else Ash,
                        ),
                    )
                }
            } else {
                // decorative — the visible tab label below is the accessible name,
                // so describing the glyph too would double the announcement.
                StrokeIcon(icon = tab.icon, size = 22.dp, tint = tint)
            }
        }
        Text(
            text = tab.label.uppercase(),
            style = TextStyle(
                fontFamily = Arcana.fonts.body,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                fontSize = 10.sp,
                letterSpacing = 0.18.em,
                color = tint,
            ),
        )
    }
}
