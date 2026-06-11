@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.apptolast.paparcar.presentation.bluetooth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.apptolast.paparcar.domain.permissions.PermissionManager
import io.apptolast.paparcar.ios.permissions.IosPermissionRequester
import org.koin.compose.koinInject
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString

@Composable
actual fun rememberRequestBluetoothPermissionAction(): () -> Unit {
    val permissionManager = koinInject<PermissionManager>()
    val requester = remember(permissionManager) { IosPermissionRequester(permissionManager) }
    return { requester.requestBluetooth() }
}

// iOS has no public deep link to the system Bluetooth panel (App-prefs:Bluetooth is rejected by Apple),
// so we open the app's Settings page where the Bluetooth permission toggle lives.
@Composable
actual fun rememberOpenBluetoothSettingsAction(): () -> Unit {
    return {
        NSURL.URLWithString(UIApplicationOpenSettingsURLString)?.let { url ->
            UIApplication.sharedApplication.openURL(
                url = url,
                options = emptyMap<Any?, Any?>(),
                completionHandler = null,
            )
        }
    }
}
