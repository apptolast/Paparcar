package com.rndeveloper.paparcar

import androidx.compose.ui.window.ComposeUIViewController
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
            presentationModule,
            domainModule,
            iosMockModule,
        )
    }
}
