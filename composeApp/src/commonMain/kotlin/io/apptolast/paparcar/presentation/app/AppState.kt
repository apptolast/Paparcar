package io.apptolast.paparcar.presentation.app

import io.apptolast.paparcar.domain.connectivity.ConnectivityStatus

data class AppState(
    val permissionsGranted: Boolean = false,
    val locationServicesEnabled: Boolean = false,
    val darkTheme: Boolean = true,
    val imperialUnits: Boolean = false,
    val connectivity: ConnectivityStatus = ConnectivityStatus.Online,
) {
    /** True only when runtime permissions AND GPS are both ready. */
    val isFullyOperational: Boolean
        get() = permissionsGranted && locationServicesEnabled

    val isOffline: Boolean
        get() = connectivity == ConnectivityStatus.Offline
}
