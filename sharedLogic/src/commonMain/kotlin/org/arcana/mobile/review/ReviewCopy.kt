package org.arcana.mobile.review

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import org.arcana.mobile.data.FeedbackScopeDto
import org.arcana.mobile.data.ReviewDto
import org.arcana.mobile.data.ReviewPromptDto
import org.arcana.mobile.schedule.wallClock

/** The answer scales, in server order. Index + 1 is the stored value. */
val AGAIN_OPTIONS = listOf("yes" to "Yes", "maybe" to "Maybe", "no" to "No")
val INTENSITY_LABELS = listOf("Easy", "Moderate", "Hard")
const val SCORE_MAX = 5
const val COMMENT_MAX_LENGTH = 1000

fun againLabel(value: String): String = AGAIN_OPTIONS.firstOrNull { it.first == value }?.second ?: value

fun intensityLabel(value: Int?): String? = value?.let { INTENSITY_LABELS.getOrNull(it - 1) }

/** "Yesterday · Soto Method": the caps line over the Home card. */
fun reviewPromptEyebrow(prompt: ReviewPromptDto, today: LocalDate): String {
    val day = runCatching { relativeDayLabel(wallClock(prompt.endAt).date, today) }.getOrNull()
    return listOfNotNull(day, prompt.brand.name.takeIf { it.isNotBlank() }).joinToString(" · ")
}

/** "Sculpt 50 with Jess"; the instructor clause drops when unknown. */
fun classWithInstructor(classType: String, instructor: String?): String =
    classType + instructor?.takeIf { it.isNotBlank() }?.let { " with $it" }.orEmpty()

/** "Sculpt 50 with Jess at Soto Method". */
fun classLine(classType: String, instructor: String?, brand: String): String =
    "${classWithInstructor(classType, instructor)} at $brand"

/** Today, Yesterday, then "Sat Sep 12". */
fun relativeDayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.minus(1, DateTimeUnit.DAY) -> "Yesterday"
    else -> {
        val dow = date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.titlecase() }
        val mon = date.month.name.take(3).lowercase().replaceFirstChar { it.titlecase() }
        "$dow $mon ${date.day}"
    }
}

/** The feed header: "6 reviews · 5 would take a class again · usually Hard ·
 *  Instructors 4.8 · Classes 4.5 · Studio 4.7". Parts with no data drop. */
fun scopeStatsLine(scope: FeedbackScopeDto): String {
    if (scope.count == 0) return "No feedback here yet."
    val parts = mutableListOf(if (scope.count == 1) "1 review" else "${scope.count} reviews")
    if (scope.again.yes > 0) {
        parts += if (scope.again.yes == 1) "1 would take a class again" else "${scope.again.yes} would take a class again"
    }
    intensityLabel(usualIntensity(scope))?.let { parts += "usually $it" }
    scope.instructorAvg?.let { parts += "Instructors ${oneDecimal(it)}" }
    scope.classAvg?.let { parts += "Classes ${oneDecimal(it)}" }
    scope.studioAvg?.let { parts += "Studio ${oneDecimal(it)}" }
    return parts.joinToString(" · ")
}

/** The one hint that leads into a feed, wherever it sits: what it is about is
 *  said by where it is placed (under the class, the instructor, the location). */
fun whatMembersSayLabel(count: Int): String = "$WHAT_MEMBERS_SAY · ${reviewCountLabel(count)}"

const val WHAT_MEMBERS_SAY = "What members say"

fun reviewCountLabel(count: Int): String = if (count == 1) "1 review" else "$count reviews"

/** Whether a feed opens with its averages. One review is its own average, so
 *  it takes two; and the all-studios feed pools every studio, whose averages
 *  describe nothing a member can act on. */
fun showsAverages(scope: FeedbackScopeDto): Boolean = scope.type != "all" && scope.count > 1

/** The caps line over a feed's averages. */
fun averagesHeading(count: Int): String = "Average of $count reviews"

/** How much of a scope would take it again, 0.0 to 1.0: a yes counts whole, a
 *  maybe half, a no nothing. Two yeses and two maybes read 0.75, not "50%":
 *  a maybe is not a no. */
fun againScore(scope: FeedbackScopeDto): Double? {
    val total = scope.again.yes + scope.again.maybe + scope.again.no
    return if (total == 0) null else (scope.again.yes + scope.again.maybe * 0.5) / total
}

/** A scope's "again" answers as the one mark they add up to: yes from two
 *  thirds, no up to one third, maybe between. */
fun againOnBalance(scope: FeedbackScopeDto): String? = againScore(scope)?.let { score ->
    when {
        score >= 2.0 / 3 -> "yes"
        score <= 1.0 / 3 -> "no"
        else -> "maybe"
    }
}

/** The average intensity as the 1-to-3 answer it rounds to ("usually Hard"). */
fun usualIntensity(scope: FeedbackScopeDto): Int? = scope.intensityAvg?.roundHalfUp()

/** "4.8": an average as the feed prints it, rounded half up. */
fun oneDecimal(value: Double): String {
    val tenths = (value * 10 + 0.5).toInt()
    return "${tenths / 10}.${tenths % 10}"
}

/** The answers on one line under a feed item: "Would take it again · Hard ·
 *  Instructor 5 · Class 4 · Studio 4". */
fun reviewAnswersLine(review: ReviewDto): String {
    val parts = mutableListOf(
        when (review.again) {
            "yes" -> "Would take it again"
            "maybe" -> "Might take it again"
            "no" -> "Would not take it again"
            else -> review.again
        },
    )
    intensityLabel(review.intensity)?.let { parts += it }
    review.instructorScore?.let { parts += "Instructor $it" }
    review.classScore?.let { parts += "Class $it" }
    review.studioScore?.let { parts += "Studio $it" }
    return parts.joinToString(" · ")
}

/** "Sep 12" from a server timestamp; the raw prefix when it will not parse. */
fun reviewDateLabel(createdAt: String): String = runCatching {
    val date = wallClock(createdAt).date
    val mon = date.month.name.take(3).lowercase().replaceFirstChar { it.titlecase() }
    "$mon ${date.day}"
}.getOrElse { createdAt.take(10) }

private fun Double.roundHalfUp(): Int = (this + 0.5).toInt()
