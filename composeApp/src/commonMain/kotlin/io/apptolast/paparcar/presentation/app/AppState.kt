package io.apptolast.paparcar.presentation.app

import io.apptolast.paparcar.domain.preferences.ThemeMode

data class AppState(
    val permissionsGranted: Boolean = false,
    val locationServicesEnabled: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val imperialUnits: Boolean = false,
) {
    /** True only when runtime permissions AND GPS are both ready. */
    val isFullyOperational: Boolean
        get() = permissionsGranted && locationServicesEnabled
}
