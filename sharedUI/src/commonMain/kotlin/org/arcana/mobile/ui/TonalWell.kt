package org.arcana.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import org.arcana.mobile.theme.Moss
import org.arcana.mobile.theme.MossLight

val TonalWellShape: Shape = RoundedCornerShape(20.dp)

private val WellFill = Moss.copy(alpha = 0.11f)
private val WellEdge = MossLight.copy(alpha = 0.42f)

/** The hairline inside a well: its own edge, drawn across. */
val TonalWellRule = WellEdge

/**
 * The container for everything member feedback: recessed into the atmosphere
 * (a Moss tint and a soft MossLight edge, no shadow) where a card is lifted
 * off it. One look for the prompt, the feed and the Discover row, so a member
 * learns to read it as "reviews".
 */
fun Modifier.tonalWell(shape: Shape = TonalWellShape): Modifier =
    clip(shape).background(WellFill).border(1.dp, WellEdge, shape)
