package org.cadence.mobile

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import org.cadence.mobile.classes.ClassesScreen
import org.cadence.mobile.home.HomeScreen
import org.cadence.mobile.navigation.AppNavigationBar
import org.cadence.mobile.navigation.AppTab
import org.cadence.mobile.profile.ProfileScreen
import org.cadence.mobile.theme.Background

@Composable
fun App() {
    var splashVisible by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(2800)
        splashVisible = false
    }

    if (splashVisible) SplashScreen() else MainScaffold()
}

@Composable
private fun MainScaffold() {
    var selectedTab by remember { mutableStateOf(AppTab.Home) }

    Scaffold(
        containerColor = Background,
        bottomBar = {
            AppNavigationBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
            )
        }
    ) { innerPadding ->
        when (selectedTab) {
            AppTab.Home    -> HomeScreen(modifier = Modifier.padding(innerPadding))
            AppTab.Classes -> ClassesScreen(modifier = Modifier.padding(innerPadding))
            AppTab.Profile -> ProfileScreen(modifier = Modifier.padding(innerPadding))
        }
    }
}
