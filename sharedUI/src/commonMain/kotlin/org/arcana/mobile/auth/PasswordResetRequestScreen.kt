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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.arcana.mobile.theme.Ash
import org.arcana.mobile.theme.Danger
import org.arcana.mobile.theme.Ink
import org.arcana.mobile.theme.Lime
import org.arcana.mobile.theme.Moss
import org.arcana.mobile.theme.Stone
import org.arcana.mobile.theme.WordmarkLogo
import org.arcana.mobile.ui.ArcanaTextField
import org.arcana.mobile.ui.BodyText
import org.arcana.mobile.ui.Display
import org.arcana.mobile.ui.Overline
import org.arcana.mobile.ui.PrimaryCta
import org.arcana.mobile.ui.safeContentPadding

@Composable
fun PasswordResetRequestScreen(
    viewModel: PasswordResetRequestViewModel,
    onBackToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { viewModel.resetState() }
    val email by viewModel.email.collectAsState()
    val submit by viewModel.submitState.collectAsState()
    val focusManager = LocalFocusManager.current

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
                verticalArrangement = Arrangement.spacedBy(36.dp),
            ) {
                Spacer(Modifier.height(8.dp))
                WordmarkLogo(modifier = Modifier.height(24.dp), tint = Moss)

                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Overline(text = "Password reset", color = Moss)
                    Display(text = "Regain\naccess.", size = 52, color = Ink)
                }

                if (submit is PasswordResetSubmit.Sent) {
                    SentBlock(onBackToLogin = onBackToLogin)
                } else {
                    ArcanaTextField(
                        label = "Email",
                        value = email,
                        onValueChange = viewModel::updateEmail,
                        placeholder = "you@domain.co",
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Done,
                        onImeAction = {
                            focusManager.moveFocus(FocusDirection.Down)
                            viewModel.submit()
                        },
                        contentType = ContentType.EmailAddress,
                    )
                    if (submit is PasswordResetSubmit.Failed) {
                        BodyText(
                            text = "Couldn't reach the server. Check your connection and try again.",
                            size = 14,
                            color = Danger,
                        )
                    }
                    if (submit is PasswordResetSubmit.Submitting) {
                        LoadingPill()
                    } else {
                        PrimaryCta(
                            label = "Send reset email",
                            onClick = viewModel::submit,
                            enabled = viewModel.canSubmit,
                        )
                    }
                    BackLink(onBackToLogin = onBackToLogin)
                }

                Spacer(Modifier.weight(1f))
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SentBlock(onBackToLogin: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        BodyText(
            text = "If an account exists with this email, we'll send password reset instructions to it.",
            size = 16,
            color = Ink,
        )
        PrimaryCta(label = "Back to sign in", onClick = onBackToLogin)
    }
}

@Composable
private fun BackLink(onBackToLogin: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onBackToLogin) {
        BodyText(
            text = "Back to sign in",
            size = 14,
            color = Ash,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

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
