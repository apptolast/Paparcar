package com.rndeveloper.paparcar.fakes.data.repository

import com.rndeveloper.paparcar.domain.geocoder.GeocoderDataSource
import com.rndeveloper.paparcar.domain.model.AddressInfo
import com.rndeveloper.paparcar.domain.model.PlaceInfo
import com.rndeveloper.paparcar.domain.model.SearchResult
import com.rndeveloper.paparcar.domain.places.PlacesDataSource
import com.rndeveloper.paparcar.domain.permissions.PermissionManager
import com.rndeveloper.paparcar.domain.permissions.AppPermissionState
import com.rndeveloper.paparcar.domain.permissions.OemBackgroundReliabilityManager
import com.rndeveloper.paparcar.domain.detection.PendingParkNudge
import com.rndeveloper.paparcar.domain.detection.PendingPromptWindow
import com.rndeveloper.paparcar.domain.preferences.AppPreferences
import com.rndeveloper.paparcar.domain.preferences.ThemeMode
import com.rndeveloper.paparcar.domain.bluetooth.BluetoothScanner
import com.rndeveloper.paparcar.domain.bluetooth.BtConnection
import com.rndeveloper.paparcar.domain.connectivity.ConnectivityObserver
import com.rndeveloper.paparcar.domain.connectivity.ConnectivityStatus
import com.rndeveloper.paparcar.domain.model.bluetooth.BluetoothDeviceInfo
import com.rndeveloper.paparcar.fakes.MockScenario
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Clock

/**
 * [DET-ASK-STATE-001] The car the mock question is about. MUST be the mock fleet's active vehicle:
 * in production this name is copied from the notification that posted the question, so it always
 * agrees with the trip — a different name here would show a disagreement the real app cannot
 * produce (the peek eyebrow names the trip's car, the row would name another).
 */
private const val MOCK_PROMPT_VEHICLE = "Toyota Corolla"

/** The mock has one connected car at a time, so any non-zero stamp ranks it. Only the ordering of
 *  TWO live links needs a real clock, and the mock never produces two.
 *  [UI-MAP-PUCK-BELONGS-TO-THE-DRIVE-NOT-TO-ONE-LANE-001] */
private const val MOCK_CONNECTED_AT_MS = 1L

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

/**
 * @param scenario when non-null, both proprietary gates mirror [MockScenario.aggressiveOem] so the
 * Dev Catalog can exercise the REDUCED-reliability surfaces on any emulator. [DET-RELIABILITY-001]
 */
