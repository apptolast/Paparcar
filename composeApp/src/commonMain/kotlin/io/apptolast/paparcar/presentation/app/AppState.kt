package io.apptolast.paparcar.presentation.app

import io.apptolast.paparcar.domain.connectivity.ConnectivityStatus
import io.apptolast.paparcar.domain.preferences.ThemeMode

data class AppState(
    val permissionsGranted: Boolean = false,
    val locationServicesEnabled: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val imperialUnits: Boolean = false,
    val selectedLanguage: String = "auto",
    val connectivity: ConnectivityStatus = ConnectivityStatus.Online,
    val hasSeenGpsAccuracyDisclaimer: Boolean = false,
) {
    /** True only when runtime permissions AND GPS are both ready. */
    val isFullyOperational: Boolean
        get() = permissionsGranted && locationServicesEnabled

    val isOffline: Boolean
        get() = connectivity == ConnectivityStatus.Offline

    /** Show once when the user is fully operational and hasn't dismissed the disclaimer yet. */
    val showGpsAccuracyDisclaimer: Boolean
        get() = isFullyOperational && !hasSeenGpsAccuracyDisclaimer
}
