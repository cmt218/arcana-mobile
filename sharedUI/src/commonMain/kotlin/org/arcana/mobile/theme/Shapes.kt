package org.arcana.mobile.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** The five radii the app uses. Eleven literals collapsed to these. */
object ArcanaShapes {
    val Chip: Shape = RoundedCornerShape(12.dp)
    val Card: Shape = RoundedCornerShape(16.dp)
    val Hero: Shape = RoundedCornerShape(14.dp)
    val Sheet: Shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val Pill: Shape = CircleShape
}
