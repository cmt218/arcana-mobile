package org.arcana.mobile.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.arcana.mobile.schedule.ScrollJumpChevron
import org.arcana.mobile.theme.Arcana
import org.arcana.mobile.theme.ArcanaShapes
import org.arcana.mobile.theme.Ash
import org.arcana.mobile.theme.Dur
import org.arcana.mobile.theme.Ease
import org.arcana.mobile.theme.Ink
import org.arcana.mobile.theme.Mist
import org.arcana.mobile.theme.Moss
import org.arcana.mobile.theme.Stone
import org.arcana.mobile.theme.Surface

// The Book tab's contextual filter card, shared with Discover: the pill that
// opens a section, the floating popover panel, and its DONE button.

// Shared expand/collapse for every filter panel. Exit is the true reverse of
// enter — same Medium duration + Emphasized easing — so closing reads as smooth
// as the downward unroll rather than snapping shut.
internal val filterPanelEnter =
    expandVertically(tween(Dur.Medium, easing = Ease.Emphasized), expandFrom = Alignment.Top) +
        fadeIn(tween(Dur.Short))
internal val filterPanelExit =
    shrinkVertically(tween(Dur.Medium, easing = Ease.Emphasized), shrinkTowards = Alignment.Top) +
        fadeOut(tween(Dur.Short))

/** Wraps an expanded filter's content as the floating popover card: elevation,
 *  rounded on every corner (no pointer), a hairline, a capped height with its own
 *  scroll, and a 24.dp horizontal inset matching the controls' content width so
 *  the card is never wider than them (parent CLAUDE.md: width <= controls).
 *  [FilterPopoverOverlay] anchors it under the controls, over the schedule. */
@Composable
internal fun FloatingFilterPanel(
    maxHeight: Dp,
    verticalArrangement: Arrangement.Vertical,
    contentHorizontalPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cardSource = remember { MutableInteractionSource() }
    val scroll = rememberScrollState()
    val scrollScope = rememberCoroutineScope()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, bottom = 12.dp)
            .cardShadow(ArcanaShapes.Card)
            .clip(ArcanaShapes.Card)
            .background(Surface)
            .border(1.dp, Mist, ArcanaShapes.Card)
            // Swallow taps in the card's own gaps so the outside-tap catcher
            // behind it doesn't close the popover.
            .clickable(interactionSource = cardSource, indication = null) {},
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = maxHeight)
                .verticalScroll(scroll)
                .padding(horizontal = contentHorizontalPadding, vertical = 14.dp),
            verticalArrangement = verticalArrangement,
            content = content,
        )
        // Jump-to-top / jump-to-bottom affordances, each fading in only when the
        // list can still scroll that way; a tap snaps to that end. Only the tall
        // panels (All Studios, Modalities) ever overflow enough to show them.
        ScrollJumpChevron(
            pointsDown = false,
            visible = scroll.canScrollBackward,
            onClick = { scrollScope.launch { scroll.animateScrollTo(0) } },
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
        )
        ScrollJumpChevron(
            pointsDown = true,
            visible = scroll.canScrollForward,
            onClick = { scrollScope.launch { scroll.animateScrollTo(scroll.maxValue) } },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
        )
    }
}


/** The Time / Modalities overlay-filter buttons. Moss-filled when active, an Ash
 *  outline otherwise. Pass `Modifier.weight(1f)` to size two pills equally; the
 *  label centers. */
@Composable
internal fun FilterPill(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val source = remember { MutableInteractionSource() }
    val fill by animateColorAsState(
        targetValue = if (active) Moss else Surface,
        animationSpec = tween(Dur.Short),
        label = "filterPillFill",
    )
    val border by animateColorAsState(
        targetValue = if (active) Moss else Ash,
        animationSpec = tween(Dur.Short),
        label = "filterPillBorder",
    )
    Row(
        modifier = modifier
            .pressable(source, pressedScale = 0.97f)
            .then(if (active) Modifier.controlShadow(ArcanaShapes.Pill) else Modifier.softShadow(ArcanaShapes.Pill))
            .clip(CircleShape)
            .background(fill)
            .border(1.dp, border, CircleShape)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.opticallyCentredCaps(FILTER_PILL_LABEL_SIZE, FILTER_PILL_LABEL_TRACKING_EM),
            maxLines = 1, softWrap = false,
            style = TextStyle(
                fontFamily = Arcana.fonts.display,
                fontWeight = FontWeight.SemiBold,
                fontSize = FILTER_PILL_LABEL_SIZE,
                letterSpacing = FILTER_PILL_LABEL_TRACKING_EM.em,
                color = if (active) Stone else Ink,
            ),
        )
    }
}

internal val FILTER_PILL_LABEL_SIZE = 12.sp
internal const val FILTER_PILL_LABEL_TRACKING_EM = 0.10f

/** Moss-filled "DONE" button that collapses an expanded filter section — the
 *  same effect as tapping the active pill again, but reachable from the bottom
 *  of a long favorites list / studio accordion without scrolling back up. */
@Composable
internal fun FilterDoneButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    val pressed by rememberPressed(source)
    val fill by animateColorAsState(
        targetValue = if (pressed) Moss.pressedShade() else Moss,
        animationSpec = tween(Dur.Quick),
        label = "filterDoneFill",
    )
    Row(
        modifier = modifier
            .pressable(source, pressedScale = 0.97f)
            .controlShadow(ArcanaShapes.Pill)
            .clip(CircleShape)
            .background(fill)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "DONE",
            modifier = Modifier.opticallyCentredCaps(FILTER_PILL_LABEL_SIZE, FILTER_PILL_LABEL_TRACKING_EM),
            maxLines = 1, softWrap = false,
            style = TextStyle(
                fontFamily = Arcana.fonts.display,
                fontWeight = FontWeight.SemiBold,
                fontSize = FILTER_PILL_LABEL_SIZE,
                letterSpacing = FILTER_PILL_LABEL_TRACKING_EM.em,
                color = Stone,
            ),
        )
    }
}

