package com.rndeveloper.paparcar.presentation.app

import com.rndeveloper.paparcar.domain.connectivity.ConnectivityBannerPhase
import com.rndeveloper.paparcar.domain.connectivity.ConnectivityStatus
import com.rndeveloper.paparcar.domain.preferences.ThemeMode

data class AppState(
    val permissionsGranted: Boolean = false,
    val locationServicesEnabled: Boolean = false,
    val hasVehicle: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val imperialUnits: Boolean = false,
    val connectivity: ConnectivityStatus = ConnectivityStatus.Online,
    /** Drives the root connectivity banner (Hidden/Offline/Restored). [CONN-BANNER-001] */
    val connectivityBanner: ConnectivityBannerPhase = ConnectivityBannerPhase.Hidden,
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
