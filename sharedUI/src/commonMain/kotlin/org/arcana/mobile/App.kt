package org.arcana.mobile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.backhandler.BackHandler
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.coroutines.delay
import org.arcana.mobile.analytics.Telemetry
import org.arcana.mobile.auth.AuthScreen
import org.arcana.mobile.auth.AuthViewModel
import org.arcana.mobile.auth.PasswordResetRequestScreen
import org.arcana.mobile.auth.PasswordResetRequestViewModel
import org.arcana.mobile.booking.MyBookingsScreen
import org.arcana.mobile.discover.DiscoverScreen
import org.arcana.mobile.discover.StudioPageScreen
import org.arcana.mobile.review.FeedbackScope
import org.arcana.mobile.review.FeedbackFeedScreen
import org.arcana.mobile.concierge.ConciergeRequestScreen
import org.arcana.mobile.home.HomeScreen
import org.arcana.mobile.navigation.ArcanaDestination
import org.arcana.mobile.profile.EditProfileScreen
import org.arcana.mobile.profile.ProfileScreen
import org.arcana.mobile.profile.ProfileUiState
import org.arcana.mobile.profile.ProfileViewModel
import org.arcana.mobile.schedule.ClassDetailScreen
import org.arcana.mobile.schedule.ScheduleScreen
import org.arcana.mobile.search.SearchScreen
import org.arcana.mobile.search.searchHoldEnterTransition
import org.arcana.mobile.session.AppSessionController
import org.arcana.mobile.signup.LeaveSignupDialog
import org.arcana.mobile.signup.SignupCompletionScreen
import org.arcana.mobile.signup.SignupCompletionViewModel
import org.arcana.mobile.signup.SignupStep
import org.arcana.mobile.signup.SignupSurveyScreen
import org.arcana.mobile.signup.SignupSurveyViewModel
import org.arcana.mobile.studios.StudioSelectionScreen
import org.arcana.mobile.theme.ArcanaTheme
import org.arcana.mobile.theme.Dur
import org.arcana.mobile.theme.NavTransitions
import org.arcana.mobile.theme.Stone
import org.arcana.mobile.ui.ArcanaTab
import org.arcana.mobile.ui.ArcanaTabBar
import org.arcana.mobile.ui.LocalFloatingBarInset
import org.arcana.mobile.ui.floatingBarInset
import org.arcana.mobile.discover.DiscoverMapRequests
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

// Session rules (welcome-token machine, survey gate, first-launch recovery,
// teardown) live in :sharedLogic session/AppSessionController.kt — App.kt only
// collects its state and forwards events.

