package com.rndeveloper.paparcar

import android.app.Application
import com.apptolast.baselogin.appContext
import com.apptolast.baselogin.di.loginPresentationModule
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

        // Use startKoin directly instead of initLoginKoin to bypass the library's DataModule,
        // which is Firebase-dependent. That is still the reason this bootstrap exists.
        //
        // [MOCK-AUTH-SCREENS-NEED-THEIR-VIEWMODELS-001] What changed: bypassing `initLoginKoin`
        // also dropped the library's auth ViewModels, and mockModule used to re-register ONE of
        // them by hand — so "Sign Up" and "Forgot password?" died on NoDefinitionFound while login
        // worked. Copying the two missing ones would have left the same hole open for the next
        // screen. BaseLogin 2.0.0 publishes `loginPresentationModule`, so the whole set comes in
        // as a unit and there is no per-ViewModel list to keep in sync.
        //
        // The unreachable-in-mock screens (phone, magic link, reauth, reset-by-deep-link) ride
        // along harmlessly: Koin resolves a ViewModel when it is REQUESTED, and nothing in the Dev
        // Catalog requests those. Its `LoginLibraryConfig` dependency is bound in mockModule,
        // because `initLoginKoin` — the thing that binds it in prod — is exactly what we skip.
        startKoin {
            androidContext(this@MockPaparcarApp)
            modules(
                loginPresentationModule,
                presentationModule,
                domainModule,
                mockModule,
            )
        }
    }
}
