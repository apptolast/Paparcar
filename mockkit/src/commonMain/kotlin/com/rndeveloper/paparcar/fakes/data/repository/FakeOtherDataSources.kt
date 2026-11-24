package com.rndeveloper.paparcar.fakes.data.repository

import com.rndeveloper.paparcar.domain.analytics.AdMeasurementConsent
import com.rndeveloper.paparcar.localePrefersImperialUnits
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
import com.rndeveloper.paparcar.domain.detection.PendingSpotRelease
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.onboarding.FirstStep
import com.rndeveloper.paparcar.domain.onboarding.NearbyComplement
import com.rndeveloper.paparcar.domain.spot.VouchedSpot
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Clock

/**
 * [DET-ASK-STATE-001] The car the mock question is about. MUST be the mock fleet's active vehicle:
 * in production this name is copied from the notification that posted the question, so it always
 * agrees with the trip — a different name here would show a disagreement the real app cannot
 * produce (the peek eyebrow names the trip's car, the row would name another).
 */
private const val MOCK_PROMPT_VEHICLE = "Toyota Corolla"
/** El MISMO coche que el nombre de arriba: `mock_vehicle_002`, que en el carril se llama
 *  «Second car». Sin el id la pregunta no sabe de quién habla y la noticia no puede mudarse a su
 *  card — que es justo el defecto. [HOME-A-CARS-NEWS-RIDES-ITS-OWN-CARD-001] */
private const val MOCK_PROMPT_VEHICLE_ID = "mock_vehicle_002"

/** El coche del garaje mock sobre el que se prueba la pregunta de grupo cerrado.
 *  [PRIV-A-DELETED-CAR-MUST-NOT-ORPHAN-ITS-GROUP-001] */
private const val MOCK_DISSOLVED_VEHICLE_ID = "mock_vehicle_001"

/** The mock has one connected car at a time, so any non-zero stamp ranks it. Only the ordering of
 *  TWO live links needs a real clock, and the mock never produces two.
 *  [UI-MAP-PUCK-BELONGS-TO-THE-DRIVE-NOT-TO-ONE-LANE-001] */
private const val MOCK_CONNECTED_AT_MS = 1L
/** [DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001] Where the mock question is ABOUT — inside the same
 *  same Madrid block as the seeded spots, so flipping `promptOpen` in the Dev Catalog draws the
 *  unconfirmed marker on screen instead of somewhere off the visible map. Without a place the row
 *  still renders but the marker never appears, which is a real state (a window from an older build)
 *  and NOT the one the scenario is for. */
private val MOCK_PROMPT_CANDIDATE = GpsPoint(40.4226, -3.7004, 11f, 0L, 0f)

/** The street the mock question names — a real address in the same block, so the Dev Catalog
 *  shows the wording that ships and not a placeholder. */
private const val MOCK_PROMPT_STREET = "Calle de Fuencarral 22"

class FakeGeocoderDataSource : GeocoderDataSource {
    override suspend fun getAddress(lat: Double, lon: Double): Result<AddressInfo> =
        Result.success(AddressInfo("Gran Vía", "Madrid", "Madrid", "España", "ES"))

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

    /**
     * Lo llama la app JUSTO después de que el diálogo del sistema conteste, para releer el estado.
     * En mock ese diálogo es real pero su respuesta no llega a ningún sitio, así que aquí es donde
     * se simula el «sí»: con [MockScenario.grantsLocationWhenAsked], un tier `None` sube a `Core`.
     *
     * Es la única forma de reproducir la TRANSICIÓN que arma la invitación a detección — ver la
     * palanca. Sin la palanca, no-op, como antes. [HOME-FIRST-ENTRY-FLOW-001]
     */
    override fun refreshPermissions() {
        val sc = scenario ?: return
        if (sc.grantsLocationWhenAsked.value && sc.permissionTier.value == MockScenario.PermissionTier.None) {
            sc.permissionTier.value = MockScenario.PermissionTier.Core
        }
    }

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

