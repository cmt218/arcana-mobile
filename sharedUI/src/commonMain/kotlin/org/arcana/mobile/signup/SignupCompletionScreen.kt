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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.arcana.mobile.ui.ArcanaDropdownField
import org.arcana.mobile.ui.DropdownOption
import org.arcana.mobile.theme.Ash
import org.arcana.mobile.theme.Ash2
import org.arcana.mobile.theme.Danger
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
            onPhoneNumberChange = viewModel::updatePhoneNumber,
            onGenderChange = viewModel::updateGender,
            onBirthdayChange = viewModel::updateBirthday,
            onAddressLine1Change = viewModel::updateAddressLine1,
            onAddressLine2Change = viewModel::updateAddressLine2,
            onCityChange = viewModel::updateCity,
            onStateChange = viewModel::updateState,
            onPostalCodeChange = viewModel::updatePostalCode,
            onPasswordChange = viewModel::updatePassword,
            onConfirmPasswordChange = viewModel::updateConfirmPassword,
            onSubmit = viewModel::submit,
            modifier = modifier,
        )

        is SignupCompletionState.Success -> SuccessLoader(modifier = modifier)

        is SignupCompletionState.Error -> ErrorState(
            error = s,
            onNavigateToLogin = onNavigateToLogin,
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
    onPhoneNumberChange: (String) -> Unit,
    onGenderChange: (String) -> Unit,
    onBirthdayChange: (String) -> Unit,
    onAddressLine1Change: (String) -> Unit,
    onAddressLine2Change: (String) -> Unit,
    onCityChange: (String) -> Unit,
    onStateChange: (String) -> Unit,
    onPostalCodeChange: (String) -> Unit,
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
                .imePadding()
                // Own the hardware Tab key so it advances exactly one field. On the
                // iOS simulator the platform ALSO traverses focus on Tab, which —
                // combined with each field's onNext moveFocus — advanced two fields
                // per press. Consuming the event here makes us the single mover; the
                // soft-keyboard "Next" path (onImeAction) is untouched for phones.
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Tab) {
                        focusManager.moveFocus(
                            if (event.isShiftPressed) FocusDirection.Up else FocusDirection.Down
                        )
                        true
                    } else {
                        false
                    }
                },
        ) {
            Column(modifier = Modifier.heightIn(min = viewportMinHeight)) {
                Spacer(Modifier.height(8.dp))
                // Wordmark only — the step stamp was removed now that signup
                // completion is a single screen (no multi-step flow to count).
                WordmarkLogo(modifier = Modifier.height(20.dp), color = Moss)

                Spacer(Modifier.height(44.dp))
                // Header.
                Overline(text = "Create your login", color = Moss)
                Spacer(Modifier.height(14.dp))
                Display(text = "Claim\nyour\nname.", size = 52, color = Ink)

                // Non-field failures (network / server / unrecognized) surface
                // here so the member keeps everything they typed.
                val formError = editing.formError
                if (formError != null) {
                    Spacer(Modifier.height(20.dp))
                    FormErrorBanner(message = formError)
                }

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
                        imeAction = ImeAction.Next,
                        onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
                        capitalization = KeyboardCapitalization.Words,
                        contentType = ContentType.PersonFirstName,
                    )
                    ArcanaTextField(
                        label = "Last name",
                        value = editing.lastName,
                        onValueChange = onLastNameChange,
                        imeAction = ImeAction.Next,
                        onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
                        capitalization = KeyboardCapitalization.Words,
                        contentType = ContentType.PersonLastName,
                    )
                    ArcanaTextField(
                        label = "Phone number",
                        value = editing.phoneNumber,
                        onValueChange = onPhoneNumberChange,
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Next,
                        onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
                        contentType = ContentType.PhoneNumber,
                        error = editing.phoneError,
                    )
                    ArcanaDropdownField(
                        label = "Gender",
                        selectedValue = editing.gender,
                        options = GENDER_OPTIONS,
                        onSelect = onGenderChange,
                    )
                    ArcanaTextField(
                        label = "Birthday",
                        value = editing.birthday,
                        onValueChange = onBirthdayChange,
                        placeholder = "MM/DD/YYYY",
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next,
                        onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
                        visualTransformation = DateMaskVisualTransformation,
                        error = editing.birthdayError,
                    )
                    ArcanaTextField(
                        label = "Street address",
                        value = editing.addressLine1,
                        onValueChange = onAddressLine1Change,
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next,
                        onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
                        contentType = ContentType.AddressStreet,
                    )
                    ArcanaTextField(
                        label = "Apt / unit (optional)",
                        value = editing.addressLine2,
                        onValueChange = onAddressLine2Change,
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Next,
                        onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
                        contentType = ContentType.AddressAuxiliaryDetails,
                    )
                    ArcanaTextField(
                        label = "City",
                        value = editing.city,
                        onValueChange = onCityChange,
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next,
                        onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
                        contentType = ContentType.AddressLocality,
                    )
                    ArcanaTextField(
                        label = "State",
                        value = editing.state,
                        onValueChange = onStateChange,
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Next,
                        onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
                        contentType = ContentType.AddressRegion,
                    )
                    ArcanaTextField(
                        label = "ZIP code",
                        value = editing.postalCode,
                        onValueChange = onPostalCodeChange,
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next,
                        onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
                        contentType = ContentType.PostalCode,
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
                        contentType = ContentType.NewPassword,
                        error = editing.passwordError,
                    )
                    ArcanaTextField(
                        label = "Confirm password",
                        value = editing.confirmPassword,
                        onValueChange = onConfirmPasswordChange,
                        placeholder = "Re-enter your password",
                        secure = true,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                        onImeAction = onSubmit,
                        contentType = ContentType.NewPassword,
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
                // Guaranteed breathing room below the CTA so it never sits glued to
                // the bottom edge / gesture bar once the form is tall enough to scroll.
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

/** Gender choices — values are the server's choice codes; labels are shown. */
private val GENDER_OPTIONS = listOf(
    DropdownOption("male", "Male"),
    DropdownOption("female", "Female"),
    DropdownOption("other", "Other"),
)

/**
 * Masks raw birthday digits (`MMDDYYYY`) as `MM/DD/YYYY`, inserting the slashes
 * as the member types. The offset mapping keeps the cursor correct across the two
 * inserted separators. The underlying value stays digit-only (see the VM).
 */
private val DateMaskVisualTransformation = object : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.take(8)
        val out = buildString {
            for (i in digits.indices) {
                append(digits[i])
                if (i == 1 || i == 3) append('/')
            }
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = when {
                offset <= 1 -> offset          // MM
                offset <= 3 -> offset + 1       // DD (after first slash)
                else -> offset + 2              // YYYY (after both slashes)
            }.coerceAtMost(out.length)

            override fun transformedToOriginal(offset: Int): Int = when {
                offset <= 2 -> offset
                offset <= 5 -> offset - 1
                else -> offset - 2
            }.coerceIn(0, digits.length)
        }
        return TransformedText(AnnotatedString(out), mapping)
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
                // decorative — the email text beside it is the content.
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
 * Success — the auth gate (App.kt) flips to the main app on a
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
 * A Danger-toned banner for non-field failures (network / server / unrecognized
 * 400). Sits above the form fields so the member never loses what they typed.
 */
@Composable
private fun FormErrorBanner(message: String) {
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

/**
 * Terminal error — centered, brand-aligned. Both kinds mean the form can't
 * proceed (the link is dead, or an account already exists), so each routes the
 * member to log in. Recoverable validation failures never reach here; they're
 * shown inline on the editing form instead.
 */
@Composable
private fun ErrorState(
    error: SignupCompletionState.Error,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val body = when (error.kind) {
        SignupErrorKind.TokenExpired -> "Looks like this link's already been used."
        SignupErrorKind.AlreadyHasAccount -> "You already have an account with this email."
    }
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
            Overline(text = "Already signed up", color = Moss)
            Spacer(Modifier.height(14.dp))
            Display(text = "Log in\ninstead.", size = 44, color = Ink)
            Spacer(Modifier.height(18.dp))
            BodyText(text = body, size = 15, color = Ash)
            Spacer(Modifier.height(32.dp))
            PrimaryCta(label = "Log in", onClick = onNavigateToLogin)
        }
    }
}
