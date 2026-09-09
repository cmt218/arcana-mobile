package org.arcana.mobile.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.arcana.mobile.theme.Ink
import org.arcana.mobile.theme.Springs

private const val PRESSED_SHADE = 0.12f

// A tap releases within a frame or two, faster than a spring visibly moves, and
// the derived pressed State coalesces its brief true away before an effect keyed
// on it runs — so a quick tap showed no dip, only a press-and-hold did. Latch on
// the raw Press event (never coalesced), snap down fast, and hold a floor so every
// tap registers; ease back up on the spring.
private const val PRESS_DOWN_MS = 90
private val MIN_PRESS = 120.milliseconds

/** The fill a control shows while pressed: the same colour, a step toward Ink. */
fun Color.pressedShade(): Color = lerp(this, Ink, PRESSED_SHADE)

@Composable
fun rememberPressed(interactionSource: MutableInteractionSource): State<Boolean> =
    interactionSource.collectIsPressedAsState()

/**
 * Scales the control to [pressedScale] while pressed and back on release. The dip
 * is latched to a minimum so a quick tap shows it. Replaces the ripple: pair with
 * `clickable(interactionSource = source, indication = null)`.
 */
@Composable
fun Modifier.pressable(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = 0.97f,
): Modifier {
    var held by remember { mutableStateOf(false) }
    LaunchedEffect(interactionSource, enabled) {
        var downAt = TimeSource.Monotonic.markNow()
        var release: Job? = null
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    release?.cancel()
                    if (enabled) { held = true; downAt = TimeSource.Monotonic.markNow() }
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    release?.cancel()
                    release = launch {
                        val remaining = MIN_PRESS - downAt.elapsedNow()
                        if (remaining.isPositive()) delay(remaining)
                        held = false
                    }
                }
            }
        }
    }
    // Held as a State, not unwrapped with `by`: this composable has no restart
    // group, so a `by` read of the value here would recompose the CALLER every frame.
    val scale = animateFloatAsState(
        targetValue = if (held) pressedScale else 1f,
        animationSpec = if (held) tween(PRESS_DOWN_MS) else Springs.Snappy,
        label = "pressScale",
    )
    return graphicsLayer { scaleX = scale.value; scaleY = scale.value }
}