    // [ONBOARDING-A-TUTORIAL-IS-REMEMBERED-BY-THE-ACCOUNT-NOT-THE-DEVICE-001] Sign-out wipe. The
    // scenario's own switches are left alone on purpose: the Dev Catalog's preset IS the state
    // under demo, and a mock sign-out must not silently undo what the operator just set.
    override fun clearUserScopedState() {
        _firstStepsDone.value = emptySet()
        _firstStepsDeferred.value = emptySet()
        _firstStepsDismissed.value = false
        _hasConfirmedFirstPark = false
        _firstParkNudgeCount = 0
        _lastFirstParkNudgeAt = 0L
        _pendingParkNudge.value = null
        pendingPromptWindow.value = null
        stillParked.value = null
    }

    private val _isOnboardingCompleted = MutableStateFlow(true)
    override val isOnboardingCompleted: Boolean
        get() = scenario?.onboardingCompleted?.value ?: _isOnboardingCompleted.value
    override fun setOnboardingCompleted() {
        scenario?.let { it.onboardingCompleted.value = true } ?: run { _isOnboardingCompleted.value = true }
    }

    private val _hasSeenGpsAccuracyDisclaimer = MutableStateFlow(true)
    override val hasSeenGpsAccuracyDisclaimer: Boolean get() = _hasSeenGpsAccuracyDisclaimer.value
    override fun setGpsAccuracyDisclaimerSeen() { _hasSeenGpsAccuracyDisclaimer.value = true }

    private val _hasResolvedInstallAttribution = MutableStateFlow(true)
    override val hasResolvedInstallAttribution: Boolean get() = _hasResolvedInstallAttribution.value
    override fun setInstallAttributionResolved() { _hasResolvedInstallAttribution.value = true }

    // The mock graph has no ad lane (NoAdMeasurementLane), so the live flow never asks; the prompt
    // and the Settings row are exercised in the state gallery. [MKT-AD-MEASUREMENT-ASKS-ITS-OWN-CONSENT-001]
    private val _adMeasurementConsent = MutableStateFlow(AdMeasurementConsent.UNASKED)
    override val adMeasurementConsent: AdMeasurementConsent get() = _adMeasurementConsent.value
    override fun setAdMeasurementConsent(consent: AdMeasurementConsent) { _adMeasurementConsent.value = consent }

    private val _hasRequestedLocationPermission = MutableStateFlow(false)
    override val hasRequestedLocationPermission: Boolean get() = _hasRequestedLocationPermission.value
    override fun setLocationPermissionRequested() { _hasRequestedLocationPermission.value = true }

    // [MONEY-FOUNDERS-WELCOME-001] En memoria: el Dev Catalog vuelve a enseñar el momento en
    // cuanto se reinicia la app, que es lo que se quiere de una palanca de demo.
    private val _plusPromoWelcomedUids = MutableStateFlow<Set<String>>(emptySet())
    override val plusPromoWelcomedUids: Set<String> get() = _plusPromoWelcomedUids.value
    override fun addPlusPromoWelcomedUid(uid: String) { _plusPromoWelcomedUids.value += uid }
    private val _plusPromoEndingNoticedUids = MutableStateFlow<Set<String>>(emptySet())
    override val plusPromoEndingNoticedUids: Set<String> get() = _plusPromoEndingNoticedUids.value
    override fun addPlusPromoEndingNoticedUid(uid: String) { _plusPromoEndingNoticedUids.value += uid }
    // [MONEY-FOUNDERS-PLUS-PROMO-001]
    private val _foundersPromoClaimedUids = MutableStateFlow<Set<String>>(emptySet())
    override val foundersPromoClaimedUids: Set<String> get() = _foundersPromoClaimedUids.value
    override fun addFoundersPromoClaimedUid(uid: String) { _foundersPromoClaimedUids.value += uid }
    private val _foundersPromoGrantedUids = MutableStateFlow<Set<String>>(emptySet())
    override val foundersPromoGrantedUids: Set<String> get() = _foundersPromoGrantedUids.value
    override fun addFoundersPromoGrantedUid(uid: String) { _foundersPromoGrantedUids.value += uid }

