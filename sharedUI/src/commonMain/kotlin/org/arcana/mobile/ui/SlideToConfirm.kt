package org.arcana.mobile.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import org.arcana.mobile.theme.Arcana
import org.arcana.mobile.theme.ArcanaShapes
import org.arcana.mobile.theme.Dur
import org.arcana.mobile.theme.Ink
import org.arcana.mobile.theme.Lime
import org.arcana.mobile.theme.Moss
import org.arcana.mobile.theme.Springs
import org.arcana.mobile.theme.Stone

private const val ARM_AT = 0.85f
private const val LABEL_FADE_END = 0.7f
private val TRACK_HEIGHT = 56.dp
private val THUMB = 44.dp
private val THUMB_INSET = 6.dp

internal fun slideArmed(progress: Float): Boolean = progress >= ARM_AT
internal fun slideLabelAlpha(progress: Float): Float = (1f - progress / LABEL_FADE_END).coerceIn(0f, 1f)

private enum class SlideAnchor { Start, End }

/**
 * A pill whose accent well is a thumb. Drag it to the end: light ticks build as
 * it travels, at 85% the control arms (threshold haptic, the well turns Stone),
 * and release then confirms via [onConfirm]. Release earlier and it springs
 * home. While [submitting] an in-flight shimmer sweeps the filled bar; once
 * [completed] is true it renders [completedLabel] and a check.
 */
@Suppress("DEPRECATION")
@Composable
fun SlideToConfirm(
    label: String,
    subLabel: String?,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = Moss,
    accentColor: Color = Lime,
    submitting: Boolean = false,
    completed: Boolean = false,
    completedLabel: String = "BOOKED",
) {
    val density = LocalDensity.current
    val haptics = rememberHaptics()
    var trackWidthPx by remember { mutableIntStateOf(0) }
    val travelPx = with(density) { (trackWidthPx - (THUMB + THUMB_INSET * 2).roundToPx()).coerceAtLeast(1) }.toFloat()
    val state = remember(travelPx) {
        // Only this deprecated factory can set velocityThreshold = infinity (no
        // fling); the modifier-config replacement dropped that knob.
        AnchoredDraggableState(
            initialValue = SlideAnchor.Start,
            anchors = DraggableAnchors { SlideAnchor.Start at 0f; SlideAnchor.End at travelPx },
            positionalThreshold = { distance -> distance * ARM_AT },
            velocityThreshold = { Float.POSITIVE_INFINITY },
            snapAnimationSpec = Springs.Settle,
            decayAnimationSpec = exponentialDecay(),
        )
    }
    val armed by remember(travelPx) {
        derivedStateOf { slideArmed((state.requireOffset() / travelPx).coerceIn(0f, 1f)) }
    }
    LaunchedEffect(armed) { if (armed && !completed) haptics.threshold() }
    // Progressive ticks building toward the 0.85 arm point — light, repeatable,
    // re-firing if the thumb springs back and is dragged forward again.
    LaunchedEffect(travelPx, completed) {
        if (completed) return@LaunchedEffect
        var lastBucket = 0
        snapshotFlow { (state.requireOffset() / travelPx).coerceIn(0f, 1f) }.collect { p ->
            val bucket = when {
                p >= 0.75f -> 3
                p >= 0.50f -> 2
                p >= 0.25f -> 1
                else -> 0
            }
            if (bucket > lastBucket) haptics.tick()
            lastBucket = bucket
        }
    }
    LaunchedEffect(state.settledValue) {
        if (state.settledValue == SlideAnchor.End && !completed) onConfirm()
    }
    LaunchedEffect(completed) { if (completed) state.snapTo(SlideAnchor.End) }

    val wellColor by animateColorAsState(if (armed || completed) Stone else accentColor, tween(Dur.Quick), label = "well")
    val pop by animateFloatAsState(targetValue = if (completed) 1f else 0f, animationSpec = Springs.Kick, label = "completedPop")

    Box(
        modifier
            .fillMaxWidth()
            .height(TRACK_HEIGHT)
            .onSizeChanged { trackWidthPx = it.width }
            .controlShadow(ArcanaShapes.Pill)
            .clip(ArcanaShapes.Pill)
            .background(containerColor)
            .innerHighlight(ArcanaShapes.Pill),
    ) {
        // Option A "round knob": no progress fill behind the thumb — the knob
        // gliding in the moss groove is the whole affordance, so there is no hard
        // lime/moss seam. While the booking call is in flight, a soft highlight
        // sweeps the track so it doesn't sit inert.
        if (submitting && !completed) {
            SubmitSweep(Modifier.matchParentSize())
        }
        Column(
            Modifier
                .align(Alignment.CenterStart)
                // Clear the resting thumb (spans THUMB_INSET..THUMB_INSET+THUMB) so
                // the label reads in full before the sweep fades it.
                .padding(start = THUMB + THUMB_INSET * 2 + 8.dp)
                .graphicsLayer { alpha = if (completed) 1f else slideLabelAlpha((state.requireOffset() / travelPx).coerceIn(0f, 1f)) },
        ) {
            Text(
                text = (if (completed) completedLabel else label).uppercase(),
                maxLines = 1,
                modifier = Modifier.opticallyCentredCaps(fontSize = 14.sp, letterSpacingEm = 0.10f),
                style = TextStyle(fontFamily = Arcana.fonts.display, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.10.em, color = Stone),
            )
            if (subLabel != null && !completed) {
                Text(
                    text = subLabel,
                    maxLines = 1,
                    style = TextStyle(fontFamily = Arcana.fonts.body, fontWeight = FontWeight.Medium, fontSize = 9.sp, letterSpacing = 0.10.em, color = Stone.copy(alpha = 0.67f)),
                )
            }
        }
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = THUMB_INSET)
                .offset { IntOffset(state.requireOffset().roundToInt(), 0) }
                .graphicsLayer { val s = 1f + 0.18f * pop * (1f - pop) * 4f; scaleX = s; scaleY = s }
                .size(THUMB)
                // Raised knob: its own soft shadow + top highlight so it reads as
                // a physical thumb sitting in the groove, not a flat lime disc.
                .softShadow(CircleShape)
                .clip(CircleShape)
                .background(wellColor)
                .innerHighlight(CircleShape)
                .then(if (enabled && !completed) Modifier.anchoredDraggable(state, Orientation.Horizontal) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            StrokeIcon(
                icon = if (completed) ArcanaIcons.Check else ArcanaIcons.ArrowRight,
                size = 18.dp,
                tint = Ink,
                contentDescription = if (completed) null else "Slide to $label",
            )
        }
    }
}

/** An indeterminate highlight sweeping along a filled pill while its request is
 *  in flight: the gesture is done but the confirm check hasn't landed. */
@Composable
internal fun SubmitSweep(modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "slideSubmit")
    val x by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1050, easing = LinearEasing), RepeatMode.Restart),
        label = "slideSubmitSweep",
    )
    Box(
        modifier.drawBehind {
            val band = size.width * 0.34f
            val cx = -band + x * (size.width + band * 2f)
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.5f to Stone.copy(alpha = 0.34f),
                    1f to Color.Transparent,
                    startX = cx - band,
                    endX = cx + band,
                ),
                size = Size(size.width, size.height),
            )
        },
    )
}
