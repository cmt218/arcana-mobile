// The app's one motion hand. Every duration, easing and spring is drawn from here.
package org.arcana.mobile.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

object Ease {
    /** Everything entering. */
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    /** Everything leaving: fast out, no lingering. */
    val Exit = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
}

object Dur {
    const val Quick = 120
    const val Short = 200
    const val Medium = 340
    const val Long = 480
}

object Springs {
    private const val SNAPPY_DAMPING = 0.85f
    private const val SETTLE_DAMPING = 0.90f
    private const val KICK_DAMPING = 0.65f

    val Snappy: SpringSpec<Float> = snappy()
    val Settle: SpringSpec<Float> = settle()
    /** The one bouncy spring; nowhere else. */
    val Kick: SpringSpec<Float> = kick()

    fun <T> snappy(): SpringSpec<T> = spring(dampingRatio = SNAPPY_DAMPING, stiffness = Spring.StiffnessMedium)
    fun <T> settle(): SpringSpec<T> = spring(dampingRatio = SETTLE_DAMPING, stiffness = Spring.StiffnessMediumLow)
    fun <T> kick(): SpringSpec<T> = spring(dampingRatio = KICK_DAMPING, stiffness = Spring.StiffnessMedium)
}
