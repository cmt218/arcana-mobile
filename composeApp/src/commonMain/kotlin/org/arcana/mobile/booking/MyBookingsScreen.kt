package org.arcana.mobile.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.arcana.mobile.data.BookingDto
import org.arcana.mobile.theme.*
import org.arcana.mobile.ui.BodyText
import org.arcana.mobile.ui.Caption
import org.arcana.mobile.ui.Heading2
import org.arcana.mobile.ui.Heading3
import org.arcana.mobile.ui.SectionRule
import org.arcana.mobile.ui.StatusPill
import org.arcana.mobile.ui.TextLink
import org.arcana.mobile.ui.safeContentPadding
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MyBookingsScreen(onClose: () -> Unit) {
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
                        BookingRow(b, onCancel = { confirmCancel = b })
                    }
                }
                if (s.past.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(12.dp))
                        SectionRule("Past")
                        Spacer(Modifier.height(8.dp))
                    }
                    items(s.past) { b ->
                        BookingRow(b, onCancel = null)
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

@Composable
private fun BookingRow(b: BookingDto, onCancel: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BodyText(b.session.name, size = 16, color = Wood)
            val spotSuffix = b.spot?.let { " · ${it.label}" } ?: ""
            Caption("${b.session.studio}$spotSuffix", size = 12, color = Ash)
        }
        StatusPill(b.status)
        if (onCancel != null) {
            Spacer(Modifier.width(8.dp))
            TextLink(label = "Cancel", onClick = onCancel, color = BurntNectar)
        }
    }
}