    private val _autoDetectParking = MutableStateFlow(true)

    private var homeEntryGeneration = scenario?.homeEntryGeneration?.value
    private var firstHomeStartedUids: Set<String> = emptySet()
    private var firstHomeCompletedUids: Set<String> = emptySet()
    private fun syncHomeEntryGeneration() {
        val generation = scenario?.homeEntryGeneration?.value
        if (generation != homeEntryGeneration) {
            homeEntryGeneration = generation
            firstHomeStartedUids = emptySet()
            firstHomeCompletedUids = emptySet()
        }
    }
    override val firstHomeParkingFlowStartedUids: Set<String>
        get() { syncHomeEntryGeneration(); return firstHomeStartedUids }
    override fun startFirstHomeParkingFlow(uid: String) {
        syncHomeEntryGeneration()
        firstHomeStartedUids += uid
    }
    override val firstHomeParkingFlowCompletedUids: Set<String>
        get() { syncHomeEntryGeneration(); return firstHomeCompletedUids }
    override fun completeFirstHomeParkingFlow(uid: String) {
        syncHomeEntryGeneration()
        firstHomeCompletedUids += uid
    }

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

    // [ONBOARDING-FIRST-STEPS-ARE-GUIDED-NOT-TOLD-001] Scenario-aware: flipping `firstStepsPending`
    // in the Dev Catalog replays the checklist on the REAL Home. Default is "already dismissed" so
    // the mock app keeps booting into the steady-state Home the other scenarios are written against.
    private val _firstStepsDone = MutableStateFlow<Set<FirstStep>>(emptySet())
    private val _firstStepsDismissed = MutableStateFlow(true)
    override fun observeFirstStepsDone(): kotlinx.coroutines.flow.Flow<Set<FirstStep>> = _firstStepsDone
    override fun setFirstStepsDone(steps: Set<FirstStep>) { _firstStepsDone.value = steps }
    private val _firstStepsDeferred = kotlinx.coroutines.flow.MutableStateFlow<Set<FirstStep>>(emptySet())
    override fun observeFirstStepsDeferred(): kotlinx.coroutines.flow.Flow<Set<FirstStep>> = _firstStepsDeferred
    override fun setFirstStepsDeferred(steps: Set<FirstStep>) { _firstStepsDeferred.value = steps }
    // [ONB-THE-MAP-ANSWERS-MORE-THAN-SPOTS-001] Empty by default so the mock app shows the
    // provenance line of each complement on its first visit, which is the state worth seeing.
    private val _introducedComplements =
        kotlinx.coroutines.flow.MutableStateFlow<Set<NearbyComplement>>(emptySet())
    override fun observeIntroducedComplements(): kotlinx.coroutines.flow.Flow<Set<NearbyComplement>> =
        _introducedComplements
    override fun setIntroducedComplements(complements: Set<NearbyComplement>) {
        _introducedComplements.value = complements
    }
    override fun observeFirstStepsDismissed(): kotlinx.coroutines.flow.Flow<Boolean> =
        scenario?.firstStepsPending?.map { pending -> !pending } ?: _firstStepsDismissed
    override fun setFirstStepsDismissed(dismissed: Boolean) {
        _firstStepsDismissed.value = dismissed
        if (dismissed) scenario?.firstStepsPending?.value = false
    }

    // [DET-NUDGE-PERSIST-001]
    private val _pendingParkNudge = MutableStateFlow<PendingParkNudge?>(null)
    private val _dissolvedSharedCars = MutableStateFlow<Set<String>>(emptySet())

