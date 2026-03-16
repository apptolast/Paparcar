package io.apptolast.paparcar.fakes

import io.apptolast.paparcar.domain.permissions.AppPermissionState
import io.apptolast.paparcar.domain.permissions.PermissionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakePermissionManager : PermissionManager {

    private val _permissionState = MutableStateFlow(AppPermissionState())
    override val permissionState: StateFlow<AppPermissionState> = _permissionState.asStateFlow()

    var refreshCount = 0
        private set

    override fun refreshPermissions() {
        refreshCount++
    }

    /** Programmatically push a new permission state (simulates OS callback). */
    fun emit(state: AppPermissionState) {
        _permissionState.value = state
    }

    companion object {
        fun allGranted() = AppPermissionState(
            hasLocationPermission = true,
            hasBackgroundLocationPermission = true,
            hasActivityRecognitionPermission = true,
            hasNotificationPermission = true,
            isLocationServicesEnabled = true,
        )

        fun allDenied() = AppPermissionState()

        fun permissionsOnlyNoGps() = AppPermissionState(
            hasLocationPermission = true,
            hasBackgroundLocationPermission = true,
            hasActivityRecognitionPermission = true,
            hasNotificationPermission = true,
            isLocationServicesEnabled = false,
        )
    }
}
