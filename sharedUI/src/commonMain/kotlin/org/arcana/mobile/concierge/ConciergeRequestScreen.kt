package org.arcana.mobile.concierge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.arcana.mobile.networking.transportErrorCopy
import org.arcana.mobile.theme.Outline
import org.arcana.mobile.theme.Ash
import org.arcana.mobile.theme.Charcoal
import org.arcana.mobile.theme.Atmosphere
import org.arcana.mobile.theme.BurntNectar
import org.arcana.mobile.theme.Ink
import org.arcana.mobile.theme.Lime
import org.arcana.mobile.theme.Moss
import org.arcana.mobile.theme.Surface
import org.arcana.mobile.theme.Wood
import org.arcana.mobile.ui.ArcanaIcons
import org.arcana.mobile.ui.ArcanaMultilineTextField
import org.arcana.mobile.ui.BodyText
import org.arcana.mobile.ui.Caption
import org.arcana.mobile.ui.Display
import org.arcana.mobile.ui.Heading2
import org.arcana.mobile.ui.IconCircle
import org.arcana.mobile.ui.Overline
import org.arcana.mobile.ui.PrimaryCta
import org.arcana.mobile.ui.SettlingCta
import org.arcana.mobile.ui.StrokeIcon
import org.arcana.mobile.ui.safeBottomBarPadding
import org.arcana.mobile.ui.safeContentPadding
import org.koin.compose.viewmodel.koinViewModel

/**
 * Concierge — a member's direct line to the founders. Reached from the Account
 * tab's Concierge row. Free-form message box → submit → fire-and-forget
 * confirmation. The founders reach back out-of-band; there's no in-app history.
 */
@Composable
fun ConciergeRequestScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm = koinViewModel<ConciergeRequestViewModel>()
    val message by vm.message.collectAsState()
    val submit by vm.submitState.collectAsState()
    val messageFocus = remember { FocusRequester() }
    val focus = LocalFocusManager.current

    if (submit is ConciergeSubmit.Sent) {
        SentConfirmation(onClose = onClose, modifier = modifier)
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
    Atmosphere()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeContentPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState())
            .imePadding(),
    ) {
        IconCircle(
            icon = ArcanaIcons.Close,
            diameter = 38,
            iconSize = 18,
            background = Surface,
            borderColor = Outline,
            contentColor = Ink,
            onClick = onClose,
            contentDescription = "Close",
        )
        Spacer(Modifier.height(20.dp))
        Overline(text = "Concierge", color = Moss)
        Spacer(Modifier.height(12.dp))
        Heading2("Reach the founders", size = 26, color = Wood)
        Spacer(Modifier.height(14.dp))
        BodyText(
            text = "Having an issue, or want to reach the founders directly? " +
                "Tell us what's going on and we'll be in touch.",
            size = 15,
            color = Charcoal,
        )

        Spacer(Modifier.height(28.dp))
        // One line with the words on the rule, growing to three, then scrolling:
        // the same field as a review's comment.
        ArcanaMultilineTextField(
            label = "Your message",
            value = message,
            onValueChange = vm::updateMessage,
            maxLength = ConciergeRequestViewModel.MESSAGE_MAX_LENGTH,
            placeholder = "What's going on?",
            minLines = 1,
            maxLines = 3,
            idleColor = Charcoal,
            modifier = Modifier.fillMaxWidth().focusRequester(messageFocus),
        )

        val failed = submit as? ConciergeSubmit.Failed
        if (failed != null) {
            Spacer(Modifier.height(12.dp))
            Caption(
                text = transportErrorCopy(failed.code)
                    ?: "Couldn't send your message. Try again.",
                size = 13,
                color = BurntNectar,
                maxLines = 3,
            )
        }

        Spacer(Modifier.height(28.dp))
        // Always Moss, never greyed: with nothing written a tap goes to the field,
        // and a slow send sweeps the pill instead of swapping it for a spinner.
        SettlingCta(
            label = "Send",
            settledLabel = "Sent",
            settled = false,
            busy = submit is ConciergeSubmit.Submitting,
            onClick = {
                if (message.isBlank()) {
                    messageFocus.requestFocus()
                } else {
                    focus.clearFocus()
                    vm.submit()
                }
            },
        )
    }
    }
}

/** Fire-and-forget success state. No history — the founders follow up directly. */
@Composable
private fun SentConfirmation(onClose: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
    Atmosphere()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeContentPadding()
            // Lift the Done button clear of the home indicator / bottom inset.
            .safeBottomBarPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        IconCircle(
            icon = ArcanaIcons.Close,
            diameter = 38,
            iconSize = 18,
            background = Surface,
            borderColor = Outline,
            contentColor = Ink,
            onClick = onClose,
            contentDescription = "Close",
        )
        Spacer(Modifier.weight(1f))
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.size(56.dp).clip(CircleShape).background(Moss),
                contentAlignment = Alignment.Center,
            ) {
                // decorative — "Message sent." below states the outcome.
                StrokeIcon(icon = ArcanaIcons.Check, size = 26.dp, tint = Lime)
            }
            Spacer(Modifier.height(24.dp))
            Display(
                text = "Message sent.",
                size = 44,
                color = Ink,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            BodyText(
                text = "We've got it. The founders will reach out to you directly.",
                size = 15,
                color = Charcoal,
            )
        }
        Spacer(Modifier.weight(1f))
        PrimaryCta(label = "Done", onClick = onClose)
    }
    }
}
