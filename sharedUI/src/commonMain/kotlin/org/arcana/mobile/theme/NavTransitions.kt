package org.arcana.mobile.theme

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import org.arcana.mobile.navigation.ArcanaDestination

/**
 * One definition for both NavHosts. Tabs swap instantly on Android (the
 * bar's dot carries the motion); iOS gives each tab its own NavHost, so
 * tab-to-tab there never applies. Every other destination presents like a
 * sheet that became a page: it rises from 12% of the height with a fade
 * while the screen beneath recedes to 96%, and leaves the same way, faster.
 * Vertical only: the default horizontal sibling slide reads wrong on iOS.
 */
object NavTransitions {
    private const val RISE_FRACTION = 0.12f
    private const val RECEDE_SCALE = 0.96f

    private fun isTabRoot(dest: NavDestination): Boolean =
        dest.hasRoute<ArcanaDestination.Home>() ||
            dest.hasRoute<ArcanaDestination.Schedule>() ||
            dest.hasRoute<ArcanaDestination.Discover>() ||
            dest.hasRoute<ArcanaDestination.Profile>()

    private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabToTab() =
        isTabRoot(initialState.destination) && isTabRoot(targetState.destination)

    fun AnimatedContentTransitionScope<NavBackStackEntry>.enter(): EnterTransition =
        if (tabToTab()) EnterTransition.None
        else slideInVertically(tween(Dur.Medium, easing = Ease.Emphasized)) { (it * RISE_FRACTION).toInt() } +
            fadeIn(tween(Dur.Medium, easing = Ease.Emphasized))

    fun AnimatedContentTransitionScope<NavBackStackEntry>.exit(): ExitTransition =
        if (tabToTab()) ExitTransition.None
        else scaleOut(tween(Dur.Medium, easing = Ease.Emphasized), targetScale = RECEDE_SCALE)

    fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnter(): EnterTransition =
        if (tabToTab()) EnterTransition.None
        else scaleIn(tween(Dur.Short, easing = Ease.Emphasized), initialScale = RECEDE_SCALE)

    fun AnimatedContentTransitionScope<NavBackStackEntry>.popExit(): ExitTransition =
        if (tabToTab()) ExitTransition.None
        else slideOutVertically(tween(Dur.Short, easing = Ease.Exit)) { (it * RISE_FRACTION).toInt() } +
            fadeOut(tween(Dur.Short, easing = Ease.Exit))
}
