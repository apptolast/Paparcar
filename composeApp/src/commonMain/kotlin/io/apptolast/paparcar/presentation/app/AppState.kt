package io.apptolast.paparcar.presentation.app

data class AppState(
    val permissionsGranted: Boolean = false,
    val locationServicesEnabled: Boolean = false,
) {
    /** True only when runtime permissions AND GPS are both ready. */
    val isFullyOperational: Boolean
        get() = permissionsGranted && locationServicesEnabled
}
