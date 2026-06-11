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
import androidx.navigation.toRoute
import kotlinx.coroutines.delay
import org.arcana.mobile.auth.AuthScreen
import org.arcana.mobile.auth.AuthViewModel
import org.arcana.mobile.auth.SecureStorage
import org.arcana.mobile.favorites.FavoritesRepository
import org.arcana.mobile.home.HomeScreen
import org.arcana.mobile.navigation.ArcanaDestination
import org.arcana.mobile.networking.ArcanaApiClient
import org.arcana.mobile.profile.ProfileScreen
import org.arcana.mobile.profile.ProfileUiState
import org.arcana.mobile.profile.ProfileViewModel
import org.arcana.mobile.schedule.ClassDetailScreen
import org.arcana.mobile.schedule.ScheduleScreen
import org.arcana.mobile.signup.PendingTokenSource
import org.arcana.mobile.booking.MyBookingsScreen
import org.arcana.mobile.signup.SignupCompletionScreen
import org.arcana.mobile.signup.SignupCompletionViewModel
import org.arcana.mobile.studios.StudioSelectionScreen
import org.arcana.mobile.theme.ArcanaTheme
import org.arcana.mobile.theme.Stone
import org.arcana.mobile.ui.ArcanaTab
import org.arcana.mobile.ui.ArcanaTabBar
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private const val RECOVERY_ATTEMPTED_KEY = "first_launch_recovery_attempted"

/**
 * @param initialWelcomeToken token parsed from a welcome deep link by the platform
 *   entry point (see iOS `MainViewController`). When non-null and the member is
 *   unauthenticated, [App] routes straight into [SignupCompletionScreen] instead of
 *   [AuthScreen]. Platform-neutral so Android can reuse it.
 * @param onWelcomeTokenConsumed invoked once the token has been consumed (auth
 *   succeeded, or the member chose "log in instead") so the platform can drop its
 *   pending-link reference and not re-deliver it.
 */