    /**
     * Scenario-aware, como el resto de estas preferencias: el Dev Catalog enciende
     * «el dueño cerró el grupo» y la pestaña de Vehículos se encuentra la pregunta.
     * [PRIV-A-DELETED-CAR-MUST-NOT-ORPHAN-ITS-GROUP-001]
     */
    override fun observeDissolvedSharedCars(): kotlinx.coroutines.flow.Flow<Set<String>> =
        combine(_dissolvedSharedCars, scenario?.sharedCarDissolved ?: MutableStateFlow(false)) { own, dissolved ->
            if (dissolved) own + MOCK_DISSOLVED_VEHICLE_ID else own
        }
    override fun addDissolvedSharedCar(vehicleId: String) { _dissolvedSharedCars.value += vehicleId }
    override fun clearDissolvedSharedCar(vehicleId: String) { _dissolvedSharedCars.value -= vehicleId }

    override fun observePendingParkNudge(): kotlinx.coroutines.flow.Flow<PendingParkNudge?> = _pendingParkNudge
    override fun setPendingParkNudge(nudge: PendingParkNudge) { _pendingParkNudge.value = nudge }
    override fun clearPendingParkNudge() { _pendingParkNudge.value = null }

    // [SPOT-A-BOARDING-IS-NOT-A-DEPARTURE-001]
    private var _pendingSpotRelease: PendingSpotRelease? = null
    override fun pendingSpotRelease(): PendingSpotRelease? = _pendingSpotRelease
    override fun setPendingSpotRelease(release: PendingSpotRelease) { _pendingSpotRelease = release }
    override fun clearPendingSpotRelease() { _pendingSpotRelease = null }

    // [DET-ASK-STATE-001] Scenario-aware, like the rest of the mock preferences: the Dev Catalog
    // flips `promptOpen` and the real Home renders the question row. The window is stamped at read
    // time so it is always inside the response window (a fixed timestamp would age out mid-demo).
    private val pendingPromptWindow = MutableStateFlow<PendingPromptWindow?>(null)
    override fun pendingPromptWindow() = pendingPromptWindow.value
    private val stillParked = MutableStateFlow<com.rndeveloper.paparcar.domain.detection.StillParkedQuestion?>(null)
    override fun stillParkedQuestion() = stillParked.value
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun observeStillParkedQuestion(): Flow<com.rndeveloper.paparcar.domain.detection.StillParkedQuestion?> =
        scenario?.stillParkedQuestionOpen?.flatMapLatest { open ->
            stillParked.value = if (open) {
                stillParked.value ?: com.rndeveloper.paparcar.domain.detection.StillParkedQuestion(
                    geofenceId = "mock-own-parking-fence",
                    latitude = 40.4236, longitude = -3.7007,
                    shownAtMs = Clock.System.now().toEpochMilliseconds(),
                )
            } else null
            stillParked
        } ?: stillParked
    override fun setStillParkedQuestion(question: com.rndeveloper.paparcar.domain.detection.StillParkedQuestion?) {
        stillParked.value = question
        if (question == null || question.confirmedHereAtMs != null) scenario?.stillParkedQuestionOpen?.value = false
    }
    override fun observePendingPromptWindow(): kotlinx.coroutines.flow.Flow<PendingPromptWindow?> =
        scenario?.promptOpen?.map { open ->
            if (open) {
                val nowMs = Clock.System.now().toEpochMilliseconds()
                PendingPromptWindow(
                    shownAtMs = nowMs,
                    vehicleName = MOCK_PROMPT_VEHICLE,
                    vehicleId = MOCK_PROMPT_VEHICLE_ID,
                    candidate = MOCK_PROMPT_CANDIDATE.copy(timestamp = nowMs),
                    street = MOCK_PROMPT_STREET,
                )
            } else {
                null
            }
        } ?: pendingPromptWindow
    override fun setPendingPromptWindow(window: PendingPromptWindow) { pendingPromptWindow.value = window }
    override fun clearPendingPromptWindow() {
        pendingPromptWindow.value = null
        scenario?.promptOpen?.value = false
    }

