package io.apptolast.paparcar.ios.stub

import io.apptolast.paparcar.domain.permissions.AppPermissionState
import io.apptolast.paparcar.domain.permissions.PermissionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class StubPermissionManager : PermissionManager {
    private val _permissionState = MutableStateFlow(
        AppPermissionState(
            hasLocationPermission = true,
            hasActivityRecognitionPermission = true,
        )
    )
    override val permissionState: StateFlow<AppPermissionState> = _permissionState.asStateFlow()
    override fun refreshPermissions() {}
}
