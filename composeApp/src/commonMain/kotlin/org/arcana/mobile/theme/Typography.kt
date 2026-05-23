package org.arcana.mobile.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import arcana.composeapp.generated.resources.Res
import arcana.composeapp.generated.resources.cormorant_garamond_italic
import arcana.composeapp.generated.resources.cormorant_garamond_medium_italic
import arcana.composeapp.generated.resources.dm_sans_bold
import arcana.composeapp.generated.resources.dm_sans_medium
import arcana.composeapp.generated.resources.dm_sans_regular
import arcana.composeapp.generated.resources.league_spartan_bold
import arcana.composeapp.generated.resources.league_spartan_medium
import arcana.composeapp.generated.resources.league_spartan_semibold
import org.jetbrains.compose.resources.Font

/**
 * Arcana type families — matches the typography doc
 * (https://docs.google.com/document/d/1xsKxfeA0Y3NCT_AWWGv3dYJu0agO0qLsL5kPr5Xjblg).
 * Three families only: League Spartan (display), DM Sans (body + nav + microcopy),
 * Cormorant Garamond italic (sparing accent).
 */

@Composable
fun LeagueSpartan(): FontFamily = FontFamily(
    Font(Res.font.league_spartan_medium,   weight = FontWeight.Medium),
    Font(Res.font.league_spartan_semibold, weight = FontWeight.SemiBold),
    Font(Res.font.league_spartan_bold,     weight = FontWeight.Bold),
)

@Composable
fun DmSans(): FontFamily = FontFamily(
    Font(Res.font.dm_sans_regular, weight = FontWeight.Normal),
    Font(Res.font.dm_sans_medium,  weight = FontWeight.Medium),
    Font(Res.font.dm_sans_bold,    weight = FontWeight.Bold),
)

@Composable
fun CormorantGaramond(): FontFamily = FontFamily(
    Font(Res.font.cormorant_garamond_italic,        weight = FontWeight.Normal, style = FontStyle.Italic),
    Font(Res.font.cormorant_garamond_medium_italic, weight = FontWeight.Medium, style = FontStyle.Italic),
)

/** Bundle of the three families, resolved once and provided through [ArcanaTheme]. */
class ArcanaFonts internal constructor(
    val display: FontFamily,
    val body: FontFamily,
    val accent: FontFamily,
)

@Composable
fun arcanaFonts(): ArcanaFonts = ArcanaFonts(
    display = LeagueSpartan(),
    body = DmSans(),
    accent = CormorantGaramond(),
)
