package org.cadence.mobile.classes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import org.cadence.mobile.theme.Background
import org.cadence.mobile.theme.Muted

@Composable
fun ClassesScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().background(Background),
        contentAlignment = Alignment.Center,
    ) {
        Text("Classes coming soon.", style = TextStyle(color = Muted))
    }
}
