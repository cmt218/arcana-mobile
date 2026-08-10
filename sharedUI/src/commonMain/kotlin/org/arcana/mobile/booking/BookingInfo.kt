package org.arcana.mobile.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.arcana.mobile.data.BookingDto
import org.arcana.mobile.theme.Ink
import org.arcana.mobile.theme.Paper
import org.arcana.mobile.ui.BodyText
import org.arcana.mobile.ui.SectionRule

// bookingInfoOrNull lives in :sharedLogic booking/BookingNotes.kt (same package).

/** Class-detail "Booking info" section. Caller gates on [bookingInfoOrNull]. */
@Composable
fun BookingInfoCallout(note: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        SectionRule(label = "Booking info")
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Paper)
                .padding(16.dp),
        ) {
            BodyText(text = note, size = 15, color = Ink)
        }
    }
}
