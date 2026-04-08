package org.cadence.mobile

import androidx.compose.ui.window.ComposeUIViewController
import org.cadence.mobile.di.appModule
import org.koin.core.context.startKoin
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    startKoin {
        modules(appModule)
    }
    return ComposeUIViewController { App() }
}
