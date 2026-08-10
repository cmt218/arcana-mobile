package org.arcana.mobile.profile

import androidx.compose.foundation.background
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
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.unit.dp
import org.arcana.mobile.theme.Ash
import org.arcana.mobile.theme.Danger
import org.arcana.mobile.theme.Ink
import org.arcana.mobile.theme.Lime
import org.arcana.mobile.theme.Mist
import org.arcana.mobile.theme.Moss
import org.arcana.mobile.theme.Stone
import org.arcana.mobile.ui.ArcanaDropdownField
import org.arcana.mobile.ui.ArcanaIcons
import org.arcana.mobile.ui.ArcanaTextField
import org.arcana.mobile.ui.BodyText
import org.arcana.mobile.ui.Display
import org.arcana.mobile.ui.DropdownOption
import org.arcana.mobile.ui.IconCircle
import org.arcana.mobile.ui.Overline
import org.arcana.mobile.ui.PrimaryCta
import org.arcana.mobile.ui.safeContentPadding
import org.koin.compose.viewmodel.koinViewModel

/**
 * "Edit your profile" — reached from the gear in the Profile hero. Pre-fills the
 * same fields the claim-your-name flow collects (first/last name, gender,
 * birthday, address — no password, no phone), lets the member change them, and
 * PATCHes `/users/me/`. Close (X) discards; Save persists and is enabled only
 * once something has changed and everything is still valid (see
 * [EditProfileViewModel]). Field widgets + masks mirror [org.arcana.mobile.signup.SignupCompletionScreen].
 */
@Composable
fun EditProfileScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditProfileViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val canSave by viewModel.canSave.collectAsState()

    when (val s = state) {
        EditProfileViewModel.State.Loading -> CenteredLoader(modifier)
        is EditProfileViewModel.State.LoadError -> LoadErrorState(
            message = s.message,
            onRetry = viewModel::load,
            onClose = onClose,
            modifier = modifier,
        )
        // The save closes the screen; render the loader for the instant before the
        // nav pop lands so there's no flash of the form.
        EditProfileViewModel.State.Saved -> {
            LaunchedEffect(Unit) { onClose() }
            CenteredLoader(modifier)
        }
        is EditProfileViewModel.State.Editing -> EditingForm(
            editing = s,
            canSave = canSave,
            onClose = onClose,
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
            onSave = viewModel::save,
            modifier = modifier,
        )
    }
}

