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
 * Gateway — sign-in / create-account. Both modes share one Stone shell;
 * only the copy and CTA differ. Layout is grouped (wordmark / header / form / cta)
 * with Arrangement.spacedBy + a weight-pushed footer so it breathes on any
 * screen size rather than relying on hand-tuned spacers.
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

    var isLoginMode by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsState()
    val loading = uiState is AuthUiState.Loading
    // Lets us advance focus from the first field to the second when the IME
    // Next action fires. Compose iOS doesn't auto-traverse focus on Next the
    // way Android does, so this has to be wired explicitly.
    val focusManager = LocalFocusManager.current

    LaunchedEffect(isLoginMode) { viewModel.resetState() }

    val onSubmit = {
        if (isLoginMode) viewModel.login(email, password)
        else viewModel.register(email, password)
    }

    // Single scrollable column with footer included.
    //
    // The footer used to live OUTSIDE the scroll so the keyboard could cover
    // it without dragging it up — but that meant the user couldn't reach the
    // footer at all while the keyboard was up. This layout collapses to one
    // scroll with the footer pinned to the bottom of the viewport via a
    // `Spacer.weight(1f)`, which gives us the best of both:
    //
    //  - Keyboard down: footer reads at the bottom of the screen as before
    //    (the inner column's `heightIn(min = maxHeight)` makes it fill the
    //    viewport, so the weight spacer has slack to push the footer down).
    //  - Keyboard up: `imePadding` shrinks the scroll viewport. The inner
    //    column still claims the *original* viewport height as its minimum,
    //    so the footer ends up just below the fold — reachable via scroll
    //    instead of riding the top edge of the keyboard.
    //
    // BoxWithConstraints supplies `maxHeight` (the viewport pre-IME) so the
    // weight-spacer trick works inside an otherwise-unbounded scroll.
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
                HeaderBlock(isLoginMode)
                FormBlock(
                    isLoginMode = isLoginMode,
                    email = email, onEmailChange = { email = it },
                    password = password, onPasswordChange = { password = it },
                    error = (uiState as? AuthUiState.Error)?.message,
                    onSubmit = onSubmit,
                    onEmailNext = { focusManager.moveFocus(FocusDirection.Down) },
                )
                CtaBlock(
                    isLoginMode = isLoginMode,
                    loading = loading,
                    onSubmit = onSubmit,
                )
                Spacer(Modifier.weight(1f))
                Footer(
                    isLoginMode = isLoginMode,
                    onToggle = { isLoginMode = !isLoginMode },
                    onDeveloperSettings = { showDeveloperSettings = true },
                    modifier = Modifier.padding(bottom = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun HeaderBlock(isLoginMode: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Overline(text = if (isLoginMode) "Sign in" else "Create account", color = Moss)
        Display(
            text = if (isLoginMode) "Welcome\nback." else "Welcome.",
            size = 52,
            color = Ink,
        )
    }
}

@Composable
private fun FormBlock(
    isLoginMode: Boolean,
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
            placeholder = if (isLoginMode) "Your password" else "At least 8 characters",
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
    isLoginMode: Boolean,
    loading: Boolean,
    onSubmit: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
            PrimaryCta(
                label = if (isLoginMode) "Sign in" else "Create account",
                onClick = onSubmit,
            )
        }
        if (!isLoginMode) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                BodyText(
                    text = "Continuing accepts the Member Agreement.",
                    size = 12,
                    color = Ash,
                )
            }
        }
    }
}

@Composable
private fun Footer(
    isLoginMode: Boolean,
    onToggle: () -> Unit,
    onDeveloperSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Overline(
            text = if (isLoginMode) "New here?" else "Already a member?",
            size = 10,
            color = Ash,
        )
        TextLink(
            label = if (isLoginMode) "Sign up" else "Sign in",
            onClick = onToggle,
            color = Moss,
            icon = if (isLoginMode) ArcanaIcons.ArrowUpRight else ArcanaIcons.ArrowRight,
        )
        Spacer(Modifier.height(8.dp))
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

