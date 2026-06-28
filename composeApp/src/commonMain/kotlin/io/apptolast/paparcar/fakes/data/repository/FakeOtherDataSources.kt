package io.apptolast.paparcar.fakes.data.repository

import io.apptolast.paparcar.domain.geocoder.GeocoderDataSource
import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.PlaceInfo
import io.apptolast.paparcar.domain.model.SearchResult
import io.apptolast.paparcar.domain.places.PlacesDataSource
import io.apptolast.paparcar.domain.permissions.PermissionManager
import io.apptolast.paparcar.domain.permissions.AppPermissionState
import io.apptolast.paparcar.domain.permissions.OemBackgroundReliabilityManager
import io.apptolast.paparcar.domain.preferences.AppPreferences
import io.apptolast.paparcar.domain.preferences.ThemeMode
import io.apptolast.paparcar.domain.bluetooth.BluetoothScanner
import io.apptolast.paparcar.domain.connectivity.ConnectivityObserver
import io.apptolast.paparcar.domain.connectivity.ConnectivityStatus
import io.apptolast.paparcar.domain.model.bluetooth.BluetoothDeviceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeGeocoderDataSource : GeocoderDataSource {
    override suspend fun getAddress(lat: Double, lon: Double): Result<AddressInfo> =
        Result.success(AddressInfo("Calle Real", "Puerto de Santa María", "Cádiz", "España", "ES"))

    override suspend fun searchByName(query: String, maxResults: Int): Result<List<SearchResult>> =
        Result.success(emptyList())
}

class FakePlacesDataSource : PlacesDataSource {
    override suspend fun getNearbyPlace(lat: Double, lon: Double): Result<PlaceInfo?> =
        Result.success(null)
}

class FakePermissionManager : PermissionManager {
    override val permissionState: StateFlow<AppPermissionState> =
        MutableStateFlow(AppPermissionState(
            hasLocationPermission = true,
            hasBackgroundLocationPermission = true,
            hasActivityRecognitionPermission = true,
            hasNotificationPermission = true,
            isLocationServicesEnabled = true
        )).asStateFlow()

    override fun refreshPermissions() {}
}

class FakeOemBackgroundReliabilityManager : OemBackgroundReliabilityManager {
    override val requiresAutostartWhitelist: Boolean = false
    override suspend fun launchAutostartSettings(): Boolean = false
    override val requiresOemBatterySettings: Boolean = false
    override suspend fun launchOemBatterySettings(): Boolean = false
}

class FakeAppPreferences : AppPreferences {
    private val _isOnboardingCompleted = MutableStateFlow(true)
    override val isOnboardingCompleted: Boolean get() = _isOnboardingCompleted.value
    override fun setOnboardingCompleted() { _isOnboardingCompleted.value = true }

    private val _hasSeenGpsAccuracyDisclaimer = MutableStateFlow(true)
    override val hasSeenGpsAccuracyDisclaimer: Boolean get() = _hasSeenGpsAccuracyDisclaimer.value
    override fun setGpsAccuracyDisclaimerSeen() { _hasSeenGpsAccuracyDisclaimer.value = true }

    private val _hasRequestedLocationPermission = MutableStateFlow(false)
    override val hasRequestedLocationPermission: Boolean get() = _hasRequestedLocationPermission.value
    override fun setLocationPermissionRequested() { _hasRequestedLocationPermission.value = true }

    private val _autoDetectParking = MutableStateFlow(true)
    override val autoDetectParking: Boolean get() = _autoDetectParking.value
    override fun setAutoDetectParking(enabled: Boolean) { _autoDetectParking.value = enabled }

    private val _notifyParkingDetected = MutableStateFlow(true)
    override val notifyParkingDetected: Boolean get() = _notifyParkingDetected.value
    override fun setNotifyParkingDetected(enabled: Boolean) { _notifyParkingDetected.value = enabled }

    private val _notifySpotFreed = MutableStateFlow(true)
    override val notifySpotFreed: Boolean get() = _notifySpotFreed.value
    override fun setNotifySpotFreed(enabled: Boolean) { _notifySpotFreed.value = enabled }

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    override val themeMode: ThemeMode get() = _themeMode.value
    override fun setThemeMode(mode: ThemeMode) { _themeMode.value = mode }

    private val _useImperialUnits = MutableStateFlow(false)
    override val useImperialUnits: Boolean get() = _useImperialUnits.value
    override fun setUseImperialUnits(enabled: Boolean) { _useImperialUnits.value = enabled }

    private val _defaultMapType = MutableStateFlow("TERRAIN")
    override val defaultMapType: String get() = _defaultMapType.value
    override fun setDefaultMapType(type: String) { _defaultMapType.value = type }

    private val _selectedLanguage = MutableStateFlow("auto")
    override val selectedLanguage: String get() = _selectedLanguage.value
    override fun setSelectedLanguage(tag: String) { _selectedLanguage.value = tag }
}

class FakeBluetoothScanner : BluetoothScanner {
    override fun isBluetoothEnabled(): Boolean = true
    override fun getBondedDevices(): List<BluetoothDeviceInfo> = emptyList()
}

class FakeConnectivityObserver : ConnectivityObserver {
    override val status: StateFlow<ConnectivityStatus> =
        MutableStateFlow(ConnectivityStatus.Online).asStateFlow()

    override fun start() {}
    override fun stop() {}
}
