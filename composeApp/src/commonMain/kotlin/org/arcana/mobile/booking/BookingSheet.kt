package org.arcana.mobile.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.arcana.mobile.data.ScheduleSessionDto
import org.arcana.mobile.data.SpotDto
import org.arcana.mobile.theme.Ash
import org.arcana.mobile.theme.BurntNectar
import org.arcana.mobile.theme.Clay
import org.arcana.mobile.theme.Graphite
import org.arcana.mobile.theme.Mist
import org.arcana.mobile.theme.Moss
import org.arcana.mobile.theme.Stone
import org.arcana.mobile.theme.Wood
import org.arcana.mobile.ui.BodyText
import org.arcana.mobile.ui.Caption
import org.arcana.mobile.ui.CtaSpinner
import org.arcana.mobile.ui.Heading3
import org.arcana.mobile.ui.Overline
import org.arcana.mobile.ui.PrimaryCta

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingSheet(
    session: ScheduleSessionDto,
    requiresSpot: Boolean,
    selectedSpot: SpotDto?,
    creditsRemaining: Int?,
    onSelectSpot: (SpotDto) -> Unit,
    shouldAskStudioVisit: Boolean,
    visitedBefore: Boolean?,
    onAnswerVisit: (Boolean) -> Unit,
    confirmEnabled: Boolean,
    submitting: Boolean,
    errorMessage: String? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Stone) {
        // Scrollable so tall content (e.g. a big spot grid + the studio-visit
        // prompt + CONFIRM) is always reachable — never clipped off the bottom.
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            if (errorMessage != null) {
                // Booking was rejected — show the reason in the sheet itself and
                // replace the confirm controls with a single dismiss action.
                Heading3("Can't book this class", size = 20, color = Wood)
                Spacer(Modifier.height(8.dp))
                BodyText(session.template.name, size = 16, color = Wood)
                Caption(session.location.studio.name, size = 12, color = Ash)
                Spacer(Modifier.height(16.dp))
                BodyText(errorMessage, size = 14, color = Clay)
                Spacer(Modifier.height(20.dp))
                PrimaryCta(label = "GOT IT", onClick = onDismiss, enabled = true)
            } else {
                Heading3("Confirm booking", size = 20, color = Wood)
                Spacer(Modifier.height(8.dp))
                BodyText(session.template.name, size = 16, color = Wood)
                Caption(session.location.studio.name, size = 12, color = Ash)
                if (requiresSpot) {
                    Spacer(Modifier.height(16.dp))
                    Overline("Pick your spot", size = 11, color = Graphite)
                    Spacer(Modifier.height(8.dp))
                    SpotPicker(spots = session.spots, selected = selectedSpot, onSelect = onSelectSpot)
                }
                Spacer(Modifier.height(16.dp))
                val creditLine = creditsRemaining?.let { "This uses 1 of $it credits" } ?: "This uses 1 credit"
                Caption(creditLine, size = 13, color = Moss)
                Spacer(Modifier.height(8.dp))
                BodyText(
                    "Free to cancel until the studio cutoff. After that, the credit's spent even if you cancel.",
                    size = 12,
                    color = Graphite,
                )
                // One-time-per-studio prompt, right before confirm so we always
                // capture it. For spot classes the picker is above, so the order
                // is: pick spot -> answer -> confirm.
                if (shouldAskStudioVisit) {
                    Spacer(Modifier.height(20.dp))
                    StudioVisitPrompt(
                        studioName = session.location.studio.name,
                        answer = visitedBefore,
                        onAnswer = onAnswerVisit,
                    )
                }
                Spacer(Modifier.height(20.dp))
                PrimaryCta(
                    label = if (submitting) "BOOKING…" else "CONFIRM",
                    onClick = onConfirm,
                    enabled = if (submitting) false else confirmEnabled,
                    trailing = if (submitting) {
                        { CtaSpinner() }
                    } else null,
                )
            }
        }
    }
}

/**
 * One-time "have you been to {studioName} before?" Yes/No prompt. Two pills in the
 * SpotPicker idiom (Burnt Nectar when selected, Mist outline otherwise). The
 * caller gates CONFIRM until [answer] is non-null.
 */
@Composable
private fun StudioVisitPrompt(
    studioName: String,
    answer: Boolean?,
    onAnswer: (Boolean) -> Unit,
) {
    Overline(
        "Have you been to $studioName before?",
        size = 11,
        color = Graphite,
        maxLines = Int.MAX_VALUE,  // wrap long studio names instead of dropping "before?"
    )
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        VisitChip(label = "YES", selected = answer == true, onClick = { onAnswer(true) }, modifier = Modifier.weight(1f))
        VisitChip(label = "NO", selected = answer == false, onClick = { onAnswer(false) }, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun VisitChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (selected) Modifier.background(BurntNectar)
                else Modifier.border(1.dp, Mist, RoundedCornerShape(8.dp))
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Overline(label, size = 12, color = Stone)
        } else {
            Overline(label, size = 12, color = Wood)
        }
    }
}
