package org.cadence.mobile.di

import org.cadence.mobile.auth.SecureStorage
import org.cadence.mobile.auth.TokenStorage
import org.cadence.mobile.auth.AuthViewModel
import org.cadence.mobile.classes.ClassesViewModel
import org.cadence.mobile.networking.CadenceApiClient
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { SecureStorage() }
    single { TokenStorage(get()) }
    single { CadenceApiClient(get()) }
    viewModel { AuthViewModel(get()) }
    viewModel { ClassesViewModel(get()) }
}
