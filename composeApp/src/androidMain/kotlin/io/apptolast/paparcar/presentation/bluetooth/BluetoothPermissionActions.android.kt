package io.apptolast.paparcar.presentation.bluetooth

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import io.apptolast.paparcar.domain.permissions.PermissionManager
import org.koin.compose.koinInject

@Composable
actual fun rememberRequestBluetoothPermissionAction(): () -> Unit {
    val permissionManager = koinInject<PermissionManager>()
    val launcher = rememberLauncherForActivityResult(RequestPermission()) {
        permissionManager.refreshPermissions()
    }
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        { launcher.launch(Manifest.permission.BLUETOOTH_CONNECT) }
    } else {
        {}
    }
}

@Composable
actual fun rememberOpenBluetoothSettingsAction(): () -> Unit {
    val context = LocalContext.current
    return { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
}
