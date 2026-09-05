package org.arcana.mobile.settings

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.arcana.mobile.theme.Ash
import org.arcana.mobile.theme.Danger
import org.arcana.mobile.theme.Ink
import org.arcana.mobile.theme.Moss
import org.arcana.mobile.theme.Stone
import org.arcana.mobile.ui.ArcanaIcons
import org.arcana.mobile.ui.ArcanaTextField
import org.arcana.mobile.ui.BodyText
import org.arcana.mobile.ui.Heading2
import org.arcana.mobile.ui.Overline
import org.arcana.mobile.ui.PrimaryCta
import org.arcana.mobile.ui.SectionRule
import org.arcana.mobile.ui.StrokeIcon
import org.arcana.mobile.ui.TextLink
import org.arcana.mobile.ui.safeContentPadding
import org.koin.compose.viewmodel.koinViewModel

/**
 * Pre-launch utility for swapping the API base URL at runtime, paired with
 * the Cloudflare quick-tunnel workflow. Cole shares the tunnel URL with
 * cofounder/testers; they paste it here once and the override persists.
 *
 * Currently always visible (no debug-only gate). Once prod ships and DNS
 * settles on a stable hostname, we can either gate this on a debug build
 * type or surface it as a "support" affordance for production troubleshooting.
 */
@Composable
fun DeveloperSettingsScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DeveloperSettingsViewModel = koinViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.resetState() }
    val draft by viewModel.draft.collectAsState()
    val current by viewModel.currentUrl.collectAsState()
    val status by viewModel.status.collectAsState()

    // Composition swap rather than a nav destination, so App.kt and the iOS
    // AuthFlowRoot stay untouched and the page reaches both platforms.
    var showDesignSystem by rememberSaveable { mutableStateOf(false) }
    if (showDesignSystem) {
        DesignSystemScreen(onBack = { showDesignSystem = false })
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Stone)
            .safeContentPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        // Header row — close affordance + title
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .clickable(onClick = onClose)
                    .padding(4.dp),
            ) {
                StrokeIcon(
                    icon = ArcanaIcons.Close,
                    size = 20.dp,
                    tint = Ink,
                    contentDescription = "Close developer settings",
                )
            }
            Heading2(text = "Developer settings", size = 22, color = Ink)
        }

        // API BASE URL section
        SectionRule(
            label = "API base URL",
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 12.dp),
        )

        BodyText(
            text = "The URL the app talks to. Paste in a Cloudflare quick-tunnel URL " +
                "(or a local dev URL) and save — the change applies to the next request.",
            size = 13,
            color = Ash,
            modifier = Modifier.padding(horizontal = 24.dp),
        )

        Spacer(Modifier.height(20.dp))

        ArcanaTextField(
            label = "Base URL",
            value = draft,
            onValueChange = viewModel::onDraftChange,
            placeholder = "https://example.trycloudflare.com",
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Done,
            onImeAction = { viewModel.save() },
            modifier = Modifier.padding(horizontal = 24.dp),
        )

        Spacer(Modifier.height(8.dp))

        // Live indicator of what the network layer is actually using
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Overline(text = "Currently in use", size = 10, color = Ash)
            Spacer(Modifier.height(4.dp))
            BodyText(text = current, size = 14, color = Ink)
            if (viewModel.isOverridden) {
                Spacer(Modifier.height(4.dp))
                Overline(
                    text = "OVERRIDE · default is ${viewModel.defaultUrl}",
                    size = 10,
                    color = Moss,
                )
            } else {
                Spacer(Modifier.height(4.dp))
                Overline(text = "USING DEFAULT", size = 10, color = Ash)
            }
        }

        Spacer(Modifier.height(24.dp))

        // Save + Reset stack. Save closes on success (closing is itself the
        // confirmation — the user sees they're back at the auth screen with
        // the override applied). Save stays open on validation failure so the
        // user can see the inline error and fix the input.
        PrimaryCta(
            label = "Save",
            onClick = {
                viewModel.save()
                if (viewModel.status.value is DeveloperSettingsViewModel.Status.Saved) {
                    onClose()
                }
            },
            enabled = draft.isNotBlank() && draft != current,
            modifier = Modifier.padding(horizontal = 24.dp),
        )

        Spacer(Modifier.height(12.dp))

        if (viewModel.isOverridden) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .clickable(onClick = {
                        viewModel.reset()
                        onClose()
                    })
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Overline(text = "Reset to default", size = 12, color = Danger)
            }
        }

        // Inline error from a failed Save (Saved state never reaches this
        // branch because we close on success). Idle = no-op.
        Spacer(Modifier.height(8.dp))
        val s = status
        if (s is DeveloperSettingsViewModel.Status.Error) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Overline(text = s.message.uppercase(), size = 12, color = Danger)
            }
        }

        SectionRule(
            label = "Reference",
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp),
        )

        TextLink(
            label = "Design system",
            onClick = { showDesignSystem = true },
            modifier = Modifier.padding(horizontal = 24.dp),
        )

        Spacer(Modifier.height(32.dp))
    }
}
