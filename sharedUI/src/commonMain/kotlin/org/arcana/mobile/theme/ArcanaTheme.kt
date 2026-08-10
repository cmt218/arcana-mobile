package org.arcana.mobile.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Theme entry point. Resolves the Arcana font families once and exposes them
 * (plus a Material3 light color scheme keyed to the Stone palette) to the tree.
 * Screens read fonts via `Arcana.fonts`.
 */
object Arcana {
    val fonts: ArcanaFonts
        @Composable get() = LocalArcanaFonts.current
}

private val LocalArcanaFonts = staticCompositionLocalOf<ArcanaFonts> {
    error("ArcanaTheme not provided — wrap your composable in ArcanaTheme { … }")
}

@Composable
fun ArcanaTheme(content: @Composable () -> Unit) {
    val fonts = arcanaFonts()
    val colors = lightColorScheme(
        primary = Moss,
        onPrimary = Stone,
        secondary = Lime,
        onSecondary = Ink,
        tertiary = MossLight,
        background = Stone,
        onBackground = Ink,
        surface = Paper,
        onSurface = Ink,
        surfaceVariant = Mist2,
        onSurfaceVariant = Ash,
        outline = Mist,
        error = Danger,
        onError = Stone,
    )
    CompositionLocalProvider(
        LocalArcanaFonts provides fonts,
        LocalContentColor provides Ink,
    ) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}
