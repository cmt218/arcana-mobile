package org.arcana.mobile.ui

import androidx.compose.ui.graphics.Color
import org.arcana.mobile.theme.Moss

/** Parse `#RRGGBB` (server payload format); null on empty or invalid input. */
fun parseHexColor(hex: String): Color? {
    if (hex.length != 7 || !hex.startsWith("#")) return null
    return try {
        Color(hex.substring(1, 3).toInt(16), hex.substring(3, 5).toInt(16), hex.substring(5, 7).toInt(16))
    } catch (_: NumberFormatException) {
        null
    }
}

/** A studio's accent from its `primary_color`, Moss when it has none. */
fun studioColorFor(primaryColor: String): Color = parseHexColor(primaryColor) ?: Moss
