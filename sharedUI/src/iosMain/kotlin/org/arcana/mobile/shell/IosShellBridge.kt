package org.arcana.mobile.shell

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.arcana.mobile.SPLASH_MIN_DISPLAY_MS
import org.arcana.mobile.analytics.Analytics
import org.arcana.mobile.analytics.AppStartTracker
import org.arcana.mobile.analytics.CrashReporter
import org.arcana.mobile.analytics.NoopAnalytics
import org.arcana.mobile.analytics.NoopCrashReporter
import org.arcana.mobile.analytics.Telemetry
import org.arcana.mobile.di.appModule
import org.arcana.mobile.networking.MembershipApi
import org.arcana.mobile.navigation.IosDeepLinkBridge
import org.arcana.mobile.session.AppSessionController
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.koin.mp.KoinPlatform
import org.koin.mp.KoinPlatformTools

/**
 * Kotlin side of the SwiftUI Liquid Glass shell (iOS only). The Swift app
 * (`ArcanaShell.swift`) owns the top-level chrome — TabView (native Liquid
 * Glass tab bar on iOS 26) and the authenticated/unauthenticated swap — and
 * this bridge gives it everything it needs from the Kotlin core:
 *
 * - [start]: Koin bootstrap + telemetry registration (replaces the old
 *   MainViewController-embedded startKoin; MainViewController remains as a
 *   legacy single-VC entry point during the shell transition).
 * - [observeAuthentication]: main-thread callback on every auth flip, driving
 *   the Swift shell's TabView/AuthFlow swap.
 * - The session side-effects App.kt runs on auth flips (token consumption on
 *   login, teardown on logout) run HERE for iOS — see [start]'s watcher —
 *   because App.kt is no longer composed on iOS. Keep in sync with App.kt's
 *   LaunchedEffect(isAuthenticated).
 */
object IosShellBridge {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watcherStarted = false
    private var authObserverRegistered = false
    private var initialsObserverRegistered = false

    fun start(analytics: Analytics?, crashReporter: CrashReporter?) {
        // Mark the process-start reference as early as we control it, for the
        // cold-start → Home measurement (see AppStartTracker).
        AppStartTracker.markStart()
        // Guard against double startKoin (shell recreation, previews).
        if (KoinPlatformTools.defaultContext().getOrNull() == null) {
            val a = analytics ?: NoopAnalytics
            val c = crashReporter ?: NoopCrashReporter
            startKoin {
                modules(
                    appModule,
                    module {
                        single<Analytics> { a }
                        single<CrashReporter> { c }
                    },
                )
            }
        }
        if (!watcherStarted) {
            watcherStarted = true
            val session = session()
            scope.launch {
                var previous = session.isAuthenticated.value
                session.isAuthenticated.collect { authed ->
                    // Mirror of App.kt's LaunchedEffect(isAuthenticated):
                    // login consumes any pending welcome token (and drops the
                    // platform's pending-link reference); logout wipes
                    // session-scoped data. The Swift shell separately discards
                    // its tab view controllers on logout, which clears their
                    // ViewModel stores (the sessionStore.clear() equivalent).
                    if (authed) {
                        if (session.onAuthenticated()) {
                            IosDeepLinkBridge.pendingDeepLink.value = null
                        }
                    } else if (previous) {
                        session.onSessionEnded()
                    }
                    previous = authed
                }
            }
        }
    }

    fun isAuthenticated(): Boolean = session().isAuthenticated.value

    /** Calls back on the main thread on every auth change (and once with the
     *  current value on subscribe, via StateFlow replay). Single-registration:
     *  the shell is an app-lifetime singleton; a second registration (previews,
     *  hypothetical future multi-window) would silently accumulate collectors,
     *  so it is refused. */
    fun observeAuthentication(onChange: (Boolean) -> Unit) {
        if (authObserverRegistered) return
        authObserverRegistered = true
        scope.launch { session().isAuthenticated.collect { onChange(it) } }
    }

    /** Member initials for the native You-tab avatar (the Compose bar's
     *  Moss-circle initials chip, restored on the glass bar). Fetches /me once
     *  per authenticated session (cheap; ProfileViewModel does its own load for
     *  screen content) and calls back on main; null on logout or fetch failure
     *  — Swift falls back to the generic person symbol. Single-registration
     *  like [observeAuthentication]. */
    fun observeMemberInitials(onChange: (String?) -> Unit) {
        if (initialsObserverRegistered) return
        initialsObserverRegistered = true
        scope.launch {
            session().isAuthenticated.collect { authed ->
                if (!authed) {
                    onChange(null)
                } else {
                    val initials = runCatching {
                        KoinPlatform.getKoin().get<MembershipApi>()
                            .membershipMe().member.avatarInitials
                    }.getOrNull()
                    onChange(initials?.takeIf { it.isNotBlank() })
                }
            }
        }
    }

    /** Clears every shell controller's ViewModelStore (cancelling their
     *  viewModelScopes) — call on each session boundary BEFORE discarding and
     *  rebuilding controllers. The pre-shell sessionStore.clear() semantic. */
    fun clearSessionViewModelStores() {
        ShellSessionStores.clearAll()
    }

    /** Splash minimum display, exposed so Swift owns the overlay timing. */
    fun splashMinDisplayMs(): Long = SPLASH_MIN_DISPLAY_MS

    /** Native tab bar tap → same telemetry event the Compose bar fired,
     *  including `fromScreen` (the outgoing tab's canonical screen name —
     *  the bar is only visible on tab roots, so this matches the old
     *  MainScaffold semantics where fromScreen was the current screen). */
    fun tabSelected(tab: String, fromTab: String?) {
        KoinPlatform.getKoin().get<Telemetry>()
            .tabTapped(tab, fromScreen = fromTab?.let { tabScreenName(it) })
    }

    /** $screen for a tab ROOT re-shown by a tab switch. The pre-shell NavHost
     *  emitted $screen on every tab change; under the shell, tab compositions
     *  persist across switches (verified empirically — LaunchedEffects do not
     *  re-run), so switches must emit here. Swift calls this only when the
     *  selection actually changed (same-tab re-taps never re-fired $screen
     *  pre-shell either). First visits are covered too: Schedule/Profile skip
     *  their initial composition-driven root emission (TabRoots.kt), making
     *  this the single source for switch-driven root screens. */
    fun tabRootShown(tab: String) {
        tabScreenName(tab)?.let { KoinPlatform.getKoin().get<Telemetry>().screen(it) }
    }

    private fun tabScreenName(tab: String): String? = when (tab) {
        "home" -> Telemetry.Screens.HOME
        "schedule" -> Telemetry.Screens.SCHEDULE
        "profile" -> Telemetry.Screens.PROFILE
        else -> null
    }

    private fun session(): AppSessionController =
        KoinPlatform.getKoin().get<AppSessionController>()
}
