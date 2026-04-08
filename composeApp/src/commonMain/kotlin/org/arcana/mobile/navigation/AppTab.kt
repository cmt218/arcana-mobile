package org.arcana.mobile.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector

enum class AppTab(
    val label: String,
    val icon: ImageVector,
) {
    Home(label = "Home", icon = Icons.Default.Home),
    Classes(label = "Classes", icon = Icons.AutoMirrored.Filled.List),
    Profile(label = "Profile", icon = Icons.Default.Person),
}
