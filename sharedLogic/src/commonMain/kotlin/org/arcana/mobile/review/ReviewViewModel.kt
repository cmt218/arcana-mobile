package org.arcana.mobile.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import org.arcana.mobile.analytics.Telemetry
import org.arcana.mobile.data.ReviewDto
import org.arcana.mobile.networking.ReviewApi
import org.arcana.mobile.networking.ReviewError
import org.arcana.mobile.networking.transportFailureCode

enum class ReviewStep { Again, Intensity, Recommend, Done }

/** Which 1-to-5 row a score belongs to; the name is the server field. */
enum class ScoreTarget(val field: String) {
    Instructor("instructor_score"), Class("class_score"), Studio("studio_score"),
}

data class ReviewCardUiState(
    /** What the card shows. A tap lands here at once; the request follows. Null until step one. */
    val review: ReviewDto? = null,
    val step: ReviewStep = ReviewStep.Again,
    /** Opened on an existing review: every control shows at once. */
    val editing: Boolean = false,
    /** The comment as typed; saved on Done (and when the field loses focus). */
    val comment: String = "",
    /** Step one's pick while its POST is in flight: there is no review to show it on yet. */
    val pendingAgain: String? = null,
    val saving: Boolean = false,
    /** Done was tapped and its last save is still in flight. */
    val finishing: Boolean = false,
    /** Nothing has changed since the member last tapped Done: the button reads as saved. */
    val settled: Boolean = false,
    /** Counts every close, so the screen can mark each one (a haptic) exactly once. */
    val doneCount: Int = 0,
    /** Why the last save did not land (`connection_failed` | `server_failed`); the
     *  screen shows it once as a notice. What the member typed is never touched by it. */
    val failureCode: String? = null,
    /** A server refusal by name (`not_eligible`); the card explains and closes. */
    val refusedCode: String? = null,
) {
    val started: Boolean get() = review != null
    val commentDirty: Boolean get() = comment.trim() != (review?.comment ?: "").trim()
    val again: String? get() = review?.again ?: pendingAgain
}

/**
 * One review card, keyed by booking. Every answer is one request: step one
 * POSTs (creating the review), everything after it PATCHes, so a one-tap
 * review is already complete. A tap shows at once and its request follows, in
 * order; a failed one falls back to the last answer the server confirmed. An
 * [initial] review opens the card in edit mode. With [keepOpen] (class detail)
 * Done leaves every answer on screen and settles the card instead of closing it.
 *
 * A typed comment is never lost to a failed save or a closed screen: it stays
 * in the field, and [drafts] keeps it until the server has it.
 */
