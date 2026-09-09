package com.rndeveloper.paparcar.presentation.permissions

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.runtime.Composable
import com.rndeveloper.paparcar.domain.permissions.PermissionManager
import org.koin.compose.koinInject

@Composable
actual fun rememberRequestFineLocationAction(): () -> Unit {
    val permissionManager = koinInject<PermissionManager>()
    val launcher = rememberLauncherForActivityResult(RequestPermission()) {
        permissionManager.refreshPermissions()
    }
    return { launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
}
