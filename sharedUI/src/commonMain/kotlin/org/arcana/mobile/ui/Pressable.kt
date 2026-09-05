package org.arcana.mobile.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import org.arcana.mobile.theme.Ink
import org.arcana.mobile.theme.Springs

private const val PRESSED_SHADE = 0.12f

/** The fill a control shows while pressed: the same colour, a step toward Ink. */
fun Color.pressedShade(): Color = lerp(this, Ink, PRESSED_SHADE)

@Composable
fun rememberPressed(interactionSource: MutableInteractionSource): State<Boolean> =
    interactionSource.collectIsPressedAsState()

/**
 * Scales the control to [pressedScale] while pressed and back on release, on
 * [Springs.Snappy]. Replaces the ripple: pair with
 * `clickable(interactionSource = source, indication = null)`.
 */
@Composable
fun Modifier.pressable(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = 0.97f,
): Modifier {
    val pressed by rememberPressed(interactionSource)
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = Springs.Snappy,
        label = "pressScale",
    )
    return graphicsLayer { scaleX = scale; scaleY = scale }
}
