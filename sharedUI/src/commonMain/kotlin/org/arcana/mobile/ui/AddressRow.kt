package org.arcana.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.arcana.mobile.analytics.Telemetry
import org.arcana.mobile.maps.MapTarget
import org.arcana.mobile.theme.Ash
import org.arcana.mobile.theme.Charcoal
import org.arcana.mobile.theme.Ash2
import org.arcana.mobile.theme.Ink
import org.arcana.mobile.theme.Mist2
import org.arcana.mobile.theme.Moss
import org.jetbrains.compose.resources.DrawableResource
import org.koin.compose.koinInject

private const val COPIED_HOLD_MS = 700L

/**
 * A location the member can open in a maps app: name, address and a chevron.
 * The whole row is the control. Tapping opens [AddressSheet]. [surface] is the
 * `address_tapped` telemetry surface (class_detail, reservation_row,
 * home_next_up, studio_page).
 */
@Composable
fun AddressRow(
    name: String,
    address: String,
    latitude: Double?,
    longitude: Double?,
    surface: String,
    modifier: Modifier = Modifier,
    /** What maps apps search for, e.g. "Barry's Chelsea"; defaults to [name]. */
    businessName: String? = null,
    overline: String? = null,
    nameAsDisplay: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
) {
    var sheetOpen by remember { mutableStateOf(false) }
    val source = remember { MutableInteractionSource() }
    val label = accessibleLabel(name, address)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressable(source, pressedScale = 0.99f)
            .clickable(interactionSource = source, indication = null, role = Role.Button) { sheetOpen = true }
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        leading?.invoke()
        Column(modifier = Modifier.weight(1f)) {
            if (overline != null) {
                Overline(text = overline, size = 10, color = Charcoal)
                Spacer(Modifier.height(4.dp))
            }
            if (nameAsDisplay) Display(text = name, size = 18, color = Ink)
            else BodyText(text = name, size = 16, color = Ink)
            if (address.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                BodyText(text = address, size = 12, color = Charcoal)
            }
        }
        // decorative — the row's semantics carry the label.
        StrokeIcon(icon = ArcanaIcons.ChevronRight, size = 16.dp, tint = Ash2)
    }
    if (sheetOpen) {
        AddressSheet(
            target = MapTarget(name, address, latitude, longitude, business = businessName ?: name),
            surface = surface,
            onDismiss = { sheetOpen = false },
        )
    }
}

/** One-line variant for dense rows and cards: the address and a small chevron. */
@Composable
fun AddressLink(
    name: String,
    address: String,
    latitude: Double?,
    longitude: Double?,
    surface: String,
    modifier: Modifier = Modifier,
    businessName: String? = null,
    color: Color = Charcoal,
) {
    var sheetOpen by remember { mutableStateOf(false) }
    val source = remember { MutableInteractionSource() }
    val label = accessibleLabel(name, address)
    Row(
        modifier = modifier
            .pressable(source, pressedScale = 0.98f)
            .clickable(interactionSource = source, indication = null, role = Role.Button) { sheetOpen = true }
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Caption(text = address, size = 12, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        // decorative — the row's semantics carry the label.
        StrokeIcon(icon = ArcanaIcons.ChevronRight, size = 12.dp, tint = color)
    }
    if (sheetOpen) {
        AddressSheet(
            target = MapTarget(name, address, latitude, longitude, business = businessName ?: name),
            surface = surface,
            onDismiss = { sheetOpen = false },
        )
    }
}

private fun accessibleLabel(name: String, address: String): String =
    if (address.isBlank()) "$name, open in maps" else "$name, $address, open in maps"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddressSheet(target: MapTarget, surface: String, onDismiss: () -> Unit) {
    val telemetry = koinInject<Telemetry>()
    val apps = remember { MapsLauncher.availableApps() }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(COPIED_HOLD_MS)
            onDismiss()
        }
    }
    ArcanaSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Heading3(text = target.business, size = 20, color = Ink)
            if (target.address.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Caption(text = target.address, size = 13, color = Ash, maxLines = 2)
            }
            Spacer(Modifier.height(12.dp))
            apps.forEach { app ->
                SheetAction(label = app.label, icon = ArcanaIcons.ArrowUpRight) {
                    telemetry.addressTapped(surface = surface, app = app.key)
                    MapsLauncher.open(app, target)
                    onDismiss()
                }
            }
            if (target.address.isNotBlank()) {
                SheetAction(
                    label = if (copied) "Copied" else "Copy address",
                    icon = if (copied) ArcanaIcons.Check else ArcanaIcons.Share,
                    enabled = !copied,
                ) {
                    telemetry.addressTapped(surface = surface, app = "copy")
                    MapsLauncher.copyAddress(target.address)
                    copied = true
                }
            }
        }
    }
}

@Composable
private fun SheetAction(
    label: String,
    icon: DrawableResource,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(source, enabled = enabled, pressedScale = 0.98f)
            .clickable(enabled = enabled, interactionSource = source, indication = null, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Mist2),
            contentAlignment = Alignment.Center,
        ) {
            // decorative — the label names the action.
            StrokeIcon(icon = icon, size = 18.dp, tint = if (enabled) Ink else Moss)
        }
        BodyText(text = label, size = 16, color = Ink)
    }
}
