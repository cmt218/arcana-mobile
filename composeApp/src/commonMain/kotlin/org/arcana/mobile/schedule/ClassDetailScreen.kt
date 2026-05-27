package org.arcana.mobile.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.time.Instant
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.arcana.mobile.data.ScheduleSessionDto
import org.arcana.mobile.theme.Arcana
import org.arcana.mobile.theme.Ash
import org.arcana.mobile.theme.Ash2
import org.arcana.mobile.theme.Ink
import org.arcana.mobile.theme.Mist
import org.arcana.mobile.theme.Moss
import org.arcana.mobile.theme.MossLight
import org.arcana.mobile.theme.Stone
import org.arcana.mobile.theme.Warning
import org.arcana.mobile.ui.ArcanaIcons
import org.arcana.mobile.ui.BodyText
import org.arcana.mobile.ui.Display
import org.arcana.mobile.ui.Heading2
import org.arcana.mobile.ui.Overline
import org.arcana.mobile.ui.SectionRule
import org.arcana.mobile.ui.StrokeIcon
import org.arcana.mobile.ui.safeContentPadding
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

// Copy of Month.abbr() from ScheduleScreen — small intentional duplication
// to keep both files self-contained without promoting the helper to internal.
private fun Month.abbr(): String = name.take(3)

// Copy of studioColorFor from ScheduleScreen — small intentional duplication
// to keep both files self-contained without promoting the helper to internal.
private fun studioColorFor(primaryColor: String): Color {
    if (primaryColor.length != 7 || !primaryColor.startsWith("#")) return Moss
    return try {
        val r = primaryColor.substring(1, 3).toInt(16)
        val g = primaryColor.substring(3, 5).toInt(16)
        val b = primaryColor.substring(5, 7).toInt(16)
        Color(r, g, b)
    } catch (_: NumberFormatException) {
        Moss
    }
}

@Composable
fun ClassDetailScreen(
    sessionId: Int,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ClassDetailViewModel = koinViewModel { parametersOf(sessionId) },
) {
    val state by viewModel.uiState.collectAsState()

    Box(
        modifier = modifier.fillMaxSize().background(Stone).safeContentPadding(),
    ) {
        when (val s = state) {
            ClassDetailUiState.Loading -> LoadingBlock(onClose)
            is ClassDetailUiState.Error -> ErrorBlock(message = s.message, onClose = onClose, onRetry = viewModel::reload)
            is ClassDetailUiState.Success -> SuccessBlock(session = s.session, onClose = onClose)
        }
    }
}

@Composable
private fun CloseHeader(onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.clickable(onClick = onClose).padding(4.dp)) {
            StrokeIcon(icon = ArcanaIcons.Close, size = 20.dp, tint = Ink)
        }
    }
}

@Composable
private fun LoadingBlock(onClose: () -> Unit) {
    Column {
        CloseHeader(onClose)
        Row(modifier = Modifier.padding(horizontal = 24.dp)) {
            Overline(text = "LOADING…", size = 12, color = Ash)
        }
    }
}

@Composable
private fun ErrorBlock(message: String, onClose: () -> Unit, onRetry: () -> Unit) {
    Column {
        CloseHeader(onClose)
        Column(modifier = Modifier.padding(24.dp)) {
            Heading2(text = "Couldn't load class", size = 22, color = Ink)
            Spacer(Modifier.height(8.dp))
            BodyText(text = message, size = 14, color = Ash)
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Ink)
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Overline(text = "RETRY", size = 12, color = Stone)
            }
        }
    }
}

@Composable
private fun SuccessBlock(session: ScheduleSessionDto, onClose: () -> Unit) {
    val tz = remember { TimeZone.currentSystemDefault() }
    val startLocal = remember(session.startAt) { Instant.parse(session.startAt).toLocalDateTime(tz) }
    val isCancelled = session.status == "cancelled_by_studio"
    val studio = session.location.studio
    val sc = studioColorFor(studio.primaryColor)
    val instructorLine = session.instructors.joinToString(" · ") { it.name }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item("close-header") { CloseHeader(onClose) }
        item("hero") {
            // Placeholder for the class hero image. Compose Multiplatform doesn't
            // yet have a network-image loader configured (no Coil-MP / Kamel dep).
            // For now we render a studio-tinted Box; when an image library lands,
            // swap to AsyncImage on session.template.heroImageUrl.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(sc.copy(alpha = 0.25f)),
            )
        }
        item("studio-chip") {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(sc))
                Overline(text = studio.name.uppercase(), size = 11, color = sc)
            }
        }
        item("class-name") {
            Display(
                text = session.template.name,
                size = 36, color = Ink,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp),
            )
        }
        item("meta-strip") {
            val modality = session.template.modality
            val dateText = "${startLocal.date.day} ${startLocal.date.month.abbr()}".uppercase()
            Row(
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (modality.isNotBlank()) {
                    Overline(text = modality.uppercase(), size = 12, color = Ink)
                    Box(Modifier.size(4.dp).clip(CircleShape).background(Ash2))
                }
                Overline(
                    text = "${startLocal.hour.toString().padStart(2, '0')}:${startLocal.minute.toString().padStart(2, '0')}",
                    size = 12, color = Ink,
                )
                Box(Modifier.size(4.dp).clip(CircleShape).background(Ash2))
                Overline(text = "${session.durationMinutes}MIN", size = 12, color = Ash)
                Box(Modifier.size(4.dp).clip(CircleShape).background(Ash2))
                Overline(text = dateText, size = 12, color = Ash)
                if (instructorLine.isNotEmpty()) {
                    Box(Modifier.size(4.dp).clip(CircleShape).background(Ash2))
                    Overline(text = instructorLine.uppercase(), size = 12, color = Ash)
                }
            }
        }
        if (isCancelled) {
            item("cancelled-notice") {
                Column(modifier = Modifier.padding(24.dp)) {
                    SectionRule(label = "Cancelled")
                    Spacer(Modifier.height(8.dp))
                    BodyText(
                        text = "This class has been cancelled by the studio.",
                        size = 14, color = Warning,
                    )
                }
            }
        } else {
            item("capacity") {
                Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp)) {
                    SectionRule(label = "Availability")
                    Spacer(Modifier.height(8.dp))
                    BodyText(
                        text = if (session.arcanaSpotsAvailable == 0) "FULL"
                            else "${session.arcanaSpotsAvailable} / ${session.arcanaSpotsOffered} spots open",
                        size = 16, color = Ink, weight = FontWeight.Medium,
                    )
                }
            }
        }
        if (session.template.description.isNotBlank()) {
            item("description") {
                Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp)) {
                    SectionRule(label = "About this class")
                    Spacer(Modifier.height(8.dp))
                    BodyText(text = session.template.description, size = 14, color = Ink)
                }
            }
        }
        item("location") {
            Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp)) {
                SectionRule(label = "Location")
                Spacer(Modifier.height(8.dp))
                BodyText(text = session.location.name, size = 16, color = Ink, weight = FontWeight.Medium)
                if (session.location.address.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    BodyText(text = session.location.address, size = 14, color = Ash)
                }
            }
        }
    }
}
