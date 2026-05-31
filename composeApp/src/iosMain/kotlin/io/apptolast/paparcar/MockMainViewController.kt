package io.apptolast.paparcar

import androidx.compose.ui.window.ComposeUIViewController
import io.apptolast.paparcar.di.domainModule
import io.apptolast.paparcar.di.iosMockModule
import io.apptolast.paparcar.di.presentationModule
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
