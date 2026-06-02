package org.arcana.mobile.di

import org.arcana.mobile.auth.SecureStorage
import org.arcana.mobile.auth.TokenStorage
import org.arcana.mobile.auth.AuthViewModel
import org.arcana.mobile.booking.BookingViewModel
import org.arcana.mobile.booking.MyBookingsViewModel
import org.arcana.mobile.defaultBaseUrl
import org.arcana.mobile.networking.ArcanaApiClient
import org.arcana.mobile.networking.BaseUrlProvider
import org.arcana.mobile.networking.BookingApi
import org.arcana.mobile.networking.MembershipApi
import org.arcana.mobile.schedule.ClassDetailViewModel
import org.arcana.mobile.schedule.ScheduleViewModel
import org.arcana.mobile.signup.CompleteSignupApi
import org.arcana.mobile.signup.CompleteSignupCallable
import org.arcana.mobile.signup.SignupCompletionViewModel
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
    single<BookingApi> { get<ArcanaApiClient>() }
    single<MembershipApi> { get<ArcanaApiClient>() }
    single<CompleteSignupCallable> { CompleteSignupApi(get()) }
    viewModel { AuthViewModel(get()) }
    viewModel { MyBookingsViewModel(api = get()) }
    viewModel { ScheduleViewModel(get()) }
    viewModel { DeveloperSettingsViewModel(get()) }
    viewModel { (sessionId: Int) -> ClassDetailViewModel(get(), sessionId) }
    viewModel { (token: String) -> SignupCompletionViewModel(token, get()) }
    viewModel { (sessionId: Int, spotsAvailable: Int, requiresSpot: Boolean) ->
        BookingViewModel(sessionId, spotsAvailable, requiresSpot, bookingApi = get(), membershipApi = get())
    }
}
