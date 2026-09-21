package org.arcana.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.arcana.mobile.theme.Ink
import org.arcana.mobile.theme.MossDeep
import org.arcana.mobile.ui.SPLASH_WORDMARK_SETTLED_MS
import org.arcana.mobile.ui.SplashWordmark

/** Time on the settled wordmark before the fade, so it reads as arrived. */
private const val SPLASH_HOLD_MS = 450

/** The splash's exit: the settled mark fades, eased in and out, while Home comes up through it. */
const val SPLASH_EXIT_MS: Int = 550

/**
 * How long the splash stays before its exit: the wordmark's redraw, then a
 * short hold on the settled mark. A fixed timer, sized to cover Home's first fetch
 * on most launches (docs/perf/README.md "Splash length"). App.kt and the iOS shell read it.
 */
const val SPLASH_MIN_DISPLAY_MS: Long = (SPLASH_WORDMARK_SETTLED_MS + SPLASH_HOLD_MS).toLong()

/**
 * Cold-launch brand moment: on a deep moss field with a soft vignette, the
 * wordmark redraws row by row ([SplashWordmark]), then breathes.
 */
@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MossDeep)
            .drawWithContent {
                drawContent()
                // Radial vignette: transparent in the middle, fading to MossDeep
                // at ~78% of the longer radius, then to Ink at the corners.
                // Mirrors the `radial-gradient(120% 70% at 50% 50%, transparent
                // 30%, MossDeep 78%, Ink 100%)` from the handoff README.
                val maxDim = maxOf(size.width, size.height)
                drawRect(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0.30f to Color.Transparent,
                            0.78f to MossDeep,
                            1.00f to Ink,
                        ),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = maxDim * 0.6f,
                    )
                )
            },
    ) {
        SplashWordmark(
            modifier = Modifier.fillMaxSize(),
        )
    }
}
