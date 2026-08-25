package org.arcana.mobile.signup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.arcana.mobile.theme.Ash
import org.arcana.mobile.theme.Danger
import org.arcana.mobile.theme.Graphite
import org.arcana.mobile.theme.Ink
import org.arcana.mobile.theme.Lime
import org.arcana.mobile.theme.Mist
import org.arcana.mobile.theme.Moss
import org.arcana.mobile.theme.Stone
import org.arcana.mobile.theme.WordmarkLogo
import org.arcana.mobile.ui.ArcanaMultilineTextField
import org.arcana.mobile.ui.ArcanaTextField
import org.arcana.mobile.ui.BodyText
import org.arcana.mobile.ui.Caption
import org.arcana.mobile.ui.Display
import org.arcana.mobile.ui.Overline
import org.arcana.mobile.ui.PrimaryCta
import org.arcana.mobile.ui.TextLink
import org.arcana.mobile.ui.safeContentPadding

/**
 * The onboarding survey — screen 01 of the signup flow (August cohort+),
 * shown after the welcome deep link and before "Claim your name". Ported from
 * the /beta web page's pre-checkout SurveyModal; the questions themselves live
 * in [SURVEY_QUESTIONS] (a 1:1 port of arcana-web survey.ts — do not diverge).
 *
 * Stone gateway shell like [SignupCompletionScreen]. One scrollable form:
 * section headings, full-width selectable option chips (single = pick one,
 * multi = toggle), free-text fields for the open-floor questions, and a
 * required-gated CONTINUE. Submit failures keep every answer, offer a retry,
 * and — after the first failure — a "Continue anyway" escape so the survey
 * can never block a paid member's signup.
 */
