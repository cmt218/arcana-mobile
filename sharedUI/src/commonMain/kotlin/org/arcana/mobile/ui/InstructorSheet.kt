package org.arcana.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.arcana.mobile.discover.monogramFor
import org.arcana.mobile.review.whatMembersSayLabel
import org.arcana.mobile.theme.Ash
import org.arcana.mobile.theme.Graphite
import org.arcana.mobile.theme.Ink
import org.arcana.mobile.theme.Mist2
import org.arcana.mobile.theme.Moss

/** The instructor sheet: monogram, name, bio, and the way to what members
 *  said about them. Opened from the studio page and class detail. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstructorSheet(
    name: String,
    bio: String,
    reviewCount: Int,
    onSeeFeedback: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    ArcanaSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    modifier = Modifier.size(52.dp).clip(CircleShape).background(Mist2),
                    contentAlignment = Alignment.Center,
                ) {
                    CircleMonogram(text = monogramFor(name), fontSize = 16, color = Moss)
                }
                Heading3(text = name, size = 20, color = Ink)
            }
            Spacer(Modifier.height(16.dp))
            if (bio.isNotBlank()) BodyText(text = bio, size = 14, color = Graphite)
            else Caption(text = "No bio yet.", size = 13, color = Ash)
            if (reviewCount > 0 && onSeeFeedback != null) {
                Spacer(Modifier.height(20.dp))
                TextLink(
                    label = whatMembersSayLabel(reviewCount),
                    onClick = onSeeFeedback,
                    color = Moss,
                    underline = false,
                )
            }
        }
    }
}
