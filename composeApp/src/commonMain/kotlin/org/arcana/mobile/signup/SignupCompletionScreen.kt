package org.arcana.mobile.signup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.arcana.mobile.theme.Ash
import org.arcana.mobile.theme.Ash2
import org.arcana.mobile.theme.Ink
import org.arcana.mobile.theme.Lime
import org.arcana.mobile.theme.Mist
import org.arcana.mobile.theme.Moss
import org.arcana.mobile.theme.Stone
import org.arcana.mobile.theme.WordmarkLogo
import org.arcana.mobile.ui.ArcanaIcons
import org.arcana.mobile.ui.ArcanaTextField
import org.arcana.mobile.ui.BodyText
import org.arcana.mobile.ui.Caption
import org.arcana.mobile.ui.Display
import org.arcana.mobile.ui.Overline
import org.arcana.mobile.ui.PrimaryCta
import org.arcana.mobile.ui.StrokeIcon
import org.arcana.mobile.ui.TextLink
import org.arcana.mobile.ui.safeContentPadding

/**
 * Screen 02 of the signup-completion flow — "Claim your name" / set credentials.
 *
 * Faithful to mock screen 02: Stone gateway shell (same shell as [org.arcana.mobile.auth.AuthScreen]),
 * a locked email row carried from web checkout (rendered only when [lockedEmail] is non-null),
 * and three hairline fields (display name + password + confirm) feeding the
 * [SignupCompletionViewModel]. Renders by VM state — Editing / Success / Error.
 *
 * Note: the prototype renders small metadata stamps ("STEP 02 OF 02", "FROM CHECKOUT")
 * in a Mono family. The app has no monospace family, so every such stamp is an [Overline]
 * (DM Sans Bold caps) — see the design handoff's Mono→Overline rule.
 */
@Composable
fun SignupCompletionScreen(
    viewModel: SignupCompletionViewModel,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier,
    lockedEmail: String? = null,
) {
    val state by viewModel.state.collectAsState()
    val canSubmit by viewModel.canSubmit.collectAsState()

    when (val s = state) {
        is SignupCompletionState.Editing -> EditingForm(
            editing = s,
            canSubmit = canSubmit,
            lockedEmail = lockedEmail,
            onFirstNameChange = viewModel::updateFirstName,
            onLastNameChange = viewModel::updateLastName,
            onPasswordChange = viewModel::updatePassword,
            onConfirmPasswordChange = viewModel::updateConfirmPassword,
            onSubmit = viewModel::submit,
            modifier = modifier,
        )

        is SignupCompletionState.Success -> SuccessLoader(modifier = modifier)

        is SignupCompletionState.Error -> ErrorState(
            error = s,
            onNavigateToLogin = onNavigateToLogin,
            onRetry = viewModel::reset,
            modifier = modifier,
        )
    }
}

