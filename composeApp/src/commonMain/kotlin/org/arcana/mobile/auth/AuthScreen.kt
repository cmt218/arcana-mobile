package org.arcana.mobile.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.arcana.mobile.theme.Ash
import org.arcana.mobile.theme.Danger
import org.arcana.mobile.theme.Ink
import org.arcana.mobile.theme.Lime
import org.arcana.mobile.theme.Moss
import org.arcana.mobile.theme.Stone
import org.arcana.mobile.theme.WordmarkLogo
import org.arcana.mobile.ui.ArcanaIcons
import org.arcana.mobile.ui.ArcanaTextField
import org.arcana.mobile.ui.BodyText
import org.arcana.mobile.ui.Display
import org.arcana.mobile.ui.Overline
import org.arcana.mobile.ui.PrimaryCta
import org.arcana.mobile.ui.TextLink
import org.arcana.mobile.ui.safeContentPadding
import org.arcana.mobile.settings.DeveloperSettingsScreen

/**
 * Gateway — sign-in only. There is no in-app sign-up: members onboard through
 * the invite welcome flow (SignupCompletionScreen, reached via a welcome deep
 * link). A cold launch with no welcome token lands here and can only log in.
 */
@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    modifier: Modifier = Modifier,
) {
    // Developer Settings is reachable from here as a full-screen overlay so
    // testers stuck at login (because the default API URL doesn't work for
    // them) can swap the base URL without first authenticating. Same screen
    // is also reachable post-login from Profile.
    var showDeveloperSettings by remember { mutableStateOf(false) }
    if (showDeveloperSettings) {
        DeveloperSettingsScreen(onClose = { showDeveloperSettings = false })
        return
    }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsState()
    val loading = uiState is AuthUiState.Loading
    // Lets us advance focus from the first field to the second when the IME
    // Next action fires. Compose iOS doesn't auto-traverse focus on Next the
    // way Android does, so this has to be wired explicitly.
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) { viewModel.resetState() }

    val onSubmit = { viewModel.login(email, password) }

    // Single scrollable column with footer included (see the original
    // keyboard-handling rationale): BoxWithConstraints supplies `maxHeight`
    // (the viewport pre-IME) so the weight-spacer keeps the footer at the
    // bottom with the keyboard down, and just below the fold (scroll-reachable)
    // with the keyboard up.
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
            Column(
                modifier = Modifier.heightIn(min = viewportMinHeight),
                verticalArrangement = Arrangement.spacedBy(40.dp),
            ) {
                Spacer(Modifier.height(8.dp))
                WordmarkLogo(modifier = Modifier.height(24.dp), tint = Moss)
                HeaderBlock()
                FormBlock(
                    email = email, onEmailChange = { email = it },
                    password = password, onPasswordChange = { password = it },
                    error = (uiState as? AuthUiState.Error)?.message,
                    onSubmit = onSubmit,
                    onEmailNext = { focusManager.moveFocus(FocusDirection.Down) },
                )
                CtaBlock(
                    loading = loading,
                    onSubmit = onSubmit,
                )
                Spacer(Modifier.weight(1f))
                Footer(
                    onDeveloperSettings = { showDeveloperSettings = true },
                    modifier = Modifier.padding(bottom = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun HeaderBlock() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Overline(text = "Sign in", color = Moss)
        Display(text = "Welcome\nback.", size = 52, color = Ink)
    }
}

@Composable
private fun FormBlock(
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    error: String?,
    onSubmit: () -> Unit,
    onEmailNext: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        ArcanaTextField(
            label = "Email",
            value = email,
            onValueChange = onEmailChange,
            placeholder = "you@domain.co",
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
            onImeAction = onEmailNext,
        )
        ArcanaTextField(
            label = "Password",
            value = password,
            onValueChange = onPasswordChange,
            placeholder = "Your password",
            secure = true,
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
            onImeAction = onSubmit,
        )
        if (error != null) {
            BodyText(text = error, size = 14, color = Danger)
        }
    }
}

@Composable
private fun CtaBlock(
    loading: Boolean,
    onSubmit: () -> Unit,
) {
    if (loading) {
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
    } else {
        PrimaryCta(label = "Sign in", onClick = onSubmit)
    }
}

@Composable
private fun Footer(
    onDeveloperSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Pre-launch only: lets testers swap the API base URL before login,
        // for environments where the default URL isn't reachable.
        TextLink(
            label = "Developer settings",
            onClick = onDeveloperSettings,
            color = Ash,
            underline = false,
            icon = ArcanaIcons.Settings,
        )
    }
}
