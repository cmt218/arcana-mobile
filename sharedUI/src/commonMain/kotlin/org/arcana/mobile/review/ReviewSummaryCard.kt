package org.arcana.mobile.review

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.arcana.mobile.data.ReviewDto
import org.arcana.mobile.theme.Ink
import org.arcana.mobile.theme.Moss
import org.arcana.mobile.ui.BodyText
import org.arcana.mobile.ui.Overline
import org.arcana.mobile.ui.TextLink
import org.arcana.mobile.ui.TonalWellRule
import org.arcana.mobile.ui.tonalWell

private const val COMMENT_PREVIEW_LINES = 3

/** The member's own review in the feed card's shape; [onEdit] opens the full editor. */
@Composable
fun ReviewSummaryCard(review: ReviewDto, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().tonalWell().padding(20.dp)) {
        Overline(text = "Your feedback · ${reviewDateLabel(review.createdAt)}", size = 10, color = Moss)
        if (review.comment.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            BodyText(
                text = review.comment, size = 15, color = Ink,
                maxLines = COMMENT_PREVIEW_LINES, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(TonalWellRule))
        Spacer(Modifier.height(12.dp))
        ReviewStatStrip(review)
        Spacer(Modifier.height(14.dp))
        TextLink(label = "Edit your feedback", onClick = onEdit, color = Moss, underline = false)
    }
}
