package org.arcana.mobile.shell

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.ComposeUIViewController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import org.arcana.mobile.analytics.AppStartTracker
import org.arcana.mobile.analytics.Telemetry
import org.arcana.mobile.booking.MyBookingsScreen
import org.arcana.mobile.concierge.ConciergeRequestScreen
import org.arcana.mobile.currentScreenName
import org.arcana.mobile.home.HomeScreen
import org.arcana.mobile.navigation.ArcanaDestination
import org.arcana.mobile.profile.EditProfileScreen
import org.arcana.mobile.profile.ProfileScreen
import org.arcana.mobile.schedule.ClassDetailScreen
import org.arcana.mobile.schedule.ScheduleScreen
import org.arcana.mobile.studios.StudioSelectionScreen
import org.arcana.mobile.theme.ArcanaTheme
import org.arcana.mobile.theme.Stone
import org.arcana.mobile.ui.LocalFloatingBarInset
import org.koin.compose.koinInject
import platform.UIKit.UIViewController

/*
 * Per-tab Compose roots for the SwiftUI Liquid Glass shell. SwiftUI owns the
 * TabView (native tab bar); each tab hosts its own Compose NavHost carrying
 * the destinations reachable from that tab, so per-tab back stacks live
 * naturally in per-tab controllers (the pre-shell popUpTo/saveState semantics
 * fall out for free — each controller simply stays alive across tab switches).
 *
 * `onRootChanged(isAtRoot)` tells Swift to hide the native tab bar on pushed
 * (non-tab) destinations — mirroring the pre-shell behavior where the Compose
 * ArcanaTabBar hid whenever a non-tab destination was on top.
 *
 * Android is untouched: App.kt's MainScaffold remains the Android composition.
 */

fun HomeTabViewController(onRootChanged: (Boolean) -> Unit): UIViewController =
    shellHostingController {
        TabRoot(ArcanaDestination.Home, onRootChanged, firstContent = true, emitInitialRootScreen = true) { nav ->
            composable<ArcanaDestination.Home> {
                HomeScreen(
                    onSeeAllBookings = { nav.navigate(ArcanaDestination.MyBookings) },
                    onOpenClass = { id -> nav.navigate(ArcanaDestination.ClassDetail(id)) },
                )
            }
            composable<ArcanaDestination.MyBookings> {
                MyBookingsScreen(
                    onClose = { nav.popBackStack() },
                    onOpenClass = { id -> nav.navigate(ArcanaDestination.ClassDetail(id)) },
                )
            }
            composable<ArcanaDestination.ClassDetail> { entry ->
                val args = entry.toRoute<ArcanaDestination.ClassDetail>()
                ClassDetailScreen(sessionId = args.id, onClose = { nav.popBackStack() })
            }
        }
    }

fun ScheduleTabViewController(onRootChanged: (Boolean) -> Unit): UIViewController =
    shellHostingController {
        TabRoot(ArcanaDestination.Schedule, onRootChanged) { nav ->
            composable<ArcanaDestination.Schedule> {
                ScheduleScreen(
                    onOpenClassDetail = { id -> nav.navigate(ArcanaDestination.ClassDetail(id)) },
                    onManageFavorites = { nav.navigate(ArcanaDestination.StudioSelection) },
                )
            }
            composable<ArcanaDestination.ClassDetail> { entry ->
                val args = entry.toRoute<ArcanaDestination.ClassDetail>()
                ClassDetailScreen(sessionId = args.id, onClose = { nav.popBackStack() })
            }
            composable<ArcanaDestination.StudioSelection> {
                StudioSelectionScreen(onClose = { nav.popBackStack() })
            }
        }
    }

fun ProfileTabViewController(onRootChanged: (Boolean) -> Unit): UIViewController =
    shellHostingController {
        TabRoot(ArcanaDestination.Profile, onRootChanged) { nav ->
            composable<ArcanaDestination.Profile> {
                ProfileScreen(
                    onManageStudios = { nav.navigate(ArcanaDestination.StudioSelection) },
                    onOpenConcierge = { nav.navigate(ArcanaDestination.ConciergeRequest) },
                    onOpenSettings = { nav.navigate(ArcanaDestination.EditProfile) },
                )
            }
            composable<ArcanaDestination.StudioSelection> {
                StudioSelectionScreen(onClose = { nav.popBackStack() })
            }
            composable<ArcanaDestination.EditProfile> {
                EditProfileScreen(onClose = { nav.popBackStack() })
            }
            composable<ArcanaDestination.ConciergeRequest> {
                ConciergeRequestScreen(onClose = { nav.popBackStack() })
            }
        }
    }

