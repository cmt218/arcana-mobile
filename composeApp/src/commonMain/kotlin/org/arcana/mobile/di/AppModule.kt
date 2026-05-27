package org.arcana.mobile.di

import org.arcana.mobile.auth.SecureStorage
import org.arcana.mobile.auth.TokenStorage
import org.arcana.mobile.auth.AuthViewModel
import org.arcana.mobile.networking.ArcanaApiClient
import org.arcana.mobile.schedule.ScheduleViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { SecureStorage() }
    single { TokenStorage(get()) }
    single { ArcanaApiClient(get()) }
    viewModel { AuthViewModel(get()) }
    viewModel { ScheduleViewModel(get()) }
}
