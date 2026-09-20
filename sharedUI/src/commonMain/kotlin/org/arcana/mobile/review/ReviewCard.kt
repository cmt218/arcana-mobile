package org.arcana.mobile.review

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.arcana.mobile.data.ReviewDto
import org.arcana.mobile.theme.Charcoal
import org.arcana.mobile.theme.Dur
import org.arcana.mobile.theme.Ink
import org.arcana.mobile.theme.Moss
import org.arcana.mobile.ui.ArcanaIcons
import org.arcana.mobile.ui.ArcanaMultilineTextField
import org.arcana.mobile.ui.BodyText
import org.arcana.mobile.ui.Caption
import org.arcana.mobile.ui.ChoicePills
import org.arcana.mobile.ui.Heading3
import org.arcana.mobile.ui.Overline
import org.arcana.mobile.ui.ScoreDots
import org.arcana.mobile.ui.SettlingCta
import org.arcana.mobile.ui.TextLink
import org.arcana.mobile.ui.rememberHaptics
import org.arcana.mobile.ui.tonalWell
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** What step three names: the three things a member recommends, or not. */
data class ReviewSubjects(val instructor: String?, val classType: String, val brand: String)

private const val QUESTION_AGAIN_HOME = "Would you take it again?"
private const val QUESTION_AGAIN = "Would you take this again?"
private const val QUESTION_INTENSITY = "How hard was it?"
private const val QUESTION_RECOMMEND = "Would you recommend"
// Short enough for the field's single-line overline on a 402pt phone.
private const val COMMENT_LABEL = "Anything else?"
private const val COMMENT_HELPER = "Other members will see this."
// "Your feedback is saved" overflows the pill on a 360dp phone.
private const val SAVED = "Feedback saved"
private const val REFUSED = "This class can't be reviewed."
// Long enough to read "saved" and see the check land; the booking sheet holds 550ms.
private const val SAVED_HOLD_MS = 650L
private val MARK_SIZE = 16.dp
private val FLAME_SIZE = 16.sp

/** How much room the card takes. Both sit in the same tonal well. */
enum class ReviewCardStyle {
    /** The Home prompt and a class the member attended. */
    Card,
    /** Under a Reservations row: tighter, with a smaller question. */
    Inline,
}

/**
 * The review card (spec 5.4): three states saving on every tap. [surface]
 * is home | past | detail and is stored on the review; an [initialReview]
 * opens the card in edit mode. [onStarted] fires once step one has created
 * the review, [onDone] when the card closes (class detail keeps it open), and
 * [onSaveFailed] with a transport code when a save did not land: the screen
 * shows the notice, the card keeps everything the member entered.
 */
