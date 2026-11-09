package com.rndeveloper.paparcar.presentation.app

import com.rndeveloper.paparcar.domain.detection.ParkingStrategyResolver
import com.rndeveloper.paparcar.domain.model.DeviceCapabilities
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.Spot
import com.rndeveloper.paparcar.domain.model.Entitlement
import com.rndeveloper.paparcar.domain.model.SpotStatus
import com.rndeveloper.paparcar.domain.model.Zone
import com.rndeveloper.paparcar.domain.usecase.detection.EvaluateDetectionReliabilityUseCase
import com.rndeveloper.paparcar.domain.usecase.detection.ObserveDetectionReliabilityUseCase
import com.rndeveloper.paparcar.domain.usecase.detection.PublishDetectionStateUseCase
import com.rndeveloper.paparcar.domain.analytics.AdMeasurementConsent
import com.rndeveloper.paparcar.domain.usecase.analytics.SetAdMeasurementConsentUseCase
import com.rndeveloper.paparcar.fakes.FakeAdMeasurementLane
import com.rndeveloper.paparcar.fakes.FakeAnalyticsTracker
import com.rndeveloper.paparcar.fakes.FakeBluetoothScanner
import com.rndeveloper.paparcar.fakes.FakeOemBackgroundReliabilityManager
import com.rndeveloper.paparcar.fakes.FakeUserProfileRepository
import com.rndeveloper.paparcar.domain.connectivity.ConnectivityBannerPhase
import com.rndeveloper.paparcar.domain.connectivity.ConnectivityStatus
import com.rndeveloper.paparcar.domain.usecase.entitlement.ObserveIsPlusUseCase
import com.rndeveloper.paparcar.domain.usecase.zone.ObserveZoneSpotAlertsUseCase
import com.rndeveloper.paparcar.fakes.FakeAppNotificationManager
import com.rndeveloper.paparcar.fakes.FakeAppPreferences
import com.rndeveloper.paparcar.fakes.FakeAuthRepository
import com.rndeveloper.paparcar.fakes.FakeConnectivityObserver
import com.rndeveloper.paparcar.fakes.FakeEntitlementRepository
import com.rndeveloper.paparcar.fakes.FakePermissionManager
import com.rndeveloper.paparcar.fakes.FakeSpotRepository
import com.rndeveloper.paparcar.fakes.FakeUserParkingRepository
import com.rndeveloper.paparcar.fakes.FakeVehicleRepository
import com.rndeveloper.paparcar.fakes.FakeZoneRepository
import com.rndeveloper.paparcar.fakes.FakeZoneWatchRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import com.rndeveloper.paparcar.domain.notification.MapVisibility
import com.rndeveloper.paparcar.domain.notification.NearbySpotAlerts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakePermissions: FakePermissionManager
    private lateinit var fakePrefs: FakeAppPreferences
    private lateinit var fakeConnectivity: FakeConnectivityObserver
    private lateinit var fakeVehicleRepo: FakeVehicleRepository
    private lateinit var fakeZoneRepo: FakeZoneRepository
    private lateinit var fakeParkingRepo: FakeUserParkingRepository
    private lateinit var zoneAlerts: ObserveZoneSpotAlertsUseCase
    /** Las dos entradas del carril de zona, en campos para poder CONDUCIRLO desde un test.
     *  [ALERT-A-PROVISIONAL-SPOT-IS-PUSHED-BUT-NOT-SHOWN-001] Se creaban inline en el setUp, así
     *  que el stream era inalcanzable y su entrega no tenía testigo a este nivel. */
    private lateinit var spotRepoForAlerts: FakeSpotRepository
    private lateinit var entitlementRepo: FakeEntitlementRepository
    private lateinit var mapVisibility: MapVisibility
    /** El segundo emisor de la MISMA ranura de aviso.
     *  [ZONE-AN-ALERT-NEAR-YOU-IS-NOT-AN-ALERT-IN-YOUR-ZONE-001] */
    private lateinit var nearbyAlerts: NearbySpotAlerts
    private lateinit var fakeNotifications: FakeAppNotificationManager
    // [DET-A-SWITCH-THAT-IS-OFF-LEAVES-NO-TRACE-001] Signed out and with nothing granted, so the
    // publisher is inert here: its own contract is covered by PublishDetectionStateUseCaseTest.
    private lateinit var detectionPublisher: PublishDetectionStateUseCase

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakePermissions = FakePermissionManager()
        fakePrefs = FakeAppPreferences()
        fakeConnectivity = FakeConnectivityObserver()
        fakeVehicleRepo = FakeVehicleRepository()
        fakeZoneRepo = FakeZoneRepository()
        fakeParkingRepo = FakeUserParkingRepository()
        // Quiet by default: no zones subscribed, entitlement FREE — the alert stream stays empty
        // so the connectivity/permission tests above it are undisturbed. [MONEY-001]
        spotRepoForAlerts = FakeSpotRepository()
        entitlementRepo = FakeEntitlementRepository()
        zoneAlerts = ObserveZoneSpotAlertsUseCase(
            zoneRepository = fakeZoneRepo,
            spotRepository = spotRepoForAlerts,
            watchRepository = FakeZoneWatchRepository(),
            appPreferences = fakePrefs,
            authRepository = FakeAuthRepository(),
            // FREE, y con el gate restaurado eso vuelve a ser lo que mantiene el stream vacio aqui
            // — el comentario de arriba lo prometia y del 21-09 al 27-09 no era verdad.
            // [ZONE-A-PAID-ALERT-MUST-NOT-BE-DOCUMENTED-AS-FREE-001]
            observeIsPlus = ObserveIsPlusUseCase(entitlementRepo),
        )
        // La señal que decide el canal del aviso: por defecto el mapa NO está delante, que es
        // como corrían estos tests antes de que hubiera canales.
        // [ZONE-AN-ALERT-IS-DELIVERED-WHERE-YOU-CAN-SEE-IT-001]
        mapVisibility = MapVisibility()
        // El carril de cerca de ti: mudo salvo que un test anuncie por él.
        // [ZONE-AN-ALERT-NEAR-YOU-IS-NOT-AN-ALERT-IN-YOUR-ZONE-001]
        nearbyAlerts = NearbySpotAlerts()
        fakeNotifications = FakeAppNotificationManager()
        detectionPublisher = PublishDetectionStateUseCase(
            permissionManager = fakePermissions,
            appPreferences = fakePrefs,
            observeDetectionReliability = ObserveDetectionReliabilityUseCase(
                vehicleRepository = fakeVehicleRepo,
                permissionManager = fakePermissions,
                oemBackgroundReliabilityManager = FakeOemBackgroundReliabilityManager(),
                strategyResolver = ParkingStrategyResolver(fakeVehicleRepo, FakeBluetoothScanner()),
                evaluateDetectionReliability = EvaluateDetectionReliabilityUseCase(
                    capabilities = DeviceCapabilities(supportsBtStrategy = true, supportsBatteryExemption = true),
                ),
            ),
            userProfileRepository = FakeUserProfileRepository(),
            authRepository = FakeAuthRepository(),
            analyticsTracker = FakeAnalyticsTracker(),
            platform = "android",
            appVersionName = "1.2.0",
        )
    }


    /**
     * La ÚNICA construcción del ViewModel en este fichero.
     *
     * ⛔ Había **22 llamadas posicionales** idénticas salvo el observador de conectividad, así que
     * añadir una dependencia rompia el fichero entero y en el sitio equivocado: los errores salían como
     * «tipo incorrecto» en argumentos que nadie había tocado. Con nombres, el orden deja de importar
     * — que es la misma leccion que el registro de Koin ya llevaba escrita para `HomeViewModel`.
     * [ZONE-AN-ALERT-NEAR-YOU-IS-NOT-AN-ALERT-IN-YOUR-ZONE-001]
     */
    private fun viewModel(connectivity: FakeConnectivityObserver = fakeConnectivity) = AppViewModel(
        permissionManager = fakePermissions,
        appPreferences = fakePrefs,
        connectivityObserver = connectivity,
        vehicleRepository = fakeVehicleRepo,
        zoneRepository = fakeZoneRepo,
        userParkingRepository = fakeParkingRepo,
        observeZoneSpotAlerts = zoneAlerts,
        mapVisibility = mapVisibility,
        nearbySpotAlerts = nearbyAlerts,
        appNotificationManager = fakeNotifications,
        publishDetectionState = detectionPublisher,
        adMeasurementLane = adLane,
        setAdMeasurementConsent = SetAdMeasurementConsentUseCase(fakePrefs, adLane),
    )

    /** Available by default: the question is owed unless a test says otherwise. */
    private var adLane = FakeAdMeasurementLane()

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Init ─────────────────────────────────────────────────────────────────

    @Test
    fun `initial state is all false when no permissions granted`() {
        val vm = viewModel()

        assertFalse(vm.state.value.permissionsGranted)
        assertFalse(vm.state.value.locationServicesEnabled)
        assertFalse(vm.state.value.isFullyOperational)
    }

    @Test
    fun `initial state reflects current permission state synchronously`() {
        fakePermissions.emit(FakePermissionManager.allGranted())
        val vm = viewModel()

        assertTrue(vm.state.value.permissionsGranted)
        assertTrue(vm.state.value.locationServicesEnabled)
        assertTrue(vm.state.value.isFullyOperational)
    }

    @Test
    fun `initial state reflects permissions-only no GPS`() {
        fakePermissions.emit(FakePermissionManager.permissionsOnlyNoGps())
        val vm = viewModel()

        assertTrue(vm.state.value.permissionsGranted)
        assertFalse(vm.state.value.locationServicesEnabled)
        assertFalse(vm.state.value.isFullyOperational)
    }

    // ── isDetectionReady (GPS-accuracy disclaimer trigger) ───────────────────

    @Test
    fun `isDetectionReady false when only CORE is granted`() {
        // Fully operational for the map ≠ detection ready: the disclaimer talks about detection,
        // so it must wait for the producer grant. [ONB-PERMISSIONS-ASK-AT-THE-MOMENT-OF-VALUE-001]
        fakePermissions.emit(
            com.rndeveloper.paparcar.domain.permissions.AppPermissionState(
                hasLocationPermission = true,
                isLocationServicesEnabled = true,
            ),
        )
        val vm = viewModel()

        assertTrue(vm.state.value.isFullyOperational)
        assertFalse(vm.state.value.isDetectionReady)
        assertFalse(vm.state.value.showGpsAccuracyDisclaimer)
    }

    @Test
    fun `isDetectionReady true when producer permissions complete`() {
        fakePermissions.emit(FakePermissionManager.allGranted())
        val vm = viewModel()

        assertTrue(vm.state.value.isDetectionReady)
    }

    // ── State updates ─────────────────────────────────────────────────────────

    @Test
    fun `state updates when permissions are granted after creation`() = runTest {
        val vm = viewModel()
        assertFalse(vm.state.value.isFullyOperational)

        fakePermissions.emit(FakePermissionManager.allGranted())

        assertTrue(vm.state.value.permissionsGranted)
        assertTrue(vm.state.value.locationServicesEnabled)
        assertTrue(vm.state.value.isFullyOperational)
    }

    @Test
    fun `state updates when permissions are revoked mid-session`() = runTest {
        fakePermissions.emit(FakePermissionManager.allGranted())
        val vm = viewModel()
        assertTrue(vm.state.value.isFullyOperational)

        fakePermissions.emit(FakePermissionManager.allDenied())

        assertFalse(vm.state.value.permissionsGranted)
        assertFalse(vm.state.value.isFullyOperational)
    }

    @Test
    fun `state updates when GPS is toggled off with permissions kept`() = runTest {
        fakePermissions.emit(FakePermissionManager.allGranted())
        val vm = viewModel()
        assertTrue(vm.state.value.isFullyOperational)

        fakePermissions.emit(FakePermissionManager.permissionsOnlyNoGps())

        assertTrue(vm.state.value.permissionsGranted)
        assertFalse(vm.state.value.locationServicesEnabled)
        assertFalse(vm.state.value.isFullyOperational)
    }

    // ── Connectivity banner ─────────────────────────────────────────────────────

    @Test
    fun `cold start online keeps banner hidden`() {
        val vm = viewModel()

        assertEquals(ConnectivityBannerPhase.Hidden, vm.state.value.connectivityBanner)
    }

    @Test
    fun `cold start offline shows offline banner synchronously`() {
        val offlineConnectivity = FakeConnectivityObserver(ConnectivityStatus.Offline)
        val vm = viewModel(offlineConnectivity)

        assertEquals(ConnectivityBannerPhase.Offline, vm.state.value.connectivityBanner)
    }

    @Test
    fun `going offline shows offline banner`() = runTest(testDispatcher) {
        val vm = viewModel()
        assertEquals(ConnectivityBannerPhase.Hidden, vm.state.value.connectivityBanner)

        fakeConnectivity.emit(ConnectivityStatus.Offline)

        assertEquals(ConnectivityBannerPhase.Offline, vm.state.value.connectivityBanner)
    }

    @Test
    fun `reconnect shows restored banner then auto-hides`() = runTest(testDispatcher) {
        val vm = viewModel()
        fakeConnectivity.emit(ConnectivityStatus.Offline)

        fakeConnectivity.emit(ConnectivityStatus.Online)
        assertEquals(ConnectivityBannerPhase.Restored, vm.state.value.connectivityBanner)

        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(ConnectivityBannerPhase.Hidden, vm.state.value.connectivityBanner)
    }

    @Test
    fun `going offline again before auto-hide keeps offline banner`() = runTest(testDispatcher) {
        val vm = viewModel()
        fakeConnectivity.emit(ConnectivityStatus.Offline)
        fakeConnectivity.emit(ConnectivityStatus.Online) // Restored, hide scheduled

        fakeConnectivity.emit(ConnectivityStatus.Offline) // back offline before the hide fires

        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(ConnectivityBannerPhase.Offline, vm.state.value.connectivityBanner)
    }

    @Test
    fun `drains pending vehicles on online cold start`() {
        viewModel()

        assertEquals(1, fakeVehicleRepo.pushPendingCallCount) // online → outbox drained once at init
    }

    @Test
    fun `does not drain pending vehicles on offline cold start`() {
        val offline = FakeConnectivityObserver(ConnectivityStatus.Offline)
        viewModel(offline)

        assertEquals(0, fakeVehicleRepo.pushPendingCallCount) // offline → nothing to push yet
    }

    @Test
    fun `drains pending vehicles on reconnect`() = runTest(testDispatcher) {
        val offline = FakeConnectivityObserver(ConnectivityStatus.Offline)
        viewModel(offline)
        assertEquals(0, fakeVehicleRepo.pushPendingCallCount)

        offline.emit(ConnectivityStatus.Online) // reconnect → push the offline edits to the cloud

        assertEquals(1, fakeVehicleRepo.pushPendingCallCount)
        assertEquals(1, fakeZoneRepo.pushPendingCallCount) // zones drain on the same trigger
    }

    // ── Intents ───────────────────────────────────────────────────────────────

    @Test
    fun `MarkOnboardingCompleted calls setOnboardingCompleted once`() {
        val vm = viewModel()
        assertEquals(0, fakePrefs.setOnboardingCompletedCount)

        vm.handleIntent(AppIntent.MarkOnboardingCompleted)

        assertEquals(1, fakePrefs.setOnboardingCompletedCount)
        assertTrue(fakePrefs.isOnboardingCompleted)
    }

    @Test
    fun `MarkOnboardingCompleted is idempotent`() {
        val vm = viewModel()

        vm.handleIntent(AppIntent.MarkOnboardingCompleted)
        vm.handleIntent(AppIntent.MarkOnboardingCompleted)

        assertEquals(2, fakePrefs.setOnboardingCompletedCount)
        assertTrue(fakePrefs.isOnboardingCompleted)
    }

    // ── Ad-measurement consent [MKT-AD-MEASUREMENT-ASKS-ITS-OWN-CONSENT-001] ──────

    @Test
    fun `should_askAdMeasurement_when_laneExistsAndNeverAnswered`() {
        val vm = viewModel()

        // True at construction, not after an emission: the root decides on its first composition.
        assertTrue(vm.state.value.askAdMeasurementConsent)
    }

    @Test
    fun `should_notAskAdMeasurement_when_thisBuildHasNoAdLane`() {
        // iOS and the mock flavor: nothing would be sent, so nothing is asked.
        adLane = FakeAdMeasurementLane(isAvailable = false)

        assertFalse(viewModel().state.value.askAdMeasurementConsent)
    }

    @Test
    fun `should_grantAndSwitchTheLaneOn_when_userAllows`() {
        val vm = viewModel()

        vm.handleIntent(AppIntent.AnswerAdMeasurementConsent(granted = true))

        assertFalse(vm.state.value.askAdMeasurementConsent)
        assertEquals(AdMeasurementConsent.GRANTED, fakePrefs.adMeasurementConsent)
        assertEquals(listOf(AdMeasurementConsent.GRANTED), adLane.received)
    }

    @Test
    fun `should_neverAskAgain_when_userRefused`() {
        // A no is an answer, not a postponement: asking again after it is nagging.
        viewModel().handleIntent(AppIntent.AnswerAdMeasurementConsent(granted = false))

        val relaunched = viewModel()

        assertEquals(AdMeasurementConsent.DENIED, fakePrefs.adMeasurementConsent)
        assertFalse(relaunched.state.value.askAdMeasurementConsent)
    }

    @Test
    fun `should_closeTheQuestion_when_theLaneFailsToSwitch`() {
        // The dialog has no outside-tap exit, so a failure must not trap the user in it.
        adLane = FakeAdMeasurementLane().apply { throwOnChange = true }
        val vm = viewModel()

        vm.handleIntent(AppIntent.AnswerAdMeasurementConsent(granted = true))

        assertFalse(vm.state.value.askAdMeasurementConsent)
    }

    // ── El carril de «cerca de ti» ────────────────────────────────────────────
    // [ZONE-AN-ALERT-NEAR-YOU-IS-NOT-AN-ALERT-IN-YOUR-ZONE-001]

    @Test
    fun `should show the banner through the same slot when the nearby lane announces`() = runTest(testDispatcher) {
        val vm = viewModel()
        runCurrent()

        nearbyAlerts.announce(nearbySpot)
        runCurrent()

        val cue = vm.state.value.spotAlert
        assertEquals("s-near", cue?.spotId)
        assertEquals(SpotAlertOrigin.NearYou, cue?.origin)
    }

    @Test
    fun `should never reach the system tray from the nearby lane even with the map away`() = runTest(testDispatcher) {
        // ⛔ Este carril es in-app por construcción: sólo decide con el mapa delante, y una
        // notificación del sistema prometería vigilancia con la app cerrada — que es exactamente lo
        // que NO hace, y lo que el carril de zona cobra por hacer.
        mapVisibility.set(false)
        val vm = viewModel()
        runCurrent()

        nearbyAlerts.announce(nearbySpot)
        runCurrent()

        assertTrue(fakeNotifications.zoneSpotAlerts.isEmpty(), "el carril gratis no manda notificaciones")
        assertEquals(SpotAlertOrigin.NearYou, vm.state.value.spotAlert?.origin)
    }

    /** Una zona con su aviso encendido, que es la condición para que el stream de zona hable. */
    private val zoneWithAlerts = Zone(
        id = "z-1", userId = "u", name = "Casa",
        lat = 40.0, lon = -3.0, iconKey = "home", createdAt = 0L,
        radiusMeters = 250f, notifyOnSpot = true,
    )

    private val nearbySpot = Spot(
        id = "s-near",
        location = GpsPoint(40.0, -3.0, accuracy = 5f, timestamp = 1_000L, speed = 0f),
        reportedBy = "someone-else",
        expiresAt = 0L,
    )


    @Test
    fun `should carry the doubt to the banner when the departure was only deduced`() = runTest(testDispatcher) {
        // [ALERT-A-PROVISIONAL-SPOT-IS-PUSHED-BUT-NOT-SHOWN-001] La duda tiene que llegar al cue: es el
        // UNICO aviso que esa plaza va a tener, porque anunciarla consume la memoria de «ya contada» y
        // su promoción a CONFIRMED ya no vuelve a hablar.
        val vm = viewModel()
        runCurrent()

        nearbyAlerts.announce(nearbySpot.copy(status = SpotStatus.PROVISIONAL))
        runCurrent()

        assertEquals(true, vm.state.value.spotAlert?.unconfirmed)
    }

    @Test
    fun `should tell the notification the spot is not confirmed when the map is away`() = runTest(testDispatcher) {
        // ⛔ Este call site OMITÍA `isProvisional`, cuyo default es `false`: inofensivo mientras el
        // stream vetaba las deducidas, y una promesa falsa en el instante en que dejó de vetarlas.
        mapVisibility.set(false)
        fakeZoneRepo.zones = listOf(zoneWithAlerts)
        entitlementRepo.emitEntitlement(Entitlement.PLUS)
        val vm = viewModel()
        runCurrent()

        spotRepoForAlerts.spots = listOf(nearbySpot.copy(id = "s-deduced", status = SpotStatus.PROVISIONAL))
        runCurrent()

        assertEquals(
            listOf("Casa" to true),
            fakeNotifications.zoneSpotAlertsWithDoubt,
            "la tarjeta del sistema tiene que saber que no está confirmada",
        )
        assertEquals(null, vm.state.value.spotAlert, "con el mapa fuera, el banner no habla")
    }

}
