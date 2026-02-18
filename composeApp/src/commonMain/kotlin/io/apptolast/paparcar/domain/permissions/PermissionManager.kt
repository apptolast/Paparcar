package io.apptolast.paparcar.domain.permissions

import kotlinx.coroutines.flow.StateFlow

interface PermissionManager {
    val permissionState: StateFlow<AppPermissionState>
    fun refreshPermissions()
}
