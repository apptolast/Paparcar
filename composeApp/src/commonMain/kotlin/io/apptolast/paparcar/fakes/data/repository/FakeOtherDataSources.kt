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
import io.apptolast.paparcar.fakes.MockScenario
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

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

/**
 * @param scenario when non-null, [permissionState] reflects [MockScenario.permissionTier] +
 * [MockScenario.gpsEnabled] so the Dev Catalog can land on the rationale/permissions screens.
 * When null it reports everything granted (original behaviour, used by tests/default boot).
 */
class FakePermissionManager(private val scenario: MockScenario? = null) : PermissionManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val permissionState: StateFlow<AppPermissionState> =
        if (scenario != null) {
            combine(scenario.permissionTier, scenario.gpsEnabled) { tier, gps -> tier.toState(gps) }
                .stateIn(
                    scope,
                    SharingStarted.Eagerly,
                    scenario.permissionTier.value.toState(scenario.gpsEnabled.value),
                )
        } else {
            MutableStateFlow(
                AppPermissionState(
                    hasLocationPermission = true,
                    hasBackgroundLocationPermission = true,
                    hasActivityRecognitionPermission = true,
                    hasNotificationPermission = true,
                    isLocationServicesEnabled = true,
                ),
            ).asStateFlow()
        }

    override fun refreshPermissions() {}

    private fun MockScenario.PermissionTier.toState(gps: Boolean): AppPermissionState = when (this) {
        MockScenario.PermissionTier.None -> AppPermissionState(isLocationServicesEnabled = gps)
        MockScenario.PermissionTier.Core -> AppPermissionState(
            hasLocationPermission = true,
            hasNotificationPermission = true,
            isLocationServicesEnabled = gps,
        )
        MockScenario.PermissionTier.Producer -> AppPermissionState(
            hasLocationPermission = true,
            hasBackgroundLocationPermission = true,
            hasActivityRecognitionPermission = true,
            hasNotificationPermission = true,
            isLocationServicesEnabled = gps,
        )
        MockScenario.PermissionTier.All -> AppPermissionState(
            hasLocationPermission = true,
            hasBackgroundLocationPermission = true,
            hasActivityRecognitionPermission = true,
            hasNotificationPermission = true,
            isLocationServicesEnabled = gps,
            hasBluetoothConnectPermission = true,
            isBatteryOptimizationExempt = true,
        )
    }
}

class FakeOemBackgroundReliabilityManager : OemBackgroundReliabilityManager {
    override val requiresAutostartWhitelist: Boolean = false
    override suspend fun launchAutostartSettings(): Boolean = false
    override val requiresOemBatterySettings: Boolean = false
    override suspend fun launchOemBatterySettings(): Boolean = false
}

/**
 * @param scenario when non-null, [isOnboardingCompleted] is backed by [MockScenario.onboardingCompleted]
 * so the Dev Catalog can route into the onboarding flow. Other prefs keep their in-memory defaults.
 */
class FakeAppPreferences(private val scenario: MockScenario? = null) : AppPreferences {
    private val _isOnboardingCompleted = MutableStateFlow(true)
    override val isOnboardingCompleted: Boolean
        get() = scenario?.onboardingCompleted?.value ?: _isOnboardingCompleted.value
    override fun setOnboardingCompleted() {
        scenario?.let { it.onboardingCompleted.value = true } ?: run { _isOnboardingCompleted.value = true }
    }

    private val _hasSeenGpsAccuracyDisclaimer = MutableStateFlow(true)
    override val hasSeenGpsAccuracyDisclaimer: Boolean get() = _hasSeenGpsAccuracyDisclaimer.value
    override fun setGpsAccuracyDisclaimerSeen() { _hasSeenGpsAccuracyDisclaimer.value = true }

    private val _hasRequestedLocationPermission = MutableStateFlow(false)
    override val hasRequestedLocationPermission: Boolean get() = _hasRequestedLocationPermission.value
    override fun setLocationPermissionRequested() { _hasRequestedLocationPermission.value = true }

    private val _autoDetectParking = MutableStateFlow(true)
    override val autoDetectParking: Boolean get() = _autoDetectParking.value
    override fun setAutoDetectParking(enabled: Boolean) { _autoDetectParking.value = enabled }
    override fun observeAutoDetectParking(): kotlinx.coroutines.flow.Flow<Boolean> = _autoDetectParking

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

/**
 * @param scenario when non-null, [status] tracks [MockScenario.online] so the Dev Catalog can
 * exercise the offline banner + bootstrap-offline dialog. When null it is always Online.
 */
class FakeConnectivityObserver(private val scenario: MockScenario? = null) : ConnectivityObserver {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val status: StateFlow<ConnectivityStatus> =
        if (scenario != null) {
            scenario.online
                .map { online -> if (online) ConnectivityStatus.Online else ConnectivityStatus.Offline }
                .stateIn(
                    scope,
                    SharingStarted.Eagerly,
                    if (scenario.online.value) ConnectivityStatus.Online else ConnectivityStatus.Offline,
                )
        } else {
            MutableStateFlow(ConnectivityStatus.Online).asStateFlow()
        }

    override fun start() {}
    override fun stop() {}
}
