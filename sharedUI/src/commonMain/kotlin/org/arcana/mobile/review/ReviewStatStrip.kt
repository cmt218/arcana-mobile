package org.arcana.mobile.review

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.arcana.mobile.data.FeedbackScopeDto
import org.arcana.mobile.data.ReviewDto
import org.arcana.mobile.theme.Arcana
import org.arcana.mobile.theme.Charcoal
import org.arcana.mobile.theme.Ink
import org.arcana.mobile.theme.MossLight
import org.arcana.mobile.ui.Caption
import org.arcana.mobile.ui.opticallyCentredCapsVertical

private val VALUE_HEIGHT = 24.dp
private val SCORE_SIZE = 22.sp
private val AVERAGE_SIZE = 20.sp
private val MARK_SIZE = 20.dp
private val FLAME_SIZE = 16.sp
private val Unanswered = MossLight.copy(alpha = 0.35f)

// Three flames need more room than a numeral. The widths are the same on every
// card, so the columns still line up down the feed.
private const val COLUMN = 1f
private const val INTENSITY_COLUMN = 1.5f

/**
 * A review's five answers in five fixed columns, so the same answer sits in
 * the same place on every card and a member can compare down the feed: again
 * (a mark), intensity (one to three flames), then the three 1-to-5 scores as
 * numerals, each over its label. An unanswered column keeps its place with a
 * hollow mark. A screen reader hears the strip as one sentence.
 */
@Composable
fun ReviewStatStrip(review: ReviewDto, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = reviewAnswersLine(review) },
        verticalAlignment = Alignment.Bottom,
    ) {
        StatColumn("Again") { AgainMark(review.again, MARK_SIZE) }
        StatColumn("Intensity", INTENSITY_COLUMN) { Flames(review.intensity) }
        StatColumn("Instructor") { Numeral(review.instructorScore?.toString(), SCORE_SIZE) }
        StatColumn("Class") { Numeral(review.classScore?.toString(), SCORE_SIZE) }
        StatColumn("Studio") { Numeral(review.studioScore?.toString(), SCORE_SIZE) }
    }
}

/**
 * The same five columns for a whole scope, so the averages at the top of a
 * feed line up with the answers on every card under them: the one mark the
 * answers add up to, the usual intensity, and the three average scores.
 */
@Composable
fun ReviewAverageStrip(scope: FeedbackScopeDto, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = scopeStatsLine(scope) },
        verticalAlignment = Alignment.Bottom,
    ) {
        StatColumn("Again") { againOnBalance(scope)?.let { AgainMark(it, MARK_SIZE) } ?: NoAnswer() }
        StatColumn("Intensity", INTENSITY_COLUMN) { Flames(usualIntensity(scope)) }
        StatColumn("Instructor") { Numeral(scope.instructorAvg?.let(::oneDecimal), AVERAGE_SIZE) }
        StatColumn("Class") { Numeral(scope.classAvg?.let(::oneDecimal), AVERAGE_SIZE) }
        StatColumn("Studio") { Numeral(scope.studioAvg?.let(::oneDecimal), AVERAGE_SIZE) }
    }
}

@Composable
private fun RowScope.StatColumn(label: String, weight: Float = COLUMN, value: @Composable () -> Unit) {
    Column(modifier = Modifier.weight(weight), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.height(VALUE_HEIGHT), contentAlignment = Alignment.Center) { value() }
        Spacer(Modifier.height(6.dp))
        Caption(text = label, size = 10, color = Charcoal)
    }
}

@Composable
private fun Flames(intensity: Int?) {
    if (intensity == null) NoAnswer() else IntensityFlames(intensity, FLAME_SIZE)
}

/** The hollow mark that holds an unanswered column's place. */
@Composable
private fun NoAnswer() {
    Box(Modifier.size(7.dp).clip(CircleShape).border(1.5.dp, Unanswered, CircleShape))
}

/** A League Spartan figure, or the hollow "no answer" mark. */
@Composable
private fun Numeral(value: String?, size: TextUnit) {
    if (value == null) {
        NoAnswer()
        return
    }
    Text(
        text = value,
        maxLines = 1,
        // League Spartan figures stand at cap height, so they sit high like caps.
        modifier = Modifier.opticallyCentredCapsVertical(size),
        style = TextStyle(
            fontFamily = Arcana.fonts.display,
            fontWeight = FontWeight.Bold,
            fontSize = size,
            lineHeight = size,
            color = Ink,
        ),
    )
}
