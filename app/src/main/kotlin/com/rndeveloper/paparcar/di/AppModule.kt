package com.rndeveloper.paparcar.di

import com.rndeveloper.paparcar.domain.notification.AppNotificationManager
import com.rndeveloper.paparcar.notification.AppNotificationManagerImpl
import com.rndeveloper.paparcar.notification.ForegroundNotificationProvider
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Bindings whose implementations belong to the `:app` shell, not to `:shared`:
 * [AppNotificationManagerImpl] builds PendingIntents on `MainActivity` and reads launcher
 * resources (`R.drawable`/`R.string`), both of which only exist in the application module.
 * Registered by `PaparcarApp` alongside the shared modules; the mock flavor binds
 * `FakeAppNotificationManager` instead and never loads this module.
 */
val appModule = module {
    // Notification — single instance implements both contracts
    single { AppNotificationManagerImpl(androidContext(), get(), get()) }
    single<AppNotificationManager> { get<AppNotificationManagerImpl>() }
    single<ForegroundNotificationProvider> { get<AppNotificationManagerImpl>() }
}
