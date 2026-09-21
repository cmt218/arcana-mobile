package org.arcana.mobile.review

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.arcana.mobile.theme.Charcoal
import org.arcana.mobile.theme.Moss
import org.arcana.mobile.ui.ArcanaIcons
import org.arcana.mobile.ui.Overline
import org.arcana.mobile.ui.StrokeIcon
import org.arcana.mobile.ui.pressable
import org.arcana.mobile.ui.tonalWell

private const val LABEL_SIZE = 11
private val RowShape = RoundedCornerShape(14.dp)

/**
 * The way into a feed from the thing it is about. It wears the tonal well, the
 * look of everything member feedback, so it reads as reviews at a glance and
 * as a control rather than a caption.
 */
@Composable
fun MembersSayRow(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressable(source, pressedScale = 0.99f)
            .tonalWell(RowShape)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // No cap nudge: DM Sans caps already sit on the line's centre (measured).
        Overline(
            text = WHAT_MEMBERS_SAY,
            size = LABEL_SIZE,
            color = Moss,
            modifier = Modifier.weight(1f),
        )
        Overline(
            text = reviewCountLabel(count),
            size = LABEL_SIZE,
            color = Charcoal,
        )
        // decorative — the row is the control and its text names it.
        StrokeIcon(icon = ArcanaIcons.ArrowRight, size = 14.dp, tint = Moss)
    }
}
