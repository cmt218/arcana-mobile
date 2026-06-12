package org.arcana.mobile.booking

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.arcana.mobile.data.ScheduleSessionDto
import org.arcana.mobile.data.SpotDto
import org.arcana.mobile.theme.Ash
import org.arcana.mobile.theme.Graphite
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
    confirmEnabled: Boolean,
    submitting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Stone) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
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
                "Free to cancel until the studio's cutoff. After that, the credit's spent even if you cancel.",
                size = 12,
                color = Graphite,
            )
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