@Composable
fun SignupSurveyScreen(
    viewModel: SignupSurveyViewModel,
    onDone: () -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val canSubmit by viewModel.canSubmit.collectAsState()
    val completed by viewModel.completed.collectAsState()

    LaunchedEffect(completed) {
        if (completed) onDone()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Stone)
            .safeContentPadding()
            .padding(horizontal = 28.dp)
            .verticalScroll(rememberScrollState())
            .imePadding(),
    ) {
        Spacer(Modifier.height(8.dp))
        WordmarkLogo(modifier = Modifier.height(24.dp), tint = Moss)

        Spacer(Modifier.height(44.dp))
        Overline(text = "Step 01 · Your training profile", color = Moss, maxLines = Int.MAX_VALUE)
        Spacer(Modifier.height(14.dp))
        Display(text = "Before\nyou join.", size = 52, color = Ink)
        Spacer(Modifier.height(18.dp))
        BodyText(
            text = "A few quick questions so we can make your first month count. " +
                "Takes about 2 minutes.",
            size = 15,
            color = Ash,
        )

        Spacer(Modifier.height(36.dp))
        var lastSection: String? = null
        SURVEY_QUESTIONS.forEach { question ->
            if (question.section != lastSection) {
                lastSection = question.section
                Spacer(Modifier.height(12.dp))
                Overline(text = question.section, color = Moss, maxLines = Int.MAX_VALUE)
                Spacer(Modifier.height(20.dp))
            }
            SurveyQuestionBlock(
                question = question,
                answers = state.answers,
                onSelectSingle = viewModel::selectSingle,
                onToggleMulti = viewModel::toggleMulti,
                onUpdateText = viewModel::updateText,
            )
            Spacer(Modifier.height(32.dp))
        }

        if (state.submitError != null) {
            SubmitErrorBanner(message = state.submitError!!)
            Spacer(Modifier.height(20.dp))
        }

        Overline(
            text = "${answeredCount(state.answers)} of ${SURVEY_QUESTIONS.size} answered",
            size = 10,
            color = Graphite,
            maxLines = Int.MAX_VALUE,
        )
        Spacer(Modifier.height(12.dp))
        if (state.isSubmitting) {
            SubmittingPill()
        } else {
            PrimaryCta(
                label = "Continue",
                onClick = viewModel::submit,
                enabled = canSubmit,
            )
        }

        // After a failed submit, never hold a paid member hostage — let them
        // move on to claim their account; answers can be recovered later.
        if (state.failedAttempts >= 1 && !state.isSubmitting) {
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextLink(label = "Continue anyway", onClick = viewModel::continueAnyway)
            }
        }

        Spacer(Modifier.height(36.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Caption(text = "Already a member?", color = Ash)
            Spacer(Modifier.padding(horizontal = 6.dp))
            TextLink(label = "Log in", onClick = onNavigateToLogin)
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun SurveyQuestionBlock(
    question: SurveyQuestion,
    answers: SurveyAnswers,
    onSelectSingle: (String, String) -> Unit,
    onToggleMulti: (String, String) -> Unit,
    onUpdateText: (String, String) -> Unit,
) {
    val optionalStamp = if (!question.required) "  ·  optional" else ""
    Overline(
        text = "Q${question.n}$optionalStamp",
        size = 10,
        color = Graphite,
        maxLines = Int.MAX_VALUE,
    )
    Spacer(Modifier.height(8.dp))
    BodyText(text = question.label, size = 16, color = Ink)
    question.hint?.let {
        Spacer(Modifier.height(4.dp))
        // Prose, not a stamp: Caption defaults to one ellipsised line, which
        // hid the end of longer hints on narrower phones. A member cannot
        // answer a question they can only half read.
        Caption(text = it, color = Ash, maxLines = Int.MAX_VALUE, overflow = TextOverflow.Clip)
    }
    Spacer(Modifier.height(14.dp))

    when (question.type) {
        SurveyQuestionType.Single -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                question.options.forEach { option ->
                    SurveyOptionChip(
                        label = option,
                        selected = answers.singles[question.id] == option,
                        onClick = { onSelectSingle(question.id, option) },
                    )
                }
            }
            // "Other" reveals its required specify field (howHeard).
            if (question.otherOption != null &&
                answers.singles[question.id] == question.otherOption
            ) {
                Spacer(Modifier.height(16.dp))
                ArcanaTextField(
                    label = "Please specify",
                    value = answers.texts[question.id + SURVEY_OTHER_SUFFIX] ?: "",
                    onValueChange = { onUpdateText(question.id + SURVEY_OTHER_SUFFIX, it) },
                )
            }
        }

        SurveyQuestionType.Multi -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            question.options.forEach { option ->
                SurveyOptionChip(
                    label = option,
                    selected = option in answers.multis[question.id].orEmpty(),
                    onClick = { onToggleMulti(question.id, option) },
                )
            }
        }

        SurveyQuestionType.Text -> ArcanaMultilineTextField(
            label = "Your answer",
            value = answers.texts[question.id] ?: "",
            onValueChange = { onUpdateText(question.id, it) },
            maxLength = SignupSurveyViewModel.TEXT_MAX_LENGTH,
            minLines = 3,
        )
    }
}

/**
 * Full-width selectable option row — the [org.arcana.mobile.booking] VisitChip
 * idiom (Burnt Nectar when selected, Mist outline otherwise), left-aligned
 * sentence-case body text so long options wrap instead of truncating.
 */
@Composable
private fun SurveyOptionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (selected) Modifier.background(Moss)
                else Modifier.border(1.dp, Mist, RoundedCornerShape(8.dp))
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BodyText(
            text = label,
            size = 14,
            color = if (selected) Stone else Ink,
        )
    }
}

/** Danger-toned banner for submit failures — the member keeps every answer. */
@Composable
private fun SubmitErrorBanner(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Danger.copy(alpha = 0.10f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        BodyText(text = message, size = 14, color = Danger)
    }
}

/** Moss pill + Lime spinner — the in-flight CTA treatment (AuthScreen idiom). */
@Composable
private fun SubmittingPill() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(CircleShape)
            .background(Moss),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            color = Lime,
            strokeWidth = 2.dp,
            modifier = Modifier.size(24.dp),
        )
    }
}
