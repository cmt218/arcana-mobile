package org.arcana.mobile.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.arcana.mobile.data.BookingDto
import org.arcana.mobile.theme.*
import org.arcana.mobile.ui.ArcanaIcons
import org.arcana.mobile.ui.BodyText
import org.arcana.mobile.ui.Caption
import org.arcana.mobile.ui.Heading2
import org.arcana.mobile.ui.Heading3
import org.arcana.mobile.ui.IconCircle
import org.arcana.mobile.ui.SectionRule
import org.arcana.mobile.ui.StatusPill
import org.arcana.mobile.ui.TextLink
import org.arcana.mobile.ui.safeContentPadding
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MyBookingsScreen(onClose: () -> Unit, onOpenClass: (Int) -> Unit) {
    val vm = koinViewModel<MyBookingsViewModel>()
    LaunchedEffect(Unit) { vm.load() }
    val state by vm.uiState.collectAsState()
    var confirmCancel by remember { mutableStateOf<BookingDto?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Stone)
            .safeContentPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        IconCircle(
            icon = ArcanaIcons.Close,
            diameter = 38,
            iconSize = 18,
            background = Paper,
            borderColor = Mist,
            contentColor = Ink,
            onClick = onClose,
        )
        Spacer(Modifier.height(16.dp))
        Heading2("Your bookings", size = 26, color = Wood)
        Spacer(Modifier.height(16.dp))
        when (val s = state) {
            is MyBookingsUiState.Loading -> Caption("Loading…", size = 13, color = Ash)
            is MyBookingsUiState.Error -> Caption(s.message, size = 13, color = BurntNectar)
            is MyBookingsUiState.Success -> LazyColumn(Modifier.fillMaxSize()) {
                if (s.upcoming.isNotEmpty()) {
                    item {
                        SectionRule("Upcoming · ${s.upcoming.size}")
                        Spacer(Modifier.height(8.dp))
                    }
                    items(s.upcoming) { b ->
                        BookingRow(
                            b = b,
                            onCancel = { confirmCancel = b },
                            onClick = { onOpenClass(b.session.id) },
                        )
                    }
                }
                if (s.past.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(12.dp))
                        SectionRule("Past")
                        Spacer(Modifier.height(8.dp))
                    }
                    items(s.past) { b ->
                        BookingRow(
                            b = b,
                            onCancel = null,
                            onClick = { onOpenClass(b.session.id) },
                        )
                    }
                }
            }
        }
    }

    confirmCancel?.let { b ->
        val forfeits = b.cancelPolicy.willForfeitCredit
        AlertDialog(
            onDismissRequest = { confirmCancel = null },
            title = { Heading3("Cancel this booking?", size = 18, color = Wood) },
            text = {
                BodyText(
                    text = if (forfeits) "It's inside the cancellation window, so your credit won't come back."
                    else "Your credit will be refunded.",
                    size = 14,
                    color = Graphite,
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.cancel(b.id); confirmCancel = null }) {
                    BodyText("Cancel booking", size = 14, color = BurntNectar)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancel = null }) {
                    BodyText("Keep it", size = 14, color = Moss)
                }
            },
        )
    }
}

/** Formats an ISO-8601 start time as "Tue, Jun 2 · 5:00 PM" — Kotlin/Native-safe. */
private fun formatBookingDateTime(startAt: String): String {
    return try {
        val tz = TimeZone.currentSystemDefault()
        val local = Instant.parse(startAt).toLocalDateTime(tz)
        val dow = local.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.titlecase() }
        val mon = local.month.name.take(3).lowercase().replaceFirstChar { it.titlecase() }
        val day = local.date.day
        val h = if (local.hour % 12 == 0) 12 else local.hour % 12
        val m = local.minute.toString().padStart(2, '0')
        val ampm = if (local.hour < 12) "AM" else "PM"
        "$dow, $mon $day · $h:$m $ampm"
    } catch (_: Exception) {
        startAt.take(16).replace("T", " ")
    }
}

@Composable
private fun BookingRow(b: BookingDto, onCancel: (() -> Unit)?, onClick: () -> Unit) {
    val dateTimeLabel = remember(b.session.startAt) { formatBookingDateTime(b.session.startAt) }
    val locationSuffix = b.session.location?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""
    val spotSuffix = b.spot?.let { " · ${it.label}" } ?: ""
    val studioSpot = "${b.session.studio}$locationSuffix$spotSuffix"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left column is the tappable area that opens class detail.
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClick),
        ) {
            BodyText(b.session.name, size = 16, color = Wood)
            Spacer(Modifier.height(2.dp))
            Caption(dateTimeLabel, size = 12, color = Ash)
            Spacer(Modifier.height(2.dp))
            val instructorSuffix = b.session.instructor?.let { " · with $it" } ?: ""
            Caption("$studioSpot$instructorSuffix", size = 12, color = Ash)
        }
        Spacer(Modifier.width(8.dp))
        // Trailing controls are independent tap targets — NOT inside the clickable Column.
        StatusPill(b.status)
        if (onCancel != null) {
            Spacer(Modifier.width(8.dp))
            TextLink(label = "Cancel", onClick = onCancel, color = BurntNectar)
        }
    }
}