    private val _notifyParkingDetected = MutableStateFlow(true)
    override val notifyParkingDetected: Boolean get() = _notifyParkingDetected.value
    override fun setNotifyParkingDetected(enabled: Boolean) { _notifyParkingDetected.value = enabled }

    private val _notifyZoneSpots = MutableStateFlow(true)
    override val notifyZoneSpots: Boolean get() = _notifyZoneSpots.value
    override fun setNotifyZoneSpots(enabled: Boolean) { _notifyZoneSpots.value = enabled }
    override fun observeNotifyZoneSpots(): Flow<Boolean> = _notifyZoneSpots

    private val _notifyFuelPrices = MutableStateFlow(true)
    override val notifyFuelPrices: Boolean get() = _notifyFuelPrices.value
    override fun setNotifyFuelPrices(enabled: Boolean) { _notifyFuelPrices.value = enabled }
    private val _notifyMonthRecap = MutableStateFlow(true)
    override val notifyMonthRecap: Boolean get() = _notifyMonthRecap.value
    override fun setNotifyMonthRecap(enabled: Boolean) { _notifyMonthRecap.value = enabled }

    private var _lastMonthRecap: Int? = null
    override val lastMonthRecapAnnounced: Int? get() = _lastMonthRecap
    override fun setLastMonthRecapAnnounced(yearMonth: Int) { _lastMonthRecap = yearMonth }

    private val _syncRouteGeometry = MutableStateFlow(false)
    override val syncRouteGeometry: Boolean get() = _syncRouteGeometry.value
    override fun setSyncRouteGeometry(enabled: Boolean) { _syncRouteGeometry.value = enabled }

    /** La plaza por la que se dio fe. [SPOT-A-SPACE-THE-COMMUNITY-GAVE-YOU-IS-ONE-YOU-VOUCHED-FOR-001] */
    private var _vouchedSpot: VouchedSpot? = null
    override val vouchedSpot: VouchedSpot? get() = _vouchedSpot
    override fun setVouchedSpot(spot: VouchedSpot?) { _vouchedSpot = spot }

    // [DET-A-SWITCH-THAT-IS-OFF-LEAVES-NO-TRACE-001] Starts null = nothing published yet.
    private val _lastPublishedDetectionState = MutableStateFlow<String?>(null)
    override val lastPublishedDetectionState: String? get() = _lastPublishedDetectionState.value
    override fun setLastPublishedDetectionState(fingerprint: String) {
        _lastPublishedDetectionState.value = fingerprint
    }

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    override val themeMode: ThemeMode get() = _themeMode.value
    override fun setThemeMode(mode: ThemeMode) { _themeMode.value = mode }

    // ⛔ El MISMO default que producción, no `false`.
    // [STATS-A-VOLUME-BURNED-IS-QUOTED-IN-LITRES-EVERYWHERE-001]
    //
    // Un fake que arranca en métrico pase lo que pase deja el carril imperial entero —mpg, millas,
    // galones y libras— invisible en el flavor mock, que es donde se prueban las pantallas: en el
    // emulador en-US la ficha del coche pedía «Consumption (l/100 km)», y eso NO es lo que hace la
    // app instalada. Un fake que miente sobre el arranque no simula, esconde.
    //
    // Producción resuelve el default por el país del locale y deja ganar al valor guardado en cuanto
    // el usuario toca el interruptor (`AndroidDataStoreAppPreferences` e `IosAppPreferences`, los dos
    // vía [localePrefersImperialUnits]). Aquí no hay nada guardado, así que el default ES el valor.
    private val _useImperialUnits = MutableStateFlow(localePrefersImperialUnits())
    override val useImperialUnits: Boolean get() = _useImperialUnits.value
    override fun setUseImperialUnits(enabled: Boolean) { _useImperialUnits.value = enabled }

    private val _defaultMapType = MutableStateFlow("NORMAL")
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