class ReviewViewModel(
    val bookingId: Int,
    private val surface: String,
    initial: ReviewDto?,
    private val api: ReviewApi,
    private val telemetry: Telemetry = Telemetry.Noop,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val keepOpen: Boolean = surface == SURFACE_DETAIL,
    private val drafts: ReviewDrafts = ReviewDrafts.inMemory(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        if (initial == null) ReviewCardUiState()
        else ReviewCardUiState(
            review = initial, step = ReviewStep.Recommend, editing = true,
            comment = initial.comment, settled = true,
        ),
    )
    val uiState: StateFlow<ReviewCardUiState> = _uiState

    private val queue = Mutex()
    private var confirmed: ReviewDto? = initial
    private var inFlight = 0
    private var startedAt: TimeMark? = null
    private var stepsAnswered = 0
    private var shown = false
    private var failures = 0
    private var draftJob: Job? = null

    init {
        initial?.let(::restoreDraft)
    }

    /** Once per card: a list row re-enters composition on every scroll. */
    fun onShown() {
        if (shown || _uiState.value.editing) return
        shown = true
        telemetry.reviewPromptShown(surface, bookingId)
    }

    /** Step one. Creates the review; a repeat tap on a saved review re-answers it. */
    fun answerAgain(value: String) {
        val state = _uiState.value
        if (state.review == null) {
            if (state.pendingAgain != null) return
            startedAt = timeSource.markNow()
            _uiState.update { it.copy(pendingAgain = value, failureCode = null) }
            enqueue(step = "again", onSaved = { advanceTo(ReviewStep.Intensity) }) {
                api.createReview(bookingId, again = value, promptSurface = surface)
            }
        } else {
            if (state.review.again == value) return
            patch("again", "again", JsonPrimitive(value)) { it.copy(again = value) }
        }
    }

    fun answerIntensity(value: Int) {
        val review = _uiState.value.review ?: return
        if (review.intensity == value) advanceTo(ReviewStep.Recommend)
        else patch("intensity", "intensity", JsonPrimitive(value), onSaved = { advanceTo(ReviewStep.Recommend) }) {
            it.copy(intensity = value)
        }
    }

    fun setScore(target: ScoreTarget, value: Int) {
        val review = _uiState.value.review ?: return
        val current = when (target) {
            ScoreTarget.Instructor -> review.instructorScore
            ScoreTarget.Class -> review.classScore
            ScoreTarget.Studio -> review.studioScore
        }
        if (current == value) return
        patch("recommend", target.field, JsonPrimitive(value)) {
            when (target) {
                ScoreTarget.Instructor -> it.copy(instructorScore = value)
                ScoreTarget.Class -> it.copy(classScore = value)
                ScoreTarget.Studio -> it.copy(studioScore = value)
            }
        }
    }

    fun setComment(text: String) {
        _uiState.update {
            val next = it.copy(comment = text.take(COMMENT_MAX_LENGTH))
            next.copy(settled = it.settled && !next.commentDirty)
        }
        // Once typing pauses, not per keystroke: each write is a Keychain round trip.
        draftJob?.cancel()
        draftJob = viewModelScope.launch {
            delay(DRAFT_DEBOUNCE)
            keepDraft()
        }
    }

    /** Saves a changed comment; safe to call on every focus loss. */
    fun saveComment() {
        val state = _uiState.value
        if (state.review == null || !state.commentDirty || state.finishing) return
        patchComment(state.comment.trim())
    }

    /** Closes the card. The answers are already saved; only a dirty comment is left to send. */
    fun done() {
        val state = _uiState.value
        if (state.review == null || state.finishing || state.settled) return
        _uiState.update { it.copy(finishing = true, failureCode = null) }
        if (state.commentDirty) return patchComment(state.comment.trim(), onSaved = ::finish)
        // Behind every answer still in flight. It reads "saved" only if none of them
        // failed and the comment on screen is the one the server holds: the notice
        // code is no test of that, the screen clears it as soon as it has shown it.
        val failuresBefore = failures
        viewModelScope.launch {
            queue.withLock {
                if (failures == failuresBefore && !_uiState.value.commentDirty) finish() else unfinish()
            }
        }
    }

    /** The screen has shown the failure notice. */
    fun failureShown() {
        _uiState.update { it.copy(failureCode = null) }
    }

    override fun onCleared() {
        keepDraft()
    }

    /** A comment the server does not have yet comes back with the card. */
    private fun restoreDraft(review: ReviewDto) {
        val draft = drafts.load(review.id) ?: return
        if (draft.trim() == review.comment.trim()) return drafts.clear(review.id)
        _uiState.update { it.copy(comment = draft.take(COMMENT_MAX_LENGTH), settled = false) }
    }

    /** Keeps what is typed until the server has it, then lets it go. */
    private fun keepDraft() {
        val review = confirmed ?: return
        val typed = _uiState.value.comment
        if (typed.trim() == review.comment.trim()) drafts.clear(review.id) else drafts.save(review.id, typed)
    }

    private fun finish() {
        if (!_uiState.value.editing) {
            telemetry.reviewCompleted(
                durationMs = startedAt?.elapsedNow()?.inWholeMilliseconds ?: 0L,
                steps = stepsAnswered,
            )
        }
        _uiState.update {
            // Settled either way: a card that is about to leave shows it was saved first.
            val closed = it.copy(finishing = false, settled = true, doneCount = it.doneCount + 1)
            if (keepOpen) closed.copy(step = ReviewStep.Recommend, editing = true)
            else closed.copy(step = ReviewStep.Done)
        }
    }

    private fun unfinish() {
        _uiState.update { it.copy(finishing = false) }
    }

    private fun advanceTo(step: ReviewStep) {
        _uiState.update { if (it.editing || it.step == ReviewStep.Done) it else it.copy(step = step) }
    }

    private fun patchComment(text: String, onSaved: () -> Unit = {}) =
        patch(STEP_COMMENT, "comment", JsonPrimitive(text), onSaved) { it.copy(comment = text) }

    private fun patch(
        step: String,
        field: String,
        value: JsonPrimitive,
        onSaved: () -> Unit = {},
        shown: (ReviewDto) -> ReviewDto,
    ) {
        _uiState.update { it.copy(review = it.review?.let(shown), settled = false, failureCode = null) }
        enqueue(step, onSaved) { api.updateReview(bookingId, JsonObject(mapOf(field to value))) }
    }

    /** One request at a time, in tap order. The answer is already on screen. */
    private fun enqueue(step: String, onSaved: () -> Unit = {}, call: suspend () -> ReviewDto) {
        inFlight += 1
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            queue.withLock {
                try {
                    val review = call()
                    val created = confirmed == null
                    confirmed = review
                    inFlight -= 1
                    _uiState.update { current ->
                        current.copy(
                            // Later taps are still on their way: leave what the member sees alone.
                            review = if (inFlight == 0 || current.review == null) review else current.review,
                            pendingAgain = null,
                            saving = inFlight > 0,
                            comment = if (created) review.comment else current.comment,
                        )
                    }
                    stepsAnswered += 1
                    telemetry.reviewStepSaved(
                        step = step,
                        again = review.again,
                        intensity = review.intensity,
                        scoresSet = listOfNotNull(review.instructorScore, review.classScore, review.studioScore).size,
                        hasComment = review.comment.isNotBlank(),
                    )
                    if (_uiState.value.editing) telemetry.reviewEdited()
                    if (created) restoreDraft(review) else if (step == STEP_COMMENT) keepDraft()
                    onSaved()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ReviewError) {
                    inFlight -= 1
                    failures += 1
                    _uiState.update {
                        it.copy(review = confirmed, pendingAgain = null, saving = inFlight > 0, finishing = false, refusedCode = e.code)
                    }
                } catch (e: Exception) {
                    inFlight -= 1
                    failures += 1
                    telemetry.recordError(e, mapOf("op" to "review.$step", "booking_id" to bookingId))
                    // Back to the last answers the server confirmed. The typed comment is
                    // a separate field and stays exactly as the member left it.
                    _uiState.update {
                        it.copy(
                            review = confirmed, pendingAgain = null, saving = inFlight > 0, finishing = false,
                            failureCode = e.transportFailureCode(),
                        )
                    }
                    keepDraft()
                }
            }
        }
    }

    private inline fun MutableStateFlow<ReviewCardUiState>.update(block: (ReviewCardUiState) -> ReviewCardUiState) {
        value = block(value)
    }

    companion object {
        const val SURFACE_DETAIL = "detail"
        private const val STEP_COMMENT = "comment"
        private val DRAFT_DEBOUNCE = 800.milliseconds
    }
}