@Composable
private fun EditingForm(
    editing: EditProfileViewModel.State.Editing,
    canSave: Boolean,
    onClose: () -> Unit,
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
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val f = editing.fields

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Stone)
            .safeContentPadding()
            .padding(horizontal = 28.dp)
            // imePadding on the OUTER column (which holds both the pinned header
            // and the scroll area) so the keyboard shrinks the whole content
            // region. If it sat only on the inner scroll, focusing a bottom field
            // would bubble its bring-into-view to the root and translate the whole
            // view up — pushing the header off-screen for good.
            .imePadding(),
    ) {
        // Pinned header: Close (X) discards on the left; Save persists on the
        // right, disabled until a change is made and everything is valid.
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconCircle(
                icon = ArcanaIcons.Close,
                diameter = 36, iconSize = 16,
                borderColor = Ash.copy(alpha = 0.4f),
                contentColor = Ink,
                onClick = onClose,
            )
            SaveAction(enabled = canSave, saving = editing.isSaving, onClick = onSave)
        }

        // Scrollable form beneath the pinned header.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                // Own the hardware Tab key so it advances exactly one field (the
                // iOS simulator otherwise double-traverses). Mirrors SignupCompletionScreen.
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Tab) {
                        focusManager.moveFocus(if (event.isShiftPressed) FocusDirection.Up else FocusDirection.Down)
                        true
                    } else {
                        false
                    }
                },
        ) {
            Spacer(Modifier.height(24.dp))
            Overline(text = "Your details", color = Moss)
            Spacer(Modifier.height(14.dp))
            Display(text = "Edit your\nprofile.", size = 44, color = Ink)

            val formError = editing.formError
            if (formError != null) {
                Spacer(Modifier.height(20.dp))
                FormErrorBanner(message = formError)
            }

            Spacer(Modifier.height(28.dp))
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                ArcanaTextField(
                    label = "First name",
                    value = f.firstName,
                    onValueChange = onFirstNameChange,
                    imeAction = ImeAction.Next,
                    onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
                    capitalization = KeyboardCapitalization.Words,
                    contentType = ContentType.PersonFirstName,
                )
                ArcanaTextField(
                    label = "Last name",
                    value = f.lastName,
                    onValueChange = onLastNameChange,
                    imeAction = ImeAction.Next,
                    onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
                    capitalization = KeyboardCapitalization.Words,
                    contentType = ContentType.PersonLastName,
                )
                ArcanaTextField(
                    label = "Phone number",
                    value = f.phoneNumber,
                    onValueChange = onPhoneNumberChange,
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Next,
                    onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
                    contentType = ContentType.PhoneNumber,
                )
                ArcanaDropdownField(
                    label = "Gender",
                    selectedValue = f.gender,
                    options = GENDER_OPTIONS,
                    onSelect = onGenderChange,
                )
                ArcanaTextField(
                    label = "Birthday",
                    value = f.birthday,
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
                    value = f.addressLine1,
                    onValueChange = onAddressLine1Change,
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next,
                    onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
                    contentType = ContentType.AddressStreet,
                )
                ArcanaTextField(
                    label = "Apt / unit (optional)",
                    value = f.addressLine2,
                    onValueChange = onAddressLine2Change,
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Next,
                    onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
                    contentType = ContentType.AddressAuxiliaryDetails,
                )
                ArcanaTextField(
                    label = "City",
                    value = f.city,
                    onValueChange = onCityChange,
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next,
                    onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
                    contentType = ContentType.AddressLocality,
                )
                ArcanaTextField(
                    label = "State",
                    value = f.state,
                    onValueChange = onStateChange,
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Next,
                    onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
                    contentType = ContentType.AddressRegion,
                )
                ArcanaTextField(
                    label = "ZIP code",
                    value = f.postalCode,
                    onValueChange = onPostalCodeChange,
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                    onImeAction = onSave,
                    contentType = ContentType.PostalCode,
                )
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

/**
 * Top-right "Save". Moss + Stone when there's a valid change to commit; muted
 * (Mist + Ash, inert) until then; a Stone spinner while the PATCH is in flight.
 */
@Composable
private fun SaveAction(enabled: Boolean, saving: Boolean, onClick: () -> Unit) {
    val active = enabled && !saving
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (active || saving) Moss else Mist)
            .then(if (active) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (saving) {
            CircularProgressIndicator(color = Stone, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
        } else {
            Display(text = "Save", size = 13, color = if (enabled) Stone else Ash)
        }
    }
}

/** Gender choices — values are the server's choice codes; labels are shown.
 *  Intentional small duplication of SignupCompletionScreen's list (kept private
 *  to each screen rather than promoting a shared form module). */
private val GENDER_OPTIONS = listOf(
    DropdownOption("male", "Male"),
    DropdownOption("female", "Female"),
    DropdownOption("other", "Other"),
)

/**
 * Masks raw birthday digits (`MMDDYYYY`) as `MM/DD/YYYY`. Intentional small copy
 * of the SignupCompletionScreen transformation (the underlying value stays
 * digit-only; see [EditProfileViewModel]).
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
                offset <= 1 -> offset
                offset <= 3 -> offset + 1
                else -> offset + 2
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

@Composable
private fun CenteredLoader(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().background(Stone),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(56.dp).clip(CircleShape).background(Moss),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = Lime, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
        }
    }
}

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

@Composable
private fun LoadErrorState(
    message: String,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize().background(Stone).safeContentPadding().padding(horizontal = 28.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconCircle(
                icon = ArcanaIcons.Close,
                diameter = 36, iconSize = 16,
                borderColor = Ash.copy(alpha = 0.4f),
                contentColor = Ink,
                onClick = onClose,
            )
        }
        Column(modifier = Modifier.fillMaxWidth().align(Alignment.Center)) {
            Display(text = "Couldn't\nload.", size = 44, color = Ink)
            Spacer(Modifier.height(16.dp))
            BodyText(text = message, size = 15, color = Ash)
            Spacer(Modifier.height(28.dp))
            PrimaryCta(label = "Retry", onClick = onRetry)
        }
    }
}
