package org.arcana.mobile.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.arcana.mobile.classes.ClassesUiState
import org.arcana.mobile.classes.ClassesViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.arcana.mobile.data.ClassDto
import org.arcana.mobile.theme.Background
import org.arcana.mobile.theme.Gold
import org.arcana.mobile.theme.Muted
import org.arcana.mobile.theme.Surface

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: ClassesViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier.fillMaxSize().background(Background)
    ) {
        TopBar()
        Spacer(Modifier.height(32.dp))
        FeaturedSection()
        Spacer(Modifier.height(32.dp))
        ClassRow(uiState)
        Spacer(Modifier.height(32.dp))
        UpcomingSection()
    }
}

@Composable
private fun TopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 52.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "arcana",
            style = TextStyle(
                color = Gold,
                fontSize = 18.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 8.sp,
            )
        )
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(50))
                .background(Surface),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "JD", style = TextStyle(color = Gold, fontSize = 12.sp))
        }
    }
}

@Composable
private fun FeaturedSection() {
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Text(
            text = "Good morning.",
            style = TextStyle(color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Light)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "3 studios available near you today.",
            style = TextStyle(color = Muted, fontSize = 14.sp, letterSpacing = 0.5.sp)
        )
    }
}

@Composable
private fun ClassRow(uiState: ClassesUiState) {
    Column {
        Text(
            text = "CLASSES TODAY",
            style = TextStyle(color = Muted, fontSize = 11.sp, letterSpacing = 3.sp),
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(Modifier.height(16.dp))
        when (uiState) {
            is ClassesUiState.Loading -> {
                Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Gold, modifier = Modifier.size(24.dp))
                }
            }
            is ClassesUiState.Error -> {
                Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                    Text(text = uiState.message, style = TextStyle(color = Muted, fontSize = 13.sp))
                }
            }
            is ClassesUiState.Success -> {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.classes) { cls ->
                        ClassCard(cls)
                    }
                }
            }
        }
    }
}

@Composable
private fun ClassCard(cls: ClassDto) {
    val timeLabel = cls.startTime.take(16).takeLast(5) // "HH:mm" from "YYYY-MM-DDTHH:mm"
    Column(
        modifier = Modifier
            .size(width = 160.dp, height = 180.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = cls.studio.uppercase(),
                style = TextStyle(color = Gold, fontSize = 16.sp, fontWeight = FontWeight.Light, letterSpacing = 3.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(text = timeLabel, style = TextStyle(color = Muted, fontSize = 11.sp))
        }
        Column {
            Text(text = cls.name, style = TextStyle(color = Color.White, fontSize = 13.sp))
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${cls.availableSpots} spots left",
                style = TextStyle(color = if (cls.availableSpots <= 2) Color(0xFFE07070) else Muted, fontSize = 11.sp)
            )
        }
    }
}

@Composable
private fun UpcomingSection() {
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Text(
            text = "UPCOMING",
            style = TextStyle(color = Muted, fontSize = 11.sp, letterSpacing = 3.sp)
        )
        Spacer(Modifier.height(16.dp))
        listOf(
            Triple("7:00 AM", "Reformer Flow", "FORM"),
            Triple("12:30 PM", "Power Boxing", "RISE"),
        ).forEach { (time, className, studio) ->
            BookingRow(time, className, studio)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun BookingRow(time: String, className: String, studio: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Surface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = className, style = TextStyle(color = Color.White, fontSize = 14.sp))
            Spacer(Modifier.height(2.dp))
            Text(text = studio, style = TextStyle(color = Gold, fontSize = 11.sp, letterSpacing = 2.sp))
        }
        Text(text = time, style = TextStyle(color = Muted, fontSize = 13.sp))
    }
}