/**
 * @param initialWelcomeToken token parsed from a welcome deep link by the platform
 *   entry point (see iOS `MainViewController`). When non-null and the member is
 *   unauthenticated, [App] routes straight into [SignupCompletionScreen] instead of
 *   [AuthScreen]. Platform-neutral so Android can reuse it.
 * @param onWelcomeTokenConsumed invoked once the token has been consumed (auth
 *   succeeded, or the member chose "log in instead") so the platform can drop its
 *   pending-link reference and not re-deliver it.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun App(
    initialWelcomeToken: String? = null,
    onWelcomeTokenConsumed: () -> Unit = {},
) {
    ArcanaTheme {
        val session = koinInject<AppSessionController>()
        val telemetry = koinInject<Telemetry>()
        val isAuthenticated by session.isAuthenticated.collectAsState()

        var splashVisible by rememberSaveable { mutableStateOf(true) }

        val authVm = koinViewModel<AuthViewModel>()
        var showPasswordReset by rememberSaveable { mutableStateOf(false) }
        var passwordResetInitialEmail by rememberSaveable { mutableStateOf("") }

        val sessionStore = remember { ViewModelStore() }
        val sessionStoreOwner = remember {
            object : ViewModelStoreOwner {
                override val viewModelStore = sessionStore
            }
        }

        // Pending welcome token, owned by AppSessionController. Seeded
        // SYNCHRONOUSLY during composition (not via LaunchedEffect) so an
        // Android deep-link cold start composes the signup branch on frame 1 —
        // matching the pre-extraction `mutableStateOf(initialWelcomeToken)`
        // seeding. An effect would land one recomposition late, briefly compose
        // AuthScreen, fire a spurious $screen:Auth, and latch
        // app_start_completed with authenticated=false. `initialWelcomeToken`
        // can change at runtime on BOTH platforms (Android onNewIntent / iOS
        // bridge StateFlow); the remember key forwards each new value.
        remember(initialWelcomeToken) { session.onDeepLinkToken(initialWelcomeToken) }
        val welcomeToken by session.welcomeToken.collectAsState()

        LaunchedEffect(Unit) {
            // First-launch recovery (no-op on iOS / Install Referrer on Android) —
            // at most ONCE per install; see AppSessionController for the rules.
            session.attemptFirstLaunchRecovery()
        }

        // LaunchedEffect always runs once on first composition, so without this
        // a signed-out cold start would tear down a session that never began —
        // which wipes ReviewerRedirect's marker, the one thing it persists so a
        // process death mid-review stays on staging. IosShellBridge guards the
        // same way.
        var wasAuthenticated by remember { mutableStateOf(isAuthenticated) }
        LaunchedEffect(isAuthenticated) {
            if (isAuthenticated) {
                // completeSignup (or a normal login) flipped auth on — the
                // controller drops any pending token so a later logout can't
                // re-surface the signup screen; only notify the platform when
                // a token was actually pending.
                if (session.onAuthenticated()) onWelcomeTokenConsumed()
            } else if (wasAuthenticated) {
                authVm.resetState()
                sessionStore.clear()
                // Session-scoped singletons that outlive the ViewModelStore
                // (FavoritesRepository) are wiped by the controller.
                session.onSessionEnded()
            }
            wasAuthenticated = isAuthenticated
        }

        LaunchedEffect(Unit) {
            delay(SPLASH_MIN_DISPLAY_MS)
            splashVisible = false
        }

        // Render the destination immediately — even while the splash is still up —
        // so an authenticated member's Home/Profile data fetches DURING the splash
        // window (not after it), and the fade reveals ready content instead of a
        // shimmer. The splash overlay below is drawn on top (z-stacked, opaque) and
        // covers this until it fades out. The splash duration itself is unchanged, so
        // timing can't regress: worst case (slow network) is exactly today's behavior.
        val welcome = welcomeToken
        if (isAuthenticated) {
            LaunchedEffect(Unit) {
                org.arcana.mobile.analytics.AppStartTracker.onFirstContent(telemetry, authenticated = true)
            }
            CompositionLocalProvider(
                LocalViewModelStoreOwner provides sessionStoreOwner
            ) {
                MainScaffold()
            }
        } else if (welcome != null) {
            // Survey-first signup (August cohort+): a NEW member answers the
            // onboarding survey once, then claims their account. Existing July
            // members buying August never receive a fresh signup link, so this
            // flow only reaches genuinely new members.
            var surveyDone by remember(welcome) {
                mutableStateOf(session.isSurveyDone(welcome))
            }

            // Deep-link entry points with no previous screen: without a handler,
            // back finishes the Activity and discards the member's answers.
            // See NAV-13 in docs/regression/inventory.md.
            var confirmLeaveSignup by rememberSaveable { mutableStateOf(false) }
            BackHandler(enabled = !confirmLeaveSignup) { confirmLeaveSignup = true }

            if (!surveyDone) {
                val surveyVm = koinViewModel<SignupSurveyViewModel>(key = "survey-$welcome") {
                    parametersOf(welcome)
                }
                LaunchedEffect(Unit) { telemetry.screen(Telemetry.Screens.SIGNUP_SURVEY) }
                SignupSurveyScreen(
                    viewModel = surveyVm,
                    onDone = {
                        // Persisted: re-tapping the email link later skips the
                        // survey and lands straight on claim-your-name.
                        session.markSurveyDone(welcome)
                        surveyDone = true
                    },
                    onNavigateToLogin = {
                        // "Log in" escape hatch: clear the token, notify, and
                        // fall back to AuthScreen (mirrors the claim screen).
                        session.consumeWelcomeToken()
                        onWelcomeTokenConsumed()
                    },
                )
            } else {
                // key = welcome recreates the VM if a different token arrives.
                val signupVm = koinViewModel<SignupCompletionViewModel>(key = welcome) {
                    parametersOf(welcome)
                }
                LaunchedEffect(Unit) { telemetry.screen(Telemetry.Screens.SIGNUP) }
                SignupCompletionScreen(
                    viewModel = signupVm,
                    onNavigateToLogin = {
                        // "Log in instead" (expired/consumed link): clear the token,
                        // notify, and fall back to AuthScreen.
                        session.consumeWelcomeToken()
                        onWelcomeTokenConsumed()
                    },
                    // lockedEmail stays null until the server token-preview endpoint lands.
                )
            }
            if (confirmLeaveSignup) {
                LeaveSignupDialog(
                    step = if (surveyDone) SignupStep.Claim else SignupStep.Survey,
                    onStay = { confirmLeaveSignup = false },
                    onLeave = {
                        // Same local exit as "Log in instead"; no server call.
                        confirmLeaveSignup = false
                        session.consumeWelcomeToken()
                        onWelcomeTokenConsumed()
                    },
                )
            }
        } else {
            if (showPasswordReset) {
                val resetVm = koinViewModel<PasswordResetRequestViewModel>(
                    key = "password-reset-$passwordResetInitialEmail",
                ) {
                    parametersOf(passwordResetInitialEmail)
                }
                LaunchedEffect(Unit) { telemetry.screen(Telemetry.Screens.PASSWORD_RESET_REQUEST) }
                // No confirm, unlike signup: nothing but an email is lost.
                BackHandler { showPasswordReset = false }
                PasswordResetRequestScreen(
                    viewModel = resetVm,
                    onBackToLogin = { showPasswordReset = false },
                )
            } else {
                LaunchedEffect(Unit) { telemetry.screen(Telemetry.Screens.AUTH) }
                LaunchedEffect(Unit) {
                    org.arcana.mobile.analytics.AppStartTracker.onFirstContent(telemetry, authenticated = false)
                }
                AuthScreen(
                    viewModel = authVm,
                    onForgotPassword = { email ->
                        passwordResetInitialEmail = email
                        showPasswordReset = true
                    },
                )
            }
        }
        AnimatedVisibility(
            visible = splashVisible,
            enter = fadeIn(tween(0)),
            exit = fadeOut(tween(SPLASH_EXIT_MS, easing = SplashExitEasing)),
        ) {
            SplashScreen()
        }
    }
}

/** Ease in and out, the same curve as iOS's `.easeInOut`. */
private val SplashExitEasing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

