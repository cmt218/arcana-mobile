package org.arcana.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.arcana.mobile.theme.BrandOrange
import org.arcana.mobile.theme.Lime

@Composable
fun SplashScreen() {
    val backgroundBrush = Brush.linearGradient(
        colors = listOf(BrandOrange, Lime),
        start = Offset(0f, 0f),
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )

    Box(
        modifier = Modifier.fillMaxSize().background(backgroundBrush),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "arcana",
            style = TextStyle(
                color = Color.White,
                fontSize = 52.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 12.sp,
            )
        )
    }
}
