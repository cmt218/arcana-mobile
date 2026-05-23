package org.arcana.mobile.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import arcana.composeapp.generated.resources.Res
import arcana.composeapp.generated.resources.wordmark
import org.jetbrains.compose.resources.painterResource

/** Native aspect ratio of `wordmark.png` (5421 × 1110, cropped tight to the text). */
private const val WORDMARK_ASPECT = 4.88f

/**
 * Renders the arcana dot-matrix wordmark from `wordmark.png`. The PNG is
 * cropped tight to the text bbox, so the rendered image is flush left/right
 * without surrounding padding — drop it straight into any layout.
 *
 * Pass [tint] to recolor every dot a single hue (Moss on Stone surfaces);
 * leave null to keep the asset's native stone + lime dots (used on the splash).
 */
@Composable
fun WordmarkLogo(
    modifier: Modifier = Modifier,
    tint: Color? = null,
) {
    Image(
        painter = painterResource(Res.drawable.wordmark),
        contentDescription = "Arcana",
        contentScale = ContentScale.Fit,
        colorFilter = tint?.let { ColorFilter.tint(it) },
        modifier = modifier.aspectRatio(WORDMARK_ASPECT),
    )
}