@Composable
private fun MainScaffold() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val telemetry = koinInject<Telemetry>()

    // One $screen per destination change. Keyed on the resolved name so a config
    // change / recomposition doesn't double-fire, and ClassDetail (which carries
    // an id arg) still reports a single stable screen name.
    val screenName = currentScreenName(currentDestination)
    LaunchedEffect(screenName) {
        if (screenName != null) telemetry.screen(screenName)
    }

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
        currentDestination?.hasRoute<ArcanaDestination.Discover>() == true -> ArcanaTab.Discover
        currentDestination?.hasRoute<ArcanaDestination.Profile>() == true -> ArcanaTab.Profile
        else -> null
    }

    // "Show on map" from a class or studio page. The Discover tab is RESTORED and
    // then popped to its root: a Discover visited earlier keeps its ViewModel alive
    // in the saved stack, and that one takes the request, so it must be the one shown.
    val mapRequests = koinInject<DiscoverMapRequests>()
    val showOnMap: (Int) -> Unit = { locationId ->
        mapRequests.request(locationId)
        navController.navigateToTab(ArcanaTab.Discover)
        navController.popBackStack(ArcanaDestination.Discover, inclusive = false)
    }

    CompositionLocalProvider(LocalFloatingBarInset provides floatingBarInset) {
    Box(Modifier.fillMaxSize().background(Stone)) {
        NavHost(
            navController = navController,
            startDestination = ArcanaDestination.Home,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { with(NavTransitions) { enter() } },
            exitTransition = { with(NavTransitions) { exit() } },
            popEnterTransition = { with(NavTransitions) { popEnter() } },
            popExitTransition = { with(NavTransitions) { popExit() } },
        ) {
            composable<ArcanaDestination.Home> {
                HomeScreen(
                    onSeeAllBookings = { navController.navigate(ArcanaDestination.MyBookings(source = "home")) },
                    onOpenClass = { id -> navController.navigate(ArcanaDestination.ClassDetail(id)) },
                    onBookClass = { navController.navigateToTab(ArcanaTab.Schedule) },
                )
            }
            composable<ArcanaDestination.Schedule>(
                // Stay fully visible beneath the Search overlay's animation —
                // fading here would break the lightweight-overlay read.
                exitTransition = {
                    if (targetState.destination.hasRoute<ArcanaDestination.Search>()) {
                        ExitTransition.None
                    } else null
                },
                popEnterTransition = {
                    if (initialState.destination.hasRoute<ArcanaDestination.Search>()) {
                        EnterTransition.None
                    } else null
                },
            ) {
                ScheduleScreen(
                    onOpenClassDetail = { id ->
                        navController.navigate(ArcanaDestination.ClassDetail(id))
                    },
                    onManageFavorites = { navController.navigate(ArcanaDestination.StudioSelection) },
                    onOpenSearch = { bounds ->
                        navController.navigate(
                            ArcanaDestination.Search(
                                originLeft = bounds?.left ?: -1f,
                                originTop = bounds?.top ?: -1f,
                                originRight = bounds?.right ?: -1f,
                                originBottom = bounds?.bottom ?: -1f,
                            )
                        )
                    },
                )
            }
            composable<ArcanaDestination.Search>(
                // Near-invisible fade whose only job is to hold both screens
                // mounted while SearchScreen's own reveal runs.
                enterTransition = { searchHoldEnterTransition() },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None },
            ) { entry ->
                val args = entry.toRoute<ArcanaDestination.Search>()
                SearchScreen(
                    originInRoot = args.takeIf { it.originLeft >= 0f }?.let {
                        Rect(it.originLeft, it.originTop, it.originRight, it.originBottom)
                    },
                    onOpenClassDetail = { id ->
                        navController.navigate(ArcanaDestination.ClassDetail(id))
                    },
                    onClose = { navController.popBackStack() },
                )
            }
            composable<ArcanaDestination.Discover> {
                DiscoverScreen(
                    onOpenStudio = { slug, source, locationId ->
                        navController.navigate(ArcanaDestination.StudioPage(slug, source, locationId ?: 0))
                    },
                )
            }
            composable<ArcanaDestination.StudioPage> { entry ->
                val args = entry.toRoute<ArcanaDestination.StudioPage>()
                StudioPageScreen(
                    brandSlug = args.brandSlug,
                    source = args.source,
                    fromLocationId = args.locationId.takeIf { it > 0 },
                    onClose = { navController.popBackStack() },
                    onSeeSchedule = { navController.navigateToTab(ArcanaTab.Schedule) },
                    onOpenFeedback = { scope, source -> navController.navigate(feedbackRoute(scope, source)) },
                    onShowOnMap = showOnMap,
                )
            }
            composable<ArcanaDestination.FeedbackFeed> { entry ->
                val args = entry.toRoute<ArcanaDestination.FeedbackFeed>()
                FeedbackFeedScreen(
                    scopeType = args.scopeType,
                    scopeValue = args.scopeValue,
                    label = args.label,
                    source = args.source,
                    onClose = { navController.popBackStack() },
                    onOpenStudio = { slug -> navController.navigate(ArcanaDestination.StudioPage(slug, source = "feed")) },
                    onOpenFeed = { scope -> navController.navigate(feedbackRoute(scope, source = "feed")) },
                )
            }
            composable<ArcanaDestination.Profile> {
                ProfileScreen(
                    onManageStudios = { navController.navigate(ArcanaDestination.StudioSelection) },
                    onOpenReservations = { navController.navigate(ArcanaDestination.MyBookings(source = "you")) },
                    onOpenConcierge = { navController.navigate(ArcanaDestination.ConciergeRequest) },
                    onOpenSettings = { navController.navigate(ArcanaDestination.EditProfile) },
                )
            }
            composable<ArcanaDestination.StudioSelection> {
                StudioSelectionScreen(onClose = { navController.popBackStack() })
            }
            composable<ArcanaDestination.EditProfile> {
                EditProfileScreen(onClose = { navController.popBackStack() })
            }
            composable<ArcanaDestination.MyBookings> { entry ->
                val args = entry.toRoute<ArcanaDestination.MyBookings>()
                MyBookingsScreen(
                    source = args.source,
                    onClose = { navController.popBackStack() },
                    onOpenClass = { id -> navController.navigate(ArcanaDestination.ClassDetail(id)) },
                    onBookClass = { navController.navigateToTab(ArcanaTab.Schedule) },
                )
            }
            composable<ArcanaDestination.ConciergeRequest> {
                ConciergeRequestScreen(onClose = { navController.popBackStack() })
            }
            composable<ArcanaDestination.ClassDetail> { backStackEntry ->
                val args = backStackEntry.toRoute<ArcanaDestination.ClassDetail>()
                ClassDetailScreen(
                    sessionId = args.id,
                    onClose = { navController.popBackStack() },
                    onOpenFeedback = { scope, source -> navController.navigate(feedbackRoute(scope, source)) },
                    onShowOnMap = showOnMap,
                )
            }
        }
        // selectedTab flips before the transition plays, so hold the last
        // non-null tab through the fade-out. Keep this write above every read of
        // lastTab, or each nav costs a full extra MainScaffold recomposition.
        var lastTab by remember { mutableStateOf(selectedTab) }
        if (selectedTab != null) lastTab = selectedTab
        AnimatedVisibility(
            visible = selectedTab != null,
            enter = fadeIn(tween(Dur.Quick)),
            exit = fadeOut(tween(Dur.Quick)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            lastTab?.let { tab ->
                ArcanaTabBar(
                    active = tab,
                    onSelect = { selected ->
                        // graphicsLayer alpha doesn't stop hit-testing, so the pill stays
                        // tappable through its own fade-out; ignore taps once it's fading.
                        if (selectedTab == null) return@ArcanaTabBar
                        telemetry.tabTapped(selected.name.lowercase(), fromScreen = screenName)
                        navController.navigateToTab(selected)
                    },
                    avatarInitials = avatarInitials,
                )
            }
        }
    }
    }
}

