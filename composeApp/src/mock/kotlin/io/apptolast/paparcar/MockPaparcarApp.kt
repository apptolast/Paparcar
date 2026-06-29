package io.apptolast.paparcar

import android.app.Application
import com.apptolast.customlogin.appContext
import io.apptolast.paparcar.di.domainModule
import io.apptolast.paparcar.di.mockModule
import io.apptolast.paparcar.di.presentationModule
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class MockPaparcarApp : Application() {
    override fun onCreate() {
        super.onCreate()

        appContext = this
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
