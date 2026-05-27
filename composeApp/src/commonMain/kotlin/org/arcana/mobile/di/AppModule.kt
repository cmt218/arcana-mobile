package org.arcana.mobile.di

import org.arcana.mobile.auth.SecureStorage
import org.arcana.mobile.auth.TokenStorage
import org.arcana.mobile.auth.AuthViewModel
import org.arcana.mobile.defaultBaseUrl
import org.arcana.mobile.networking.ArcanaApiClient
import org.arcana.mobile.networking.BaseUrlProvider
import org.arcana.mobile.schedule.ClassDetailViewModel
import org.arcana.mobile.schedule.ScheduleViewModel
import org.arcana.mobile.settings.DeveloperSettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { SecureStorage() }
    single { TokenStorage(get()) }
    // Default URL is platform-specific (emulator loopback on Android,
    // localhost on iOS). Physical devices override via Developer Settings.
    single { BaseUrlProvider(get(), defaultBaseUrl()) }
    single { ArcanaApiClient(get(), get()) }
    viewModel { AuthViewModel(get()) }
    viewModel { ScheduleViewModel(get()) }
    viewModel { DeveloperSettingsViewModel(get()) }
    viewModel { (sessionId: Int) -> ClassDetailViewModel(get(), sessionId) }
}
