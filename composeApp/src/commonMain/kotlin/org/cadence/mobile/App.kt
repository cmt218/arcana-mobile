package org.cadence.mobile

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

internal val Background = Color(0xFF0A0A0A)
internal val Gold = Color(0xFFBFA16A)
internal val ShimmerHighlight = Color(0xFFFFF5E0)
internal val Surface = Color(0xFF161616)
internal val Muted = Color(0xFF666666)

@Composable
fun App() {
    var splashVisible by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(2800)
        splashVisible = false
    }

    if (splashVisible) SplashScreen() else MainScreen()
}

@Composable
private fun MainScreen() {
    Column(
        modifier = Modifier.fillMaxSize().background(Background)
    ) {
        TopBar()
        Spacer(Modifier.height(32.dp))
        FeaturedSection()
        Spacer(Modifier.height(32.dp))
        StudioRow()
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
            text = "CADENCE",
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
private fun StudioRow() {
    Column {
        Text(
            text = "YOUR STUDIOS",
            style = TextStyle(color = Muted, fontSize = 11.sp, letterSpacing = 3.sp),
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(Modifier.height(16.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(4) { index ->
                StudioCard(
                    name = listOf("FORM", "RISE", "APEX", "SŌUL")[index],
                    type = listOf("Pilates", "Boxing", "Strength", "Yoga")[index],
                    spotsLeft = listOf(3, 1, 5, 2)[index]
                )
            }
        }
    }
}

@Composable
private fun StudioCard(name: String, type: String, spotsLeft: Int) {
    Column(
        modifier = Modifier
            .size(width = 140.dp, height = 180.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = name,
            style = TextStyle(color = Gold, fontSize = 20.sp, fontWeight = FontWeight.Light, letterSpacing = 4.sp)
        )
        Column {
            Text(text = type, style = TextStyle(color = Color.White, fontSize = 13.sp))
            Spacer(Modifier.height(4.dp))
            Text(
                text = "$spotsLeft spots left",
                style = TextStyle(color = if (spotsLeft <= 2) Color(0xFFE07070) else Muted, fontSize = 11.sp)
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
