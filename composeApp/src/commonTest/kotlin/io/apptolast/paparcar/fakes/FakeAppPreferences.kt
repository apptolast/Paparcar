package io.apptolast.paparcar.fakes

import io.apptolast.paparcar.domain.preferences.AppPreferences

class FakeAppPreferences(
    initialCompleted: Boolean = false,
    initialAutoDetect: Boolean = true,
    initialNotifyParking: Boolean = true,
    initialNotifySpot: Boolean = true,
    initialVehicleRegistered: Boolean = false,
    initialDarkMode: Boolean = true,
) : AppPreferences {

    private var _isOnboardingCompleted = initialCompleted
    override val isOnboardingCompleted: Boolean get() = _isOnboardingCompleted

    var setOnboardingCompletedCount = 0
        private set

    override fun setOnboardingCompleted() {
        _isOnboardingCompleted = true
        setOnboardingCompletedCount++
    }

    private var _autoDetectParking = initialAutoDetect
    override val autoDetectParking: Boolean get() = _autoDetectParking
    override fun setAutoDetectParking(enabled: Boolean) { _autoDetectParking = enabled }

    private var _notifyParkingDetected = initialNotifyParking
    override val notifyParkingDetected: Boolean get() = _notifyParkingDetected
    override fun setNotifyParkingDetected(enabled: Boolean) { _notifyParkingDetected = enabled }

    private var _notifySpotFreed = initialNotifySpot
    override val notifySpotFreed: Boolean get() = _notifySpotFreed
    override fun setNotifySpotFreed(enabled: Boolean) { _notifySpotFreed = enabled }

    private var _hasVehicleRegistered = initialVehicleRegistered
    override val hasVehicleRegistered: Boolean get() = _hasVehicleRegistered
    override fun setVehicleRegistered() { _hasVehicleRegistered = true }

    private var _darkModeEnabled = initialDarkMode
    override val darkModeEnabled: Boolean get() = _darkModeEnabled
    override fun setDarkModeEnabled(enabled: Boolean) { _darkModeEnabled = enabled }
}
