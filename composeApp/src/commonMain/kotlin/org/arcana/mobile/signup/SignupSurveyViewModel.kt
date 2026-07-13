package org.arcana.mobile.signup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.arcana.mobile.analytics.Telemetry

/**
 * Drives [SignupSurveyScreen] — the onboarding survey shown between the signup
 * deep link and the claim-your-name screen.
 *
 * Design rule: the survey must NEVER block a paid member's signup. Failures
 * keep everything they answered and offer a retry; after the first failed
 * attempt a "Continue anyway" escape appears, and an invalid/expired token
 * (410) advances immediately — the claim screen owns that error UX. The gate
 * in App.kt persists completion per token, so a member who finished (or
 * skipped) the survey never sees it again on the same link.
 */
data class SignupSurveyUiState(
    val answers: SurveyAnswers = SurveyAnswers(),
    val isSubmitting: Boolean = false,
    /** Non-null after a failed submit; shown as a banner above the CTA. */
    val submitError: String? = null,
    /** >= 1 reveals the "Continue anyway" escape hatch. */
    val failedAttempts: Int = 0,
)

class SignupSurveyViewModel(
    private val token: String,
    private val api: SignupSurveyCallable,
    private val telemetry: Telemetry = Telemetry.Noop,
) : ViewModel() {

    private val _state = MutableStateFlow(SignupSurveyUiState())
    val state: StateFlow<SignupSurveyUiState> = _state.asStateFlow()

    // Synchronously maintained alongside state (mirrors SignupCompletionViewModel)
    // so validation is observable immediately after each answer, with no
    // dispatcher timing.
    private val _canSubmit = MutableStateFlow(false)
    val canSubmit: StateFlow<Boolean> = _canSubmit.asStateFlow()

    /** Flips true exactly once; App.kt persists the done-flag and advances to
     *  the claim-your-name screen. */
    private val _completed = MutableStateFlow(false)
    val completed: StateFlow<Boolean> = _completed.asStateFlow()

    init {
        telemetry.signupSurveyStarted()
    }

    fun selectSingle(questionId: String, option: String) = mutate { answers ->
        answers.copy(singles = answers.singles + (questionId to option))
    }

    fun toggleMulti(questionId: String, option: String) = mutate { answers ->
        val current = answers.multis[questionId].orEmpty()
        val next = if (option in current) current - option else current + option
        answers.copy(multis = answers.multis + (questionId to next))
    }

    fun updateText(key: String, value: String) = mutate { answers ->
        answers.copy(texts = answers.texts + (key to value.take(TEXT_MAX_LENGTH)))
    }

    fun submit() {
        val current = _state.value
        if (current.isSubmitting || missingRequired(current.answers).isNotEmpty()) return
        setState(current.copy(isSubmitting = true, submitError = null))
        viewModelScope.launch {
            val result = api.submit(token, answersToJson(_state.value.answers))
            val latest = _state.value
            when (result) {
                SignupSurveyResult.Success -> {
                    telemetry.signupSurveySubmitted(answeredCount(latest.answers))
                    complete()
                }
                SignupSurveyResult.TokenExpiredOrConsumed -> {
                    // The link is dead — don't trap the member on the survey;
                    // the claim screen renders the proper token-expired state.
                    telemetry.signupSurveyFailed("token_expired", 410)
                    complete()
                }
                is SignupSurveyResult.NetworkError -> {
                    telemetry.signupSurveyFailed("network")
                    setState(latest.copy(
                        isSubmitting = false,
                        submitError = NETWORK_MESSAGE,
                        failedAttempts = latest.failedAttempts + 1,
                    ))
                }
                is SignupSurveyResult.Other -> {
                    telemetry.signupSurveyFailed(
                        reason = if (result.statusCode >= 500) "server_5xx" else "generic",
                        statusCode = result.statusCode,
                    )
                    setState(latest.copy(
                        isSubmitting = false,
                        submitError = if (result.statusCode >= 500) SERVER_MESSAGE else GENERIC_MESSAGE,
                        failedAttempts = latest.failedAttempts + 1,
                    ))
                }
            }
        }
    }

    /** The escape hatch after repeated failures — a paid member's signup is
     *  never held hostage by the survey. */
    fun continueAnyway() {
        if (_completed.value) return
        telemetry.signupSurveySkipped("submit_failed")
        complete()
    }

    private fun complete() {
        setState(_state.value.copy(isSubmitting = false, submitError = null))
        _completed.value = true
    }

    private fun mutate(transform: (SurveyAnswers) -> SurveyAnswers) {
        val current = _state.value
        if (current.isSubmitting) return
        setState(current.copy(answers = transform(current.answers), submitError = null))
    }

    private fun setState(s: SignupSurveyUiState) {
        _state.value = s
        _canSubmit.value = !s.isSubmitting && missingRequired(s.answers).isEmpty()
    }

    companion object {
        // Matches the server's per-answer text cap (survey_views._MAX_TEXT_LENGTH).
        const val TEXT_MAX_LENGTH = 5000

        const val NETWORK_MESSAGE =
            "Couldn't reach the server. Check your connection and try again."
        const val SERVER_MESSAGE =
            "Something went wrong on our end. Please try again in a moment."
        const val GENERIC_MESSAGE =
            "We couldn't save your answers. Please try again."
    }
}
