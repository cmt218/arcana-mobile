package org.arcana.mobile.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import org.arcana.mobile.networking.ArcanaApiClient
import org.arcana.mobile.theme.Background
import org.arcana.mobile.theme.Muted
import org.koin.compose.koinInject

@Composable
fun ProfileScreen(modifier: Modifier = Modifier) {
    val apiClient = koinInject<ArcanaApiClient>()

    Column(
        modifier = modifier.fillMaxSize().background(Background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Profile coming soon.", style = TextStyle(color = Muted))
        TextButton(onClick = { apiClient.logout() }) {
            Text("Logout", style = TextStyle(color = Muted))
        }
    }
}