@Composable
private fun TabRoot(
    start: ArcanaDestination,
    onRootChanged: (Boolean) -> Unit,
    firstContent: Boolean = false,
    // Home emits its initial root $screen from composition (cold start has no
    // tab switch); Schedule/Profile receive theirs from IosShellBridge
    // .tabRootShown on the switch that first shows them — skipping the initial
    // composition emission prevents a double on first visit.
    emitInitialRootScreen: Boolean = false,
    builder: androidx.navigation.NavGraphBuilder.(androidx.navigation.NavHostController) -> Unit,
) {
    ArcanaTheme {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val telemetry = koinInject<Telemetry>()

        // One $screen per destination change — same rule as MainScaffold.
        // The composition's FIRST emission is necessarily this tab's root; it
        // is skipped unless emitInitialRootScreen (the bridge already reported
        // the root on the tab switch that revealed this tab). Pops back to the
        // root after a push still emit normally.
        val initialRootConsumed = remember { mutableStateOf(emitInitialRootScreen) }
        val screenName = currentScreenName(backStackEntry?.destination)
        LaunchedEffect(screenName) {
            if (screenName == null) return@LaunchedEffect
            if (!initialRootConsumed.value) {
                initialRootConsumed.value = true
                return@LaunchedEffect
            }
            telemetry.screen(screenName)
        }

        // Native tab bar hides on pushed destinations (parity with the Compose
        // bar, which only showed on the three tab roots).
        val atRoot = backStackEntry?.destination?.let { dest ->
            currentScreenName(dest) in setOf(
                Telemetry.Screens.HOME, Telemetry.Screens.SCHEDULE, Telemetry.Screens.PROFILE,
            )
        } ?: true
        LaunchedEffect(atRoot) { onRootChanged(atRoot) }

        if (firstContent) {
            LaunchedEffect(Unit) {
                AppStartTracker.onFirstContent(telemetry, authenticated = true)
            }
            // Parity with pre-shell MainScaffold, which resolved the
            // session-scoped ProfileViewModel at composition (for the tab-bar
            // initials): its first /me load fires PostHog identify at session
            // start rather than on the first Profile-tab visit.
            val profileVm = org.koin.compose.viewmodel.koinViewModel<org.arcana.mobile.profile.ProfileViewModel>()
            LaunchedEffect(Unit) { profileVm.load() }
        }

        // Liquid Glass wants content flowing edge-to-edge UNDER the floating
        // tab bar. Content stays full-bleed; the tab-root scrollables add the
        // bar's region as bottom contentPadding via LocalFloatingBarInset so
        // their last items can still scroll clear of the glass.
        val barInset = with(LocalDensity.current) {
            WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).getBottom(this).toDp()
        }
        CompositionLocalProvider(LocalFloatingBarInset provides barInset) {
        Box(Modifier.fillMaxSize().background(Stone)) {
            NavHost(
                navController = navController,
                startDestination = start,
                // Cross-platform-consistent 150ms fades — same values as
                // MainScaffold; tab-sibling slides read wrong on iOS.
                enterTransition = { fadeIn(tween(150)) },
                exitTransition = { fadeOut(tween(150)) },
                popEnterTransition = { fadeIn(tween(150)) },
                popExitTransition = { fadeOut(tween(150)) },
            ) {
                builder(navController)
            }
        }
        }
    }
}

/**
 * Session-scoped ViewModelStore registry for the shell's controllers. Each
 * controller owns an explicit store created OUTSIDE the composition, so:
 * - ViewModel retention across tab switches never depends on CMP scene
 *   internals (the compose scene is disposed on leave-window and recreated on
 *   re-entry; the store persists in the controller).
 * - Logout can deterministically clear() every store (cancelling all
 *   viewModelScopes) — the pre-shell sessionStore.clear() semantic. Swift
 *   calls [IosShellBridge.clearSessionViewModelStores] on every session
 *   boundary before discarding/rebuilding controllers.
 */
internal object ShellSessionStores {
    private val stores = mutableListOf<ViewModelStore>()

    fun newStore(): ViewModelStore = ViewModelStore().also { stores.add(it) }

    fun clearAll() {
        stores.forEach { it.clear() }
        stores.clear()
    }
}

internal fun shellHostingController(content: @Composable () -> Unit): UIViewController {
    val store = ShellSessionStores.newStore()
    val owner = object : ViewModelStoreOwner {
        override val viewModelStore: ViewModelStore = store
    }
    return ComposeUIViewController {
        CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
            content()
        }
    }
}
