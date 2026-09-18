package com.rndeveloper.paparcar

import androidx.compose.ui.window.ComposeUIViewController
import com.rndeveloper.paparcar.dev.ShotPreset
import com.rndeveloper.paparcar.dev.applyTo
import com.rndeveloper.paparcar.domain.detection.MutableDetectionRuntimeState
import com.rndeveloper.paparcar.fakes.MockScenario
import org.koin.mp.KoinPlatform
import com.apptolast.baselogin.di.loginPresentationModule
import com.rndeveloper.paparcar.di.domainModule
import com.rndeveloper.paparcar.di.iosMockModule
import com.rndeveloper.paparcar.di.presentationModule
import org.koin.core.context.startKoin

/**
 * The mock entry point, and since [IOS-STORE-SHOTS-WITHOUT-SHIPPING-THE-DEMO-CODE-001] also the
 * one the App Store screenshot run mounts.
 *
 * [shot] names a [ShotPreset] — the SAME enum `DevMainActivity` reads from `--es shot <key>` on
 * Android, moved into :mockkit so both platforms shoot the same eight frames from one definition.
 * That is the whole point of it existing: the carousel is 8 frames × 9 locales, and the only way
 * that is reproducible is if the scenario is chosen by the launch, never by tapping a list whose
 * rows move. [PLAY-SHOTS-EVERY-LANGUAGE-SHOWS-ITS-OWN-UI-001]
 *
 * ⛔ This function is NOT in the App Store binary: :mockkit reaches the framework only through
 * `-Ppaparcar.screenshots=true`, and Swift only calls it from inside `#if DEBUG`.
 */
fun MockMainViewController(shot: String? = null) = run {
    initMockKoin()
    ShotPreset.from(shot)?.let { preset ->
        val koin = KoinPlatform.getKoin()
        preset.applyTo(koin.get<MockScenario>(), koin.get<MutableDetectionRuntimeState>())
    }
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