/** Canonical $screen name for the current destination (see Telemetry.Screens).
 *  Internal: reused by the iOS shell's per-tab roots (shell/TabRoots.kt). */
internal fun currentScreenName(dest: NavDestination?): String? = when {
    dest == null -> null
    dest.hasRoute<ArcanaDestination.Home>() -> Telemetry.Screens.HOME
    dest.hasRoute<ArcanaDestination.Schedule>() -> Telemetry.Screens.SCHEDULE
    dest.hasRoute<ArcanaDestination.Profile>() -> Telemetry.Screens.PROFILE
    dest.hasRoute<ArcanaDestination.StudioSelection>() -> Telemetry.Screens.STUDIO_SELECTION
    dest.hasRoute<ArcanaDestination.MyBookings>() -> Telemetry.Screens.MY_BOOKINGS
    dest.hasRoute<ArcanaDestination.Discover>() -> Telemetry.Screens.DISCOVER
    dest.hasRoute<ArcanaDestination.StudioPage>() -> Telemetry.Screens.STUDIO_PAGE
    dest.hasRoute<ArcanaDestination.FeedbackFeed>() -> Telemetry.Screens.FEEDBACK_FEED
    dest.hasRoute<ArcanaDestination.ConciergeRequest>() -> Telemetry.Screens.CONCIERGE_REQUEST
    dest.hasRoute<ArcanaDestination.EditProfile>() -> Telemetry.Screens.EDIT_PROFILE
    dest.hasRoute<ArcanaDestination.ClassDetail>() -> Telemetry.Screens.CLASS_DETAIL
    dest.hasRoute<ArcanaDestination.Search>() -> Telemetry.Screens.SEARCH
    else -> null
}

private fun NavController.navigateToTab(tab: ArcanaTab) {
    val dest: ArcanaDestination = when (tab) {
        ArcanaTab.Home -> ArcanaDestination.Home
        ArcanaTab.Schedule -> ArcanaDestination.Schedule
        ArcanaTab.Discover -> ArcanaDestination.Discover
        ArcanaTab.Profile -> ArcanaDestination.Profile
    }
    navigate(dest) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** The feed route for a scope; shared by every entry point on both shells. */
internal fun feedbackRoute(scope: FeedbackScope, source: String) =
    ArcanaDestination.FeedbackFeed(scope.type, scope.value, scope.label, source)