@Composable
fun ReviewCard(
    bookingId: Int,
    surface: String,
    initialReview: ReviewDto?,
    subjects: ReviewSubjects,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    style: ReviewCardStyle = ReviewCardStyle.Card,
    eyebrow: String? = null,
    title: String? = null,
    onNotNow: (() -> Unit)? = null,
    onStarted: (ReviewDto) -> Unit = {},
    onSaveFailed: (String) -> Unit = {},
) {
    val vm = koinViewModel<ReviewViewModel>(key = "review-$bookingId") {
        parametersOf(bookingId, surface, initialReview)
    }
    val state by vm.uiState.collectAsState()
    val haptics = rememberHaptics()
    LaunchedEffect(bookingId) { vm.onShown() }
    // The saved button gets its beat on screen before the parent takes the card away.
    val finished by rememberUpdatedState(onDone)
    LaunchedEffect(state.step) {
        if (state.step == ReviewStep.Done) {
            delay(SAVED_HOLD_MS)
            finished()
        }
    }
    val started by rememberUpdatedState(onStarted)
    LaunchedEffect(state.review?.id) {
        if (initialReview == null) state.review?.let { started(it) }
    }
    val failed by rememberUpdatedState(onSaveFailed)
    LaunchedEffect(state.failureCode) {
        state.failureCode?.let { code ->
            haptics.reject()
            failed(code)
            vm.failureShown()
        }
    }
    // One confirm per close. Seeded from the count so a row scrolling back in stays quiet.
    var closesFelt by remember { mutableIntStateOf(state.doneCount) }
    LaunchedEffect(state.doneCount) {
        if (state.doneCount > closesFelt) {
            closesFelt = state.doneCount
            haptics.confirm()
        }
    }
    // The closing card keeps its last step on screen until the parent removes it.
    var shownStep by remember { mutableStateOf(state.step) }
    if (state.step != ReviewStep.Done) shownStep = state.step

    val compact = style == ReviewCardStyle.Inline
    val questionSize = if (compact) 16 else 20
    Column(
        modifier = modifier
            .fillMaxWidth()
            .tonalWell()
            .padding(if (compact) 16.dp else 20.dp),
    ) {
        if (eyebrow != null) {
            Overline(text = eyebrow, size = 10, color = Moss)
            Spacer(Modifier.height(if (title != null) 6.dp else 14.dp))
        }
        if (title != null) {
            Caption(text = title, size = 12, color = Charcoal, maxLines = 2)
            Spacer(Modifier.height(10.dp))
        }
        when {
            state.refusedCode != null -> BodyText(REFUSED, size = 14, color = Ink)
            state.editing -> EditBody(state, subjects, vm, haptics::selection)
            else -> AnimatedContent(
                targetState = shownStep,
                // Out, then in: two questions overlaid mid-fade read as a glitch.
                transitionSpec = { fadeIn(tween(Dur.Short, delayMillis = Dur.Quick)) togetherWith fadeOut(tween(Dur.Quick)) },
                label = "reviewStep",
            ) { step ->
                when (step) {
                    ReviewStep.Again -> AgainBody(
                        question = if (surface == "home") QUESTION_AGAIN_HOME else QUESTION_AGAIN,
                        questionSize = questionSize,
                        selected = state.again,
                        onSelect = { haptics.selection(); vm.answerAgain(it) },
                        onNotNow = onNotNow,
                    )
                    ReviewStep.Intensity -> IntensityBody(
                        questionSize = questionSize,
                        selected = state.review?.intensity,
                        settled = state.settled,
                        onSelect = { haptics.selection(); vm.answerIntensity(it) },
                        onDone = vm::done,
                    )
                    else -> RecommendBody(state, questionSize, subjects, vm, haptics::selection)
                }
            }
        }
    }
}

@Composable
private fun AgainBody(
    question: String,
    questionSize: Int,
    selected: String?,
    onSelect: (String) -> Unit,
    onNotNow: (() -> Unit)?,
) {
    Column {
        Heading3(text = question, size = questionSize, color = Ink)
        Spacer(Modifier.height(14.dp))
        ChoicePills(
            options = AGAIN_OPTIONS.map { it.second },
            selectedIndex = AGAIN_OPTIONS.indexOfFirst { it.first == selected }.takeIf { it >= 0 },
            onSelect = { onSelect(AGAIN_OPTIONS[it].first) },
            leading = { AgainMark(AGAIN_OPTIONS[it].first, MARK_SIZE) },
        )
        if (onNotNow != null) {
            Spacer(Modifier.height(12.dp))
            TextLink(label = "Not now", onClick = onNotNow, color = Moss, underline = false)
        }
    }
}

@Composable
private fun IntensityBody(
    questionSize: Int,
    selected: Int?,
    settled: Boolean,
    onSelect: (Int) -> Unit,
    onDone: () -> Unit,
) {
    Column {
        Heading3(text = QUESTION_INTENSITY, size = questionSize, color = Ink)
        Spacer(Modifier.height(14.dp))
        ChoicePills(
            options = INTENSITY_LABELS,
            selectedIndex = selected?.minus(1),
            onSelect = { onSelect(it + 1) },
            leading = { IntensityFlames(it + 1, FLAME_SIZE) },
            showLabels = false,
        )
        Spacer(Modifier.height(12.dp))
        TextLink(
            label = if (settled) SAVED else "Done",
            onClick = onDone,
            color = Moss,
            icon = if (settled) ArcanaIcons.Check else ArcanaIcons.ArrowRight,
            underline = false,
        )
    }
}