@Composable
fun App(
    initialWelcomeToken: String? = null,
    onWelcomeTokenConsumed: () -> Unit = {},
) {
    ArcanaTheme {
        val apiClient = koinInject<ArcanaApiClient>()
        val favoritesRepository = koinInject<FavoritesRepository>()
        val isAuthenticated by apiClient.isAuthenticated.collectAsState()

        var splashVisible by rememberSaveable { mutableStateOf(true) }

        val authVm = koinViewModel<AuthViewModel>()

        val sessionStore = remember { ViewModelStore() }
        val sessionStoreOwner = remember {
            object : ViewModelStoreOwner {
                override val viewModelStore = sessionStore
            }
        }

        // Pending welcome token. Seeded from the cold-start deep link.
        // `initialWelcomeToken` can change at runtime on BOTH platforms, and this
        // effect propagates the new value into welcomeToken. On Android,
        // MainActivity.onNewIntent re-supplies it for a warm-start deep link (wired
        // in C.7), recomposing App. On iOS, the IosDeepLinkBridge StateFlow that
        // MainViewController collects pushes the new link in (cold AND warm), again
        // recomposing App with a fresh token.
        var welcomeToken by remember { mutableStateOf(initialWelcomeToken) }
        LaunchedEffect(initialWelcomeToken) {
            if (initialWelcomeToken != null) welcomeToken = initialWelcomeToken
        }

        val secureStorage = koinInject<SecureStorage>()
        LaunchedEffect(Unit) {
            // First-launch recovery (clipboard on iOS / Install Referrer on Android) — runs
            // at most ONCE per install, and only when signed-out with no deep link. The short
            // delay lets a cold-start deep link arrive first (the bridge sets welcomeToken),
            // so a deep-link launch never triggers iOS's pasteboard permission prompt.
            if (isAuthenticated) return@LaunchedEffect
            if (secureStorage.load(RECOVERY_ATTEMPTED_KEY) == "1") return@LaunchedEffect
            delay(700)
            if (initialWelcomeToken == null && welcomeToken == null && !isAuthenticated) {
                val recovered = PendingTokenSource().consumePendingToken()
                if (recovered != null) welcomeToken = recovered
            }
            secureStorage.save(RECOVERY_ATTEMPTED_KEY, "1")
        }

        LaunchedEffect(isAuthenticated) {
            if (isAuthenticated) {
                // completeSignup (or a normal login) flipped auth on — drop the
                // token so a later logout can't re-surface the signup screen.
                // Only notify when a token was actually pending; a normal
                // logged-in launch never had one and must not fire the callback.
                if (welcomeToken != null) {
                    welcomeToken = null
                    onWelcomeTokenConsumed()
                }
            } else {
                authVm.resetState()
                sessionStore.clear()
                // FavoritesRepository is a Koin single that outlives the
                // session ViewModelStore — wipe it so the next member's
                // session doesn't flash the previous member's favorites.
                favoritesRepository.clear()
            }
        }

        LaunchedEffect(Unit) {
            delay(SPLASH_MIN_DISPLAY_MS)
            splashVisible = false
        }

        // Render the post-splash destination underneath so the splash's 300 ms
        // fade-out reveals the next screen rather than a blank surface.
        if (!splashVisible) {
            val welcome = welcomeToken
            if (isAuthenticated) {
                CompositionLocalProvider(
                    LocalViewModelStoreOwner provides sessionStoreOwner
                ) {
                    MainScaffold()
                }
            } else if (welcome != null) {
                // key = welcome recreates the VM if a different token arrives.
                val signupVm = koinViewModel<SignupCompletionViewModel>(key = welcome) {
                    parametersOf(welcome)
                }
                SignupCompletionScreen(
                    viewModel = signupVm,
                    onNavigateToLogin = {
                        // "Log in instead" (expired/consumed link): clear the token,
                        // notify, and fall back to AuthScreen.
                        welcomeToken = null
                        onWelcomeTokenConsumed()
                    },
                    // lockedEmail stays null until the server token-preview endpoint lands.
                )
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

    // Real member initials for the Profile-tab avatar (was hardcoded "FD").
    // Shares the session-scoped ProfileViewModel, so this is the same /me the
    // Profile screen reads — no extra fetch beyond the first load.
    val profileVm = koinViewModel<ProfileViewModel>()
    LaunchedEffect(Unit) { profileVm.load() }
    val profileState by profileVm.uiState.collectAsState()
    val avatarInitials = (profileState as? ProfileUiState.Success)?.initials ?: ""

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
                    avatarInitials = avatarInitials,
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
            composable<ArcanaDestination.Home> {
                HomeScreen(
                    onSeeAllBookings = { navController.navigate(ArcanaDestination.MyBookings) },
                    onOpenClass = { id -> navController.navigate(ArcanaDestination.ClassDetail(id)) },
                )
            }
            composable<ArcanaDestination.Schedule> {
                ScheduleScreen(
                    onOpenClassDetail = { id ->
                        navController.navigate(ArcanaDestination.ClassDetail(id))
                    },
                    onManageFavorites = { navController.navigate(ArcanaDestination.StudioSelection) },
                )
            }
            composable<ArcanaDestination.Profile> {
                ProfileScreen(
                    onManageStudios = { navController.navigate(ArcanaDestination.StudioSelection) },
                )
            }
            composable<ArcanaDestination.StudioSelection> {
                StudioSelectionScreen(onClose = { navController.popBackStack() })
            }
            composable<ArcanaDestination.MyBookings> {
                MyBookingsScreen(
                    onClose = { navController.popBackStack() },
                    onOpenClass = { id -> navController.navigate(ArcanaDestination.ClassDetail(id)) },
                )
            }
            composable<ArcanaDestination.ClassDetail> { backStackEntry ->
                val args = backStackEntry.toRoute<ArcanaDestination.ClassDetail>()
                ClassDetailScreen(
                    sessionId = args.id,
                    onClose = { navController.popBackStack() },
                )
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
