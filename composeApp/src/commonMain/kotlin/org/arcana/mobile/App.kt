package org.arcana.mobile

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import kotlinx.coroutines.delay
import org.arcana.mobile.auth.AuthScreen
import org.arcana.mobile.auth.AuthViewModel
import org.arcana.mobile.classes.ClassesScreen
import org.arcana.mobile.home.HomeScreen
import org.arcana.mobile.navigation.AppNavigationBar
import org.arcana.mobile.navigation.AppTab
import org.arcana.mobile.networking.ArcanaApiClient
import org.arcana.mobile.profile.ProfileScreen
import org.arcana.mobile.theme.Background
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun App() {
    val apiClient = koinInject<ArcanaApiClient>()
    val isAuthenticated by apiClient.isAuthenticated.collectAsState()

    var splashVisible by rememberSaveable { mutableStateOf(true) }

    val authVm = koinViewModel<AuthViewModel>()

    val sessionStore = remember { ViewModelStore() }
    val sessionStoreOwner = remember {
        object : ViewModelStoreOwner {
            override val viewModelStore = sessionStore
        }
    }

    LaunchedEffect(isAuthenticated) {
        if (!isAuthenticated) {
            authVm.resetState()
            sessionStore.clear()
        }
    }

    LaunchedEffect(Unit) {
        delay(2800)
        splashVisible = false
    }

    when {
        splashVisible -> SplashScreen()
        isAuthenticated -> CompositionLocalProvider(
            LocalViewModelStoreOwner provides sessionStoreOwner
        ) {
            MainScaffold()
        }
        else -> AuthScreen(viewModel = authVm)
    }
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
