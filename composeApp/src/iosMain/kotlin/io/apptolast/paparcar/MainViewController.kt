@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.apptolast.paparcar

import androidx.compose.ui.window.ComposeUIViewController
import com.apptolast.customlogin.di.LoginLibraryConfig
import com.apptolast.customlogin.di.initLoginKoin
import io.apptolast.paparcar.di.dataModule
import io.apptolast.paparcar.di.domainModule
import io.apptolast.paparcar.di.iosDetectionModule
import io.apptolast.paparcar.di.iosPlatformModule
import io.apptolast.paparcar.di.presentationModule
import io.apptolast.paparcar.notification.IosNotificationActionHandler
import org.koin.mp.KoinPlatform
import platform.UserNotifications.UNUserNotificationCenter

fun MainViewController() = run {
    initKoin()
    installNotificationDelegate()
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
    initLoginKoin(config = LoginLibraryConfig()) {
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