@Composable
private fun EditingForm(
    editing: SignupCompletionState.Editing,
    canSubmit: Boolean,
    lockedEmail: String?,
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Compose iOS doesn't auto-traverse focus on the Next IME action the way
    // Android does, so advance focus explicitly (mirrors AuthScreen).
    val focusManager = LocalFocusManager.current

    // Keyboard-aware scroll: BoxWithConstraints supplies the pre-IME viewport
    // height so the inner column's heightIn(min = …) keeps the weight-pushed
    // footer at the fold; imePadding shrinks the scroll viewport so fields stay
    // reachable above the keyboard. Copied from AuthScreen.
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Stone)
            .safeContentPadding()
            .padding(horizontal = 28.dp),
    ) {
        val viewportMinHeight = maxHeight
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .imePadding(),
        ) {
            Column(modifier = Modifier.heightIn(min = viewportMinHeight)) {
                Spacer(Modifier.height(8.dp))
                // Wordmark only — the step stamp was removed now that signup
                // completion is a single screen (no multi-step flow to count).
                WordmarkLogo(modifier = Modifier.height(24.dp), tint = Moss)

                Spacer(Modifier.height(44.dp))
                // Header.
                Overline(text = "Create your login", color = Moss)
                Spacer(Modifier.height(14.dp))
                Display(text = "Claim\nyour\nname.", size = 52, color = Ink)

                Spacer(Modifier.height(32.dp))
                // Fields (and the locked email row when present), gap ~26.
                Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    if (lockedEmail != null) {
                        LockedEmailRow(email = lockedEmail)
                    }
                    ArcanaTextField(
                        label = "First name",
                        value = editing.firstName,
                        onValueChange = onFirstNameChange,
                        placeholder = "Your first name",
                        imeAction = ImeAction.Next,
                        onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
                    )
                    ArcanaTextField(
                        label = "Last name",
                        value = editing.lastName,
                        onValueChange = onLastNameChange,
                        placeholder = "Your last name",
                        imeAction = ImeAction.Next,
                        onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
                    )
                    ArcanaTextField(
                        label = "Password",
                        value = editing.password,
                        onValueChange = onPasswordChange,
                        placeholder = "At least 8 characters",
                        secure = true,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next,
                        onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
                    )
                    ArcanaTextField(
                        label = "Confirm password",
                        value = editing.confirmPassword,
                        onValueChange = onConfirmPasswordChange,
                        secure = true,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                        onImeAction = onSubmit,
                    )
                }

                Spacer(Modifier.height(30.dp))
                // CTA — loading treatment mirrors AuthScreen's CtaBlock.
                if (editing.isSubmitting) {
                    LoadingPill()
                } else {
                    PrimaryCta(
                        label = "Create account",
                        onClick = onSubmit,
                        enabled = canSubmit,
                    )
                }

                Spacer(Modifier.weight(1f))
                // Footer — weight-pushed to the fold.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp, bottom = 28.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BodyText(
                        text = "Completing accepts the Member Code.",
                        size = 12,
                        color = Ash,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * Locked / confirmed email carried over from web checkout — not editable.
 * A 22dp Moss check chip + the email value + a faint "FROM CHECKOUT" stamp,
 * over a 1px Mist hairline, with an "EMAIL" label above. Models the
 * prototype's SuLockedRow; private to this screen (no broad reuse at this scope).
 */
@Composable
private fun LockedEmailRow(email: String) {
    Column {
        Overline(text = "Email", color = Ash)
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Moss),
                contentAlignment = Alignment.Center,
            ) {
                StrokeIcon(icon = ArcanaIcons.Check, size = 13.dp, tint = Lime)
            }
            BodyText(text = email, modifier = Modifier.weight(1f), size = 18, color = Ink)
            Overline(text = "From checkout", size = 9, color = Ash2)
        }
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Mist),
        )
    }
}

/** Moss pill + Lime spinner — the in-flight CTA treatment, copied from AuthScreen. */
@Composable
private fun LoadingPill() {
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

/**
 * Success — the auth gate (App.kt, wired later) flips to the main app on a
 * successful complete-signup, so this screen is swapped away momentarily.
 * Render a minimal centered brand loader so there's no flash of empty content.
 * No navigation is performed here.
 */
@Composable
private fun SuccessLoader(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Stone),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
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
}

/**
 * Error — centered, brand-aligned. TokenExpired routes to login ("already signed
 * up, log in instead"); the remaining kinds offer a retry that resets the form
 * back to a fresh Editing state.
 */
@Composable
private fun ErrorState(
    error: SignupCompletionState.Error,
    onNavigateToLogin: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Stone)
            .safeContentPadding()
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
        ) {
            if (error.kind == SignupErrorKind.TokenExpired) {
                Overline(text = "Already signed up", color = Moss)
                Spacer(Modifier.height(14.dp))
                Display(text = "Log in\ninstead.", size = 44, color = Ink)
                Spacer(Modifier.height(18.dp))
                BodyText(
                    text = "Looks like this link's already been used.",
                    size = 15,
                    color = Ash,
                )
                Spacer(Modifier.height(32.dp))
                PrimaryCta(label = "Log in", onClick = onNavigateToLogin)
            } else {
                Overline(text = "Something went wrong", color = Moss)
                Spacer(Modifier.height(14.dp))
                Display(text = "Let's try\nagain.", size = 44, color = Ink)
                Spacer(Modifier.height(18.dp))
                BodyText(
                    text = defaultMessage(error.kind),
                    size = 15,
                    color = Ash,
                )
                Spacer(Modifier.height(32.dp))
                PrimaryCta(label = "Try again", onClick = onRetry, trailing = {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Lime),
                        contentAlignment = Alignment.Center,
                    ) {
                        StrokeIcon(icon = ArcanaIcons.Refresh, size = 18.dp, tint = Ink)
                    }
                })
                Spacer(Modifier.height(20.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextLink(label = "Log in", onClick = onNavigateToLogin, color = Ash)
                }
            }
        }
    }
}

private fun defaultMessage(kind: SignupErrorKind): String = when (kind) {
    SignupErrorKind.Network -> "Could not connect to the server. Check your connection and try again."
    SignupErrorKind.Server -> "Something went wrong on our end. Give it another shot."
    SignupErrorKind.BadRequest -> "We couldn't complete your signup. Please review your details and try again."
    SignupErrorKind.TokenExpired -> "This link is no longer valid."
}
