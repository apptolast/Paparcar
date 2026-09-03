package com.rndeveloper.paparcar

import androidx.compose.ui.window.ComposeUIViewController
import com.apptolast.baselogin.di.loginPresentationModule
import com.rndeveloper.paparcar.di.domainModule
import com.rndeveloper.paparcar.di.iosMockModule
import com.rndeveloper.paparcar.di.presentationModule
import org.koin.core.context.startKoin

fun MockMainViewController() = run {
    initMockKoin()
    ComposeUIViewController { App() }
}

private var mockKoinInitialized = false

private fun initMockKoin() {
    if (mockKoinInitialized) return
    mockKoinInitialized = true
    startKoin {
        modules(
            // Same list as `MockPaparcarApp` on Android: the auth ViewModels arrive with
            // BaseLogin's own module, and iosMockModule binds the LoginLibraryConfig they need
            // because mock skips `initLoginKoin`. [MOCK-AUTH-SCREENS-NEED-THEIR-VIEWMODELS-001]
            loginPresentationModule,
            presentationModule,
            domainModule,
            iosMockModule,
        )
    }
}
