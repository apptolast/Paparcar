@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.rndeveloper.paparcar

import androidx.compose.ui.window.ComposeUIViewController
import com.apptolast.baselogin.di.initLoginKoin
import com.rndeveloper.paparcar.di.dataModule
import com.rndeveloper.paparcar.di.domainModule
import com.rndeveloper.paparcar.di.iosDetectionModule
import com.rndeveloper.paparcar.di.iosPlatformModule
import com.rndeveloper.paparcar.di.paparcarLoginConfig
import com.rndeveloper.paparcar.di.presentationModule
import com.rndeveloper.paparcar.detection.IosDetectionController
import com.rndeveloper.paparcar.notification.IosNotificationActionHandler
import org.koin.mp.KoinPlatform
import platform.UserNotifications.UNUserNotificationCenter

fun MainViewController() = run {
    initKoin()
    installNotificationDelegate()
    // [IOS-F1-A-CONTROLLER-FOR-THE-HAPPY-PATH-001] Start the detection orchestrator: its
    // geofence-bus subscription must exist before any region delegate can fire (the bus has no
    // replay), and its start-of-life reconcile heals the region inventory. Idempotent.
    KoinPlatform.getKoin().get<IosDetectionController>().start()
    ComposeUIViewController { App() }
}

private var koinInitialized = false

// Held at file scope because UNUserNotificationCenter.delegate is weak — letting this go
// out of scope would silently disable Yes/No routing on the parking-confirmation notification.
private var notificationDelegate: IosNotificationActionHandler? = null

private fun initKoin() {
    if (koinInitialized) return
    koinInitialized = true
    // Start Koin via the BaseLogin initializer so the library's auth modules (AuthRepository,
    // login data/presentation) are registered alongside the app modules — mirrors PaparcarApp on
    // Android. Without this, resolving anything that depends on AuthRepository crashes at launch.
    // Email/password + magic-link work out of the box; Google/Apple sign-in on iOS additionally
    // require provider config here plus native OAuth setup (Info.plist URL scheme + plist client id).
    // Same offer builder as Android. Without it the default config leaves iOS showing a lone SMS
    // button — a method we do not support, and whose Swift handlers are not installed either, so it
    // would be a dead button. Google arrives with IOS-SOCIAL-LOGIN-001. [AUTH-PROVIDERS-EXPLICIT-001]
    initLoginKoin(config = paparcarLoginConfig(googleWebClientId = null)) {
        modules(
            presentationModule,
            domainModule,
            dataModule,
            iosPlatformModule,
            iosDetectionModule,
        )
    }
}

private fun installNotificationDelegate() {
    if (notificationDelegate != null) return
    val koin = KoinPlatform.getKoin()
    val handler = IosNotificationActionHandler(
        coordinator = koin.get(),
        revertParkingUseCase = koin.get(),
        notificationPort = koin.get(),
    )
    notificationDelegate = handler
    UNUserNotificationCenter.currentNotificationCenter().setDelegate(handler)
}
