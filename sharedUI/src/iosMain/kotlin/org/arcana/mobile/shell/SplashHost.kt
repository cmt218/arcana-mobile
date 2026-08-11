package org.arcana.mobile.shell

import androidx.compose.ui.window.ComposeUIViewController
import org.arcana.mobile.SplashScreen
import platform.UIKit.UIViewController

/** The dot-matrix splash as its own controller: the Swift shell overlays it
 *  during cold start (over BOTH the tab shell and the auth flow, matching
 *  App.kt's z-stacked splash) and fades it after
 *  [IosShellBridge.splashMinDisplayMs]. */
fun SplashViewController(): UIViewController = ComposeUIViewController {
    SplashScreen()
}
