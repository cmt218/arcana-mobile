package org.arcana.mobile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import org.arcana.mobile.auth.AuthScreen
import org.arcana.mobile.auth.AuthViewModel
import org.arcana.mobile.home.HomeScreen
import org.arcana.mobile.navigation.ArcanaDestination
import org.arcana.mobile.networking.ArcanaApiClient
import org.arcana.mobile.profile.ProfileScreen
import org.arcana.mobile.schedule.ScheduleScreen
import org.arcana.mobile.studios.StudioSelectionScreen
import org.arcana.mobile.theme.ArcanaTheme
import org.arcana.mobile.theme.Stone
import org.arcana.mobile.ui.ArcanaTab
import org.arcana.mobile.ui.ArcanaTabBar
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun App() {
    ArcanaTheme {
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
            delay(SPLASH_MIN_DISPLAY_MS)
            splashVisible = false
        }

        // Render the post-splash destination underneath so the splash's 300 ms
        // fade-out reveals the next screen rather than a blank surface.
        if (!splashVisible) {
            if (isAuthenticated) {
                CompositionLocalProvider(
                    LocalViewModelStoreOwner provides sessionStoreOwner
                ) {
                    MainScaffold()
                }
            } else {
                AuthScreen(viewModel = authVm)
            }
        }
        AnimatedVisibility(
            visible = splashVisible,
            enter = fadeIn(tween(0)),
            exit = fadeOut(tween(300)),
        ) {
            SplashScreen()
        }
    }
}

@Composable
private fun MainScaffold() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val selectedTab: ArcanaTab? = when {
        currentDestination?.hasRoute<ArcanaDestination.Home>() == true -> ArcanaTab.Home
        currentDestination?.hasRoute<ArcanaDestination.Schedule>() == true -> ArcanaTab.Schedule
        currentDestination?.hasRoute<ArcanaDestination.Profile>() == true -> ArcanaTab.Profile
        else -> null
    }

    Scaffold(
        containerColor = Stone,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (selectedTab != null) {
                ArcanaTabBar(
                    active = selectedTab,
                    onSelect = { tab -> navController.navigateToTab(tab) },
                    avatarInitials = "FD",
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ArcanaDestination.Home,
            modifier = Modifier.padding(innerPadding),
            // Cross-platform-consistent fade. Tabs are siblings (no left/right
            // semantics), and iOS's default slide reads wrong when navigating
            // "backwards" between tabs. Override per-composable if a particular
            // destination needs a different transition (e.g. a modal flow).
            enterTransition = { fadeIn(tween(150)) },
            exitTransition = { fadeOut(tween(150)) },
            popEnterTransition = { fadeIn(tween(150)) },
            popExitTransition = { fadeOut(tween(150)) },
        ) {
            composable<ArcanaDestination.Home> { HomeScreen() }
            composable<ArcanaDestination.Schedule> { ScheduleScreen() }
            composable<ArcanaDestination.Profile> {
                ProfileScreen(
                    onManageStudios = { navController.navigate(ArcanaDestination.StudioSelection) },
                )
            }
            composable<ArcanaDestination.StudioSelection> {
                StudioSelectionScreen(onClose = { navController.popBackStack() })
            }
        }
    }
}

private fun NavController.navigateToTab(tab: ArcanaTab) {
    val dest: ArcanaDestination = when (tab) {
        ArcanaTab.Home -> ArcanaDestination.Home
        ArcanaTab.Schedule -> ArcanaDestination.Schedule
        ArcanaTab.Profile -> ArcanaDestination.Profile
    }
    navigate(dest) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
