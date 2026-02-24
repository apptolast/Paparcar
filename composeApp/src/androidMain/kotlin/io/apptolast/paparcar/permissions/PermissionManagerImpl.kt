package io.apptolast.paparcar.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import io.apptolast.paparcar.domain.permissions.AppPermissionState
import io.apptolast.paparcar.domain.permissions.PermissionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class PermissionManagerImpl(private val context: Context) : PermissionManager {

    private val _permissionState = MutableStateFlow(AppPermissionState())
    override val permissionState = _permissionState.asStateFlow()

    override fun refreshPermissions() {
        _permissionState.value = AppPermissionState(
            hasLocationPermission = hasLocationPermission(),
            hasActivityRecognitionPermission = hasActivityRecognitionPermission()
        )
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasActivityRecognitionPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Before Android Q, this permission is granted by default
        }
    }
}