class FakeOemBackgroundReliabilityManager(
    private val scenario: MockScenario? = null,
) : OemBackgroundReliabilityManager {
    override val requiresAutostartWhitelist: Boolean get() = scenario?.aggressiveOem?.value ?: false
    override suspend fun launchAutostartSettings(): Boolean = false
    override val requiresOemBatterySettings: Boolean get() = scenario?.aggressiveOem?.value ?: false
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

    private var _firstParkNudgeCount = 0
    override val firstParkNudgeCount: Int get() = _firstParkNudgeCount
    override fun setFirstParkNudgeCount(count: Int) { _firstParkNudgeCount = count }
    private var _lastFirstParkNudgeAt = 0L
    override val lastFirstParkNudgeAtMillis: Long get() = _lastFirstParkNudgeAt
    override fun setLastFirstParkNudgeAt(millis: Long) { _lastFirstParkNudgeAt = millis }
    private var _hasConfirmedFirstPark = false
    override val hasConfirmedFirstPark: Boolean get() = _hasConfirmedFirstPark
    override fun setHasConfirmedFirstPark() { _hasConfirmedFirstPark = true }

    // [DET-NUDGE-PERSIST-001]
    private val _pendingParkNudge = MutableStateFlow<PendingParkNudge?>(null)
    override fun observePendingParkNudge(): kotlinx.coroutines.flow.Flow<PendingParkNudge?> = _pendingParkNudge
    override fun setPendingParkNudge(nudge: PendingParkNudge) { _pendingParkNudge.value = nudge }
    override fun clearPendingParkNudge() { _pendingParkNudge.value = null }

    // [DET-ASK-STATE-001] Scenario-aware, like the rest of the mock preferences: the Dev Catalog
    // flips `promptOpen` and the real Home renders the question row. The window is stamped at read
    // time so it is always inside the response window (a fixed timestamp would age out mid-demo).
    private val pendingPromptWindow = MutableStateFlow<PendingPromptWindow?>(null)
    override fun observePendingPromptWindow(): kotlinx.coroutines.flow.Flow<PendingPromptWindow?> =
        scenario?.promptOpen?.map { open ->
            if (open) PendingPromptWindow(Clock.System.now().toEpochMilliseconds(), MOCK_PROMPT_VEHICLE) else null
        } ?: pendingPromptWindow
    override fun setPendingPromptWindow(window: PendingPromptWindow) { pendingPromptWindow.value = window }
    override fun clearPendingPromptWindow() {
        pendingPromptWindow.value = null
        scenario?.promptOpen?.value = false
    }

    private val _notifyParkingDetected = MutableStateFlow(true)
    override val notifyParkingDetected: Boolean get() = _notifyParkingDetected.value
    override fun setNotifyParkingDetected(enabled: Boolean) { _notifyParkingDetected.value = enabled }

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    override val themeMode: ThemeMode get() = _themeMode.value
    override fun setThemeMode(mode: ThemeMode) { _themeMode.value = mode }

    private val _useImperialUnits = MutableStateFlow(false)
    override val useImperialUnits: Boolean get() = _useImperialUnits.value
    override fun setUseImperialUnits(enabled: Boolean) { _useImperialUnits.value = enabled }

    private val _defaultMapType = MutableStateFlow("TERRAIN")
    override val defaultMapType: String get() = _defaultMapType.value
    override fun setDefaultMapType(type: String) { _defaultMapType.value = type }
}

/**
 * [UI-MAP-PUCK-BELONGS-TO-THE-DRIVE-NOT-TO-ONE-LANE-001] Connection follows the Dev Catalog's BT
 * lever, and nothing else.
 *
 * ⚠ It used to answer `pairedVehicleIds.isNotEmpty()`, on the stated assumption that "the mock only
 * assigns a bluetoothDeviceId when the BT scenario is on". That was never true — `mock_vehicle_002`
 * and `_004` carry hard-coded MACs — so the mock resolved BLUETOOTH **permanently**, whatever the
 * lever said, and the Coordinator stories were being told under the wrong strategy. Harmless while
 * the strategy only picked banner copy; now that a connected car also drives the trip surface, an
 * always-connected mock would park a car puck on Home forever.
 *
 * With the lever off, the fleet still HAS paired cars that are not connected — which is precisely
 * the situation [DET-BT-CONNECTED-NOT-PAIRED-001] exists for, and the mock now models it.
 */
class FakeBluetoothScanner(private val scenario: MockScenario? = null) : BluetoothScanner {
    override fun isBluetoothEnabled(): Boolean = true
    override fun getBondedDevices(): List<BluetoothDeviceInfo> = emptyList()

    override fun isConnectedToPairedCar(pairedVehicleIds: Set<String>): Boolean =
        connectedIds().any { it in pairedVehicleIds }

    override fun observeConnectedPairedCars(): Flow<List<BtConnection>> =
        scenario?.activeVehicleBluetooth?.map { on -> connectedCars(on) } ?: flowOf(emptyList())

    private fun connectedCars(on: Boolean) =
        connectedIds(on).map { BtConnection(vehicleId = it, connectedAtMs = MOCK_CONNECTED_AT_MS) }

    private fun connectedIds(on: Boolean = scenario?.activeVehicleBluetooth?.value == true): Set<String> =
        if (on) setOf(FakeVehicleRepository.ACTIVE_VEHICLE_ID) else emptySet()
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
