package org.arcana.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import org.arcana.mobile.theme.Dur
import org.arcana.mobile.theme.Ease

// One distance for both, so the tall nudge and the short bar read as the same gesture.
private val RISE_DISTANCE = 8.dp

/**
 * Snackbars and nudges rise 8 dp into place and drop out, on the app's curves.
 * Set [collapse] on a surface embedded in flow layout (not absolutely positioned)
 * so its space closes together with the fade, rather than after it.
 */
@Composable
fun TransientSurface(
    visible: Boolean,
    modifier: Modifier = Modifier,
    collapse: Boolean = false,
    content: @Composable () -> Unit,
) {
    val risePx = with(LocalDensity.current) { RISE_DISTANCE.roundToPx() }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(Dur.Short, easing = Ease.Emphasized)) +
            slideInVertically(tween(Dur.Short, easing = Ease.Emphasized)) { risePx } +
            if (collapse) expandVertically(tween(Dur.Short, easing = Ease.Emphasized)) else EnterTransition.None,
        exit = fadeOut(tween(Dur.Quick, easing = Ease.Exit)) +
            slideOutVertically(tween(Dur.Quick, easing = Ease.Exit)) { risePx } +
            if (collapse) shrinkVertically(tween(Dur.Short, easing = Ease.Exit)) else ExitTransition.None,
    ) { content() }
}
