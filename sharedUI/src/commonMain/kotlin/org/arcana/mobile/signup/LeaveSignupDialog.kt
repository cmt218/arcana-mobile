package org.arcana.mobile.signup

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import org.arcana.mobile.theme.BurntNectar
import org.arcana.mobile.theme.Graphite
import org.arcana.mobile.theme.Moss
import org.arcana.mobile.theme.Wood
import org.arcana.mobile.ui.BodyText
import org.arcana.mobile.ui.Heading3

/** Which signup screen back was pressed on. Only the body copy differs. */
enum class SignupStep { Survey, Claim }

/** Plain strings so the copy itself is unit testable. */
object LeaveSignupCopy {
    const val TITLE = "Leave signup?"
    const val CONFIRM = "Leave"
    const val DISMISS = "Keep going"

    fun body(step: SignupStep): String = when (step) {
        SignupStep.Survey ->
            "Your answers are not saved yet, so leaving now means starting the survey over."
        SignupStep.Claim ->
            "Your details are not saved yet, so leaving now means entering them again."
    }
}

/** Confirms leaving signup, so a stray back press cannot discard what was typed. */
@Composable
fun LeaveSignupDialog(
    step: SignupStep,
    onStay: () -> Unit,
    onLeave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onStay,
        title = { Heading3(LeaveSignupCopy.TITLE, size = 18, color = Wood) },
        text = { BodyText(text = LeaveSignupCopy.body(step), size = 14, color = Graphite) },
        confirmButton = {
            TextButton(onClick = onLeave) {
                BodyText(LeaveSignupCopy.CONFIRM, size = 14, color = BurntNectar)
            }
        },
        dismissButton = {
            TextButton(onClick = onStay) {
                BodyText(LeaveSignupCopy.DISMISS, size = 14, color = Moss)
            }
        },
    )
}