@Composable
private fun RecommendBody(
    state: ReviewCardUiState,
    questionSize: Int,
    subjects: ReviewSubjects,
    vm: ReviewViewModel,
    tick: () -> Unit,
) {
    Column {
        Heading3(text = "$QUESTION_RECOMMEND…", size = questionSize, color = Ink)
        Spacer(Modifier.height(6.dp))
        ScoreRows(state, subjects, vm, tick)
        CommentAndDone(state, vm)
    }
}

@Composable
private fun EditBody(state: ReviewCardUiState, subjects: ReviewSubjects, vm: ReviewViewModel, tick: () -> Unit) {
    val review = state.review ?: return
    Column {
        Overline(text = QUESTION_AGAIN, size = 10, color = Charcoal)
        Spacer(Modifier.height(8.dp))
        ChoicePills(
            options = AGAIN_OPTIONS.map { it.second },
            selectedIndex = AGAIN_OPTIONS.indexOfFirst { it.first == review.again }.takeIf { it >= 0 },
            onSelect = { tick(); vm.answerAgain(AGAIN_OPTIONS[it].first) },
            leading = { AgainMark(AGAIN_OPTIONS[it].first, MARK_SIZE) },
        )
        Spacer(Modifier.height(16.dp))
        Overline(text = QUESTION_INTENSITY, size = 10, color = Charcoal)
        Spacer(Modifier.height(8.dp))
        ChoicePills(
            options = INTENSITY_LABELS,
            selectedIndex = review.intensity?.minus(1),
            onSelect = { tick(); vm.answerIntensity(it + 1) },
            leading = { IntensityFlames(it + 1, FLAME_SIZE) },
            showLabels = false,
        )
        Spacer(Modifier.height(16.dp))
        Overline(text = "$QUESTION_RECOMMEND…", size = 10, color = Charcoal)
        ScoreRows(state, subjects, vm, tick)
        CommentAndDone(state, vm)
    }
}

@Composable
private fun ScoreRows(state: ReviewCardUiState, subjects: ReviewSubjects, vm: ReviewViewModel, tick: () -> Unit) {
    val review = state.review
    // The saved review names its own combination (the folded class type, the
    // brand); a list row only knows the session and the studio row.
    val instructor = review?.instructor?.name ?: subjects.instructor
    val rows = listOfNotNull(
        instructor?.takeIf { it.isNotBlank() }?.let { Triple(it, ScoreTarget.Instructor, review?.instructorScore) },
        Triple(review?.classType?.label?.takeIf { it.isNotBlank() } ?: subjects.classType, ScoreTarget.Class, review?.classScore),
        Triple(review?.brand?.name?.takeIf { it.isNotBlank() } ?: subjects.brand, ScoreTarget.Studio, review?.studioScore),
    )
    rows.forEach { (label, target, value) ->
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BodyText(text = label, size = 14, color = Ink, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            ScoreDots(
                value = value,
                onSelect = { tick(); vm.setScore(target, it) },
                label = label,
            )
        }
    }
}

@Composable
private fun CommentAndDone(state: ReviewCardUiState, vm: ReviewViewModel) {
    Spacer(Modifier.height(20.dp))
    // One line until the member needs more, three at most, then it scrolls.
    ArcanaMultilineTextField(
        label = COMMENT_LABEL,
        value = state.comment,
        onValueChange = vm::setComment,
        maxLength = COMMENT_MAX_LENGTH,
        placeholder = COMMENT_HELPER,
        minLines = 1,
        maxLines = 3,
        idleColor = Charcoal,
        modifier = Modifier.onFocusChanged { if (!it.isFocused) vm.saveComment() },
    )
    Spacer(Modifier.height(16.dp))
    // Never greyed and never relabelled: it sweeps if the save is slow, rests
    // once the member taps Done, and wakes on the next change.
    val focus = LocalFocusManager.current
    SettlingCta(
        label = "Done",
        settledLabel = SAVED,
        settled = state.settled,
        busy = state.finishing,
        // The keyboard would sit over the saved state, and over the notice if it fails.
        onClick = { focus.clearFocus(); vm.done() },
    )
}
