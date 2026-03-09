package io.apptolast.paparcar

import androidx.compose.ui.window.ComposeUIViewController
import io.apptolast.paparcar.di.dataModule
import io.apptolast.paparcar.di.domainModule
import io.apptolast.paparcar.di.iosDetectionModule
import io.apptolast.paparcar.di.iosPlatformModule
import io.apptolast.paparcar.di.presentationModule
import org.koin.core.context.startKoin

fun MainViewController() = run {
    initKoin()
    ComposeUIViewController { App() }
}

private var koinInitialized = false

private fun initKoin() {
    if (koinInitialized) return
    koinInitialized = true
    startKoin {
        modules(
            presentationModule,
            domainModule,
            dataModule,
            iosPlatformModule,
            iosDetectionModule,
        )
    }
}
