package io.apptolast.paparcar.fakes

import io.apptolast.paparcar.domain.preferences.AppPreferences
import io.apptolast.paparcar.domain.preferences.ThemeMode

class FakeAppPreferences(
    initialCompleted: Boolean = false,
    initialAutoDetect: Boolean = true,
    initialNotifyParking: Boolean = true,
    initialNotifySpot: Boolean = true,
    initialVehicleRegistered: Boolean = false,
    initialThemeMode: ThemeMode = ThemeMode.SYSTEM,
    initialUseImperialUnits: Boolean = false,
    initialDefaultMapType: String = "NORMAL",
    initialSelectedLanguage: String = "auto",
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

    private var _themeMode = initialThemeMode
    override val themeMode: ThemeMode get() = _themeMode
    override fun setThemeMode(mode: ThemeMode) { _themeMode = mode }

    private var _useImperialUnits = initialUseImperialUnits
    override val useImperialUnits: Boolean get() = _useImperialUnits
    override fun setUseImperialUnits(enabled: Boolean) { _useImperialUnits = enabled }

    private var _defaultMapType = initialDefaultMapType
    override val defaultMapType: String get() = _defaultMapType
    override fun setDefaultMapType(type: String) { _defaultMapType = type }

    private var _selectedLanguage = initialSelectedLanguage
    override val selectedLanguage: String get() = _selectedLanguage
    override fun setSelectedLanguage(tag: String) { _selectedLanguage = tag }
}
