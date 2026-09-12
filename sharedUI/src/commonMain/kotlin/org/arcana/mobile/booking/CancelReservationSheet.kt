package org.arcana.mobile.booking

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.arcana.mobile.theme.Ash
import org.arcana.mobile.theme.BurntNectar
import org.arcana.mobile.theme.Clay
import org.arcana.mobile.theme.ClayDeep
import org.arcana.mobile.theme.Ink
import org.arcana.mobile.theme.Moss
import org.arcana.mobile.theme.Warning
import org.arcana.mobile.ui.ArcanaSheet
import org.arcana.mobile.ui.BodyText
import org.arcana.mobile.ui.Caption
import org.arcana.mobile.ui.CtaSpinner
import org.arcana.mobile.ui.Heading3
import org.arcana.mobile.ui.HoldToConfirm
import org.arcana.mobile.ui.PrimaryCta

/**
 * Confirmation sheet for cancelling a reservation, shared by class detail and
 * the Reservations list. The forfeit warning follows the booking's cancel
 * policy: past the studio cutoff the credit is lost (Warning), otherwise it
 * is refunded (Moss).
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun CancelReservationSheet(
    className: String,
    spotLabel: String?,
    willForfeitCredit: Boolean,
    cancelState: CancelState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState,
) {
    val submitting = cancelState is CancelState.Submitting
    ArcanaSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Heading3("Cancel reservation?", size = 20, color = Ink)
            Spacer(Modifier.height(8.dp))
            BodyText(className, size = 16, color = Ink)
            if (spotLabel != null) {
                Spacer(Modifier.height(2.dp))
                Caption(spotLabel, size = 12, color = Ash)
            }
            Spacer(Modifier.height(16.dp))
            if (willForfeitCredit) {
                BodyText(
                    "Cancelling now forfeits this class's credit. You're past the studio cutoff.",
                    size = 13, color = Warning,
                )
            } else {
                BodyText("You'll get your credit back.", size = 13, color = Moss)
            }
            Spacer(Modifier.height(20.dp))
            if (useBookingGestures()) {
                HoldToConfirm(
                    label = if (submitting) "CANCELLING…" else "HOLD TO CANCEL",
                    onConfirm = onConfirm,
                    enabled = !submitting,
                )
            } else {
                PrimaryCta(
                    label = if (submitting) "CANCELLING…" else "CANCEL RESERVATION",
                    onClick = onConfirm,
                    enabled = !submitting,
                    containerColor = Clay,
                    accentColor = ClayDeep,
                    trailing = if (submitting) {
                        { CtaSpinner() }
                    } else null,
                )
            }
            if (cancelState is CancelState.Failed) {
                Spacer(Modifier.height(12.dp))
                // cancelErrorCopy (not bookingErrorCopy) so an unmapped code
                // still falls back to cancel-appropriate copy.
                Caption(cancelErrorCopy(cancelState.code), size = 13, color = BurntNectar, maxLines = 3)
            }
        }
    }
}
