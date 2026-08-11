package org.arcana.mobile.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import org.arcana.mobile.analytics.AppStartTracker
import org.arcana.mobile.analytics.Telemetry
import org.arcana.mobile.auth.AuthScreen
import org.arcana.mobile.auth.AuthViewModel
import org.arcana.mobile.auth.PasswordResetRequestScreen
import org.arcana.mobile.auth.PasswordResetRequestViewModel
import org.arcana.mobile.navigation.DeepLinkHandler
import org.arcana.mobile.navigation.IosDeepLinkBridge
import org.arcana.mobile.session.AppSessionController
import org.arcana.mobile.signup.SignupCompletionScreen
import org.arcana.mobile.signup.SignupCompletionViewModel
import org.arcana.mobile.signup.SignupSurveyScreen
import org.arcana.mobile.signup.SignupSurveyViewModel
import org.arcana.mobile.theme.ArcanaTheme
import org.arcana.mobile.theme.Stone
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import platform.UIKit.UIViewController

/**
 * The signed-out flow for the SwiftUI shell: auth, password reset, and the
 * welcome-deep-link signup pipeline (survey → claim-your-name). This is the
 * iOS mirror of App.kt's unauthenticated branches — App.kt remains the
 * Android composition and is untouched by the shell; KEEP THE TWO IN SYNC
 * when signup/auth behavior changes.
 *
 * Session side-effects on auth flips (token consumption, teardown) live in
 * [IosShellBridge]'s watcher, not here — the Swift shell swaps this
 * controller away the moment authentication succeeds.
 */
fun AuthFlowViewController(): UIViewController = shellHostingController {
    val pending by IosDeepLinkBridge.pendingDeepLink.collectAsState()
    val token = pending?.let { DeepLinkHandler.extractWelcomeToken(it) }
    AuthFlowRoot(
        initialWelcomeToken = token,
        onWelcomeTokenConsumed = { IosDeepLinkBridge.pendingDeepLink.value = null },
    )
}

@Composable
internal fun AuthFlowRoot(
    initialWelcomeToken: String?,
    onWelcomeTokenConsumed: () -> Unit,
) {
    ArcanaTheme {
        val session = koinInject<AppSessionController>()
        val telemetry = koinInject<Telemetry>()

        // Seed SYNCHRONOUSLY during composition (same frame-1 rule as App.kt:
        // an effect would land one recomposition late and fire spurious Auth
        // telemetry on a deep-link cold start).
        remember(initialWelcomeToken) { session.onDeepLinkToken(initialWelcomeToken) }
        val welcomeToken by session.welcomeToken.collectAsState()

        LaunchedEffect(Unit) {
            // First-launch recovery (no-op on iOS by design) — at most once
            // per install; see AppSessionController for the rules.
            session.attemptFirstLaunchRecovery()
        }

        // Post-signup/-login race guard: the Kotlin watcher consumes the
        // welcome token the instant auth flips, but Swift swaps this
        // controller away one runloop later. Without this gate the fallback
        // branch would compose AuthScreen for a frame and fire a spurious
        // $screen:Auth. Render inert Stone until the swap instead.
        val isAuthenticated by session.isAuthenticated.collectAsState()
        if (isAuthenticated) {
            Box(Modifier.fillMaxSize().background(Stone))
            return@ArcanaTheme
        }

        val authVm = koinViewModel<AuthViewModel>()
        var showPasswordReset by rememberSaveable { mutableStateOf(false) }
        var passwordResetInitialEmail by rememberSaveable { mutableStateOf("") }

        val welcome = welcomeToken
        if (welcome != null) {
            // Survey-first signup: a NEW member answers the onboarding survey
            // once, then claims their account.
            var surveyDone by remember(welcome) {
                mutableStateOf(session.isSurveyDone(welcome))
            }
            if (!surveyDone) {
                val surveyVm = koinViewModel<SignupSurveyViewModel>(key = "survey-$welcome") {
                    parametersOf(welcome)
                }
                LaunchedEffect(Unit) { telemetry.screen(Telemetry.Screens.SIGNUP_SURVEY) }
                SignupSurveyScreen(
                    viewModel = surveyVm,
                    onDone = {
                        session.markSurveyDone(welcome)
                        surveyDone = true
                    },
                    onNavigateToLogin = {
                        session.consumeWelcomeToken()
                        onWelcomeTokenConsumed()
                    },
                )
            } else {
                val signupVm = koinViewModel<SignupCompletionViewModel>(key = welcome) {
                    parametersOf(welcome)
                }
                LaunchedEffect(Unit) { telemetry.screen(Telemetry.Screens.SIGNUP) }
                SignupCompletionScreen(
                    viewModel = signupVm,
                    onNavigateToLogin = {
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
                PasswordResetRequestScreen(
                    viewModel = resetVm,
                    onBackToLogin = { showPasswordReset = false },
                )
            } else {
                LaunchedEffect(Unit) { telemetry.screen(Telemetry.Screens.AUTH) }
                LaunchedEffect(Unit) {
                    AppStartTracker.onFirstContent(telemetry, authenticated = false)
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
    }
}
