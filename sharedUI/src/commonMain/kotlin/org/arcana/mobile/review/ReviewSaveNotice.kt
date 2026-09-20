package org.arcana.mobile.review

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import org.arcana.mobile.networking.transportErrorCopy
import org.arcana.mobile.ui.ErrorSnackbar
import org.arcana.mobile.ui.TransientSurface

private const val NOTICE_MS = 6_000L
private const val FALLBACK = "Couldn't save that. Try again."

/** The one "that didn't save" notice a screen shows for the review cards on it. */
@Stable
class ReviewSaveNotice {
    var code by mutableStateOf<String?>(null)
        private set

    fun show(code: String) {
        this.code = code
    }

    fun dismiss() {
        code = null
    }
}

@Composable
fun rememberReviewSaveNotice(): ReviewSaveNotice = remember { ReviewSaveNotice() }

/**
 * A save that failed says so here, in the words every submit flow uses
 * (connection or server, never confused). No retry: the card keeps what the
 * member entered and its Done stays live, so trying again is the same tap.
 */
@Composable
fun ReviewSaveNoticeHost(notice: ReviewSaveNotice, modifier: Modifier = Modifier) {
    // Held through the exit fade, when the code is already gone.
    var text by remember { mutableStateOf(FALLBACK) }
    notice.code?.let { text = transportErrorCopy(it) ?: FALLBACK }
    LaunchedEffect(notice.code) {
        if (notice.code != null) {
            delay(NOTICE_MS)
            notice.dismiss()
        }
    }
    TransientSurface(visible = notice.code != null, modifier = modifier) {
        ErrorSnackbar(text = text, onDismiss = notice::dismiss)
    }
}
