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
import org.arcana.mobile.ui.DANCE_DURATION_MS
import org.arcana.mobile.ui.DANCE_SETTLE_STAGGER_MS
import org.arcana.mobile.ui.DancingWordmark

/**
 * Minimum time the splash stays on screen. Derived from the dance constants
 * so the splash always plays long enough for the last-delayed cell to settle:
 * `settleStagger + duration` is when the most-delayed cell finishes its
 * dance, and we add a 200 ms tail so the breath pulse lights on the wordmark
 * before the 300 ms exit fade starts. App.kt reads this.
 */
const val SPLASH_MIN_DISPLAY_MS: Long =
    (DANCE_SETTLE_STAGGER_MS + DANCE_DURATION_MS + 200).toLong()

/**
 * Cold-launch brand moment. A grid of dim stone-colored dots flickers, then
 * settles into the Arcana wordmark on a deep moss field with a soft vignette.
 * See `design_handoff_splash_screen/README.md` in the design handoff bundle
 * for the canonical behavior.
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
        DancingWordmark(
            modifier = Modifier.fillMaxSize(),
        )
    }
}
