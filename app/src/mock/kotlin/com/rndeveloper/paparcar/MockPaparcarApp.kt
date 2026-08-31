package com.rndeveloper.paparcar

import android.app.Application
import com.apptolast.baselogin.appContext
import com.rndeveloper.paparcar.di.domainModule
import com.rndeveloper.paparcar.di.mockModule
import com.rndeveloper.paparcar.di.presentationModule
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class MockPaparcarApp : Application() {
    override fun onCreate() {
        super.onCreate()

        appContext = this
        // Shared code reads build facts via AppBuildInfo (BuildConfig is app-module-only).
        AppBuildInfo.isDebug = BuildConfig.DEBUG
        AppBuildInfo.versionName = BuildConfig.VERSION_NAME
        Napier.base(DebugAntilog())

        // Use startKoin directly instead of initLoginKoin to bypass the library's internal
        // DataModule (Firebase-dependent). The library's auth LoginViewModel is registered
        // explicitly in mockModule (its presentationModule is library-internal, so we can't
        // include it) — otherwise the login screen crashes when the LoggedOut scenario shows it.
        startKoin {
            androidContext(this@MockPaparcarApp)
            modules(
                presentationModule,
                domainModule,
                mockModule,
            )
        }
    }
}
