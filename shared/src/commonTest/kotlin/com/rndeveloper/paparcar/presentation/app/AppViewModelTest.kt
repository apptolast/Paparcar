package com.rndeveloper.paparcar.presentation.app

import com.rndeveloper.paparcar.domain.detection.ParkingStrategyResolver
import com.rndeveloper.paparcar.domain.model.DeviceCapabilities
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.Spot
import com.rndeveloper.paparcar.domain.model.Entitlement
import com.rndeveloper.paparcar.domain.model.SpotStatus
import com.rndeveloper.paparcar.domain.model.Zone
import com.rndeveloper.paparcar.domain.repository.EntitlementRepository
import com.rndeveloper.paparcar.domain.repository.FoundersClaimOutcome
import com.rndeveloper.paparcar.domain.repository.FoundersPromoRepository
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
import com.rndeveloper.paparcar.domain.usecase.entitlement.ClaimFoundersPromoUseCase
import com.rndeveloper.paparcar.domain.usecase.entitlement.ObservePlusGrantUseCase
import com.rndeveloper.paparcar.fakes.data.repository.FakeFoundersPromoRepository
import com.rndeveloper.paparcar.domain.usecase.zone.ObserveZoneSpotAlertsUseCase
import com.rndeveloper.paparcar.fakes.FakeAppNotificationManager
import com.rndeveloper.paparcar.fakes.FakeAppPreferences
import com.apptolast.baselogin.domain.model.AuthState
import com.apptolast.baselogin.domain.model.UserSession
import com.rndeveloper.paparcar.domain.entitlement.PLUS_PROMO_ENDING_WINDOW_DAYS
import com.rndeveloper.paparcar.domain.entitlement.PlusMoment
import com.rndeveloper.paparcar.domain.model.PlusGrant
import com.rndeveloper.paparcar.domain.model.PlusOrigin
import com.rndeveloper.paparcar.fakes.FakeAuthRepository
import com.rndeveloper.paparcar.fakes.FakeConnectivityObserver
import com.rndeveloper.paparcar.fakes.FakeEntitlementRepository
import com.rndeveloper.paparcar.fakes.FakePermissionManager
import com.rndeveloper.paparcar.fakes.FakeSpotRepository
import com.rndeveloper.paparcar.fakes.FakeUserParkingRepository
import com.rndeveloper.paparcar.fakes.FakeVehicleRepository
import com.rndeveloper.paparcar.fakes.FakeZoneRepository
import com.rndeveloper.paparcar.fakes.FakeZoneWatchRepository
import kotlinx.coroutines.CompletableDeferred
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
import kotlin.test.assertNull
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
    private fun viewModel(
        connectivity: FakeConnectivityObserver = fakeConnectivity,
        foundersPromo: FoundersPromoRepository = foundersPromoRepo,
        entitlement: EntitlementRepository = entitlementRepo,
    ) = AppViewModel(
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
        observePlusGrant = ObservePlusGrantUseCase(entitlement),
        claimFoundersPromo = ClaimFoundersPromoUseCase(foundersPromo, fakePrefs, entitlement),
        authRepository = fakeAuth,
    )

    /** [MONEY-FOUNDERS-PLUS-PROMO-001] El timbre de la promo, sin red. */
    private val foundersPromoRepo = FakeFoundersPromoRepository()

    /**
     * Esta cuenta pasó por la CAMPAÑA. [MONEY-FOUNDERS-PLUS-PROMO-001]
     *
     * ⛔ Sin esta prueba la bienvenida no sale, y es justo lo que debe pasar: un grant
     * `PROMOTIONAL` concedido a mano en el panel de RevenueCat no es una de las 2.000, y leía
     * «eres uno de los 2.000 primeros». Los tests que SÍ esperan la felicitación la piden
     * explícitamente, para que el gate se vea en cada uno en vez de vivir en el `setUp`.
     */
    private fun markAsFounder(uid: String = UID) {
        fakePrefs.foundersPromoGrantedUidsSet += uid
    }

    /** Firmado por defecto: el momento de Plus se promete POR CUENTA, así que un test que no
     *  nombre al usuario no puede medir «una sola vez». [MONEY-FOUNDERS-WELCOME-001] */
    private val fakeAuth = FakeAuthRepository(
        initialSession = UserSession(userId = UID, email = null, displayName = null, photoUrl = null),
    )

    /** Available by default: the question is owed unless a test says otherwise. */
    private var adLane = FakeAdMeasurementLane()

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun should_waitForMatchingSession_when_homeArrivesBeforeAuthObservation() = runTest(testDispatcher) {
        fakeAuth.emitState(AuthState.Unauthenticated)
        val vm = viewModel()

        vm.handleIntent(AppIntent.ReachedHome(UID))
        runCurrent()
        assertTrue(foundersPromoRepo.claimedUids.isEmpty())

        val session = UserSession(userId = UID, email = null, displayName = null, photoUrl = null)
        fakeAuth.emitState(AuthState.Authenticated(session))
        runCurrent()
        assertEquals(listOf(UID), foundersPromoRepo.claimedUids)
    }

    @Test
    fun should_retryClaim_when_homeWasReachedOfflineAndConnectivityReturns() = runTest(testDispatcher) {
        fakeConnectivity.emit(ConnectivityStatus.Offline)
        val vm = viewModel()
        vm.handleIntent(AppIntent.ReachedHome(UID))
        runCurrent()
        assertTrue(foundersPromoRepo.claimedUids.isEmpty())

        fakeConnectivity.emit(ConnectivityStatus.Online)
        runCurrent()
        assertEquals(listOf(UID), foundersPromoRepo.claimedUids)
    }

    @Test
    fun should_welcomeOnce_when_homeClaimIsConfirmedByRevenueCat() = runTest(testDispatcher) {
        val receipt = CompletableDeferred<FoundersClaimOutcome>()
        val claims = mutableListOf<String>()
        val foundersPromo = object : FoundersPromoRepository {
            override suspend fun claim(uid: String): Result<FoundersClaimOutcome> {
                claims += uid
                return Result.success(receipt.await())
            }
        }
        val vm = viewModel(foundersPromo = foundersPromo)
        runCurrent()

        assertTrue(claims.isEmpty())
        assertTrue(entitlementRepo.refreshedUserIds.isEmpty())
        assertNull(vm.state.value.plusMoment)

        vm.handleIntent(AppIntent.ReachedHome(UID))
        vm.handleIntent(AppIntent.ReachedHome(UID))
        runCurrent()

        // Dos entradas a Home mientras el recibo no llega son UNA sola solicitud, y todavia no se
        // le pregunta nada a la tienda: no hay nada que refrescar hasta que el servidor contesta.
        assertEquals(listOf(UID), claims)
        assertTrue(entitlementRepo.refreshedUserIds.isEmpty())
        assertNull(vm.state.value.plusMoment)

        val expiry = inDays(90)
        receipt.complete(FoundersClaimOutcome.Granted(expiry))
        runCurrent()

        // El recibo dispara UNA lectura fresca. Que esa lectura devuelva de verdad un grant
        // PROMOTIONAL lo prueba `RevenueCatEntitlementRepositoryImplTest`, en SU capa: aqui el
        // sujeto es el cableado -- pedir, refrescar, felicitar una sola vez.
        assertEquals(listOf(UID), entitlementRepo.refreshedUserIds)
        entitlementRepo.emitGrant(PlusGrant(PlusOrigin.PROMOTIONAL, expiry))
        runCurrent()

        val welcome = PlusMoment.FoundersWelcome(expiry)
        assertEquals(welcome, vm.state.value.plusMoment)

        vm.handleIntent(AppIntent.DismissPlusMoment(welcome))
        vm.handleIntent(AppIntent.ReachedHome(UID))
        runCurrent()

        assertNull(vm.state.value.plusMoment)
        assertEquals(listOf(UID), claims)
        assertEquals(listOf(UID), entitlementRepo.refreshedUserIds)
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
        // [BATT-002] "El mapa fuera" es OTRA pestaña, no la app en segundo plano — el listener
        // sigue vivo, sólo cambia el canal.
        vm.handleIntent(AppIntent.SetAppForeground(true))

        spotRepoForAlerts.spots = listOf(nearbySpot.copy(id = "s-deduced", status = SpotStatus.PROVISIONAL))
        runCurrent()

        assertEquals(
            listOf("Casa" to true),
            fakeNotifications.zoneSpotAlertsWithDoubt,
            "la tarjeta del sistema tiene que saber que no está confirmada",
        )
        assertEquals(null, vm.state.value.spotAlert, "con el mapa fuera, el banner no habla")
    }

    // ── [BATT-002] El listener de zona no sobrevive a la app en segundo plano ──

    @Test
    fun `should_stayQuiet_when_theAppIsNeverReportedForeground`() = runTest(testDispatcher) {
        // Sin SetAppForeground(true) — el estado por defecto, el mismo con el que arranca
        // cualquier proceso antes de que la raíz componga su primer frame.
        fakeZoneRepo.zones = listOf(zoneWithAlerts)
        entitlementRepo.emitEntitlement(Entitlement.PLUS)
        val vm = viewModel()
        runCurrent()

        spotRepoForAlerts.spots = listOf(nearbySpot.copy(id = "s-new"))
        runCurrent()

        assertTrue(fakeNotifications.zoneSpotAlerts.isEmpty(), "sin foreground, el listener no debe estar suscrito")
        assertEquals(null, vm.state.value.spotAlert)
    }

    @Test
    fun `should_deliverTheZoneAlert_when_theAppGoesBackgroundThenForegroundAgain`() = runTest(testDispatcher) {
        // El listener se cae al fondo y resucita al volver. Nada se avisa MIENTRAS nadie mira — y
        // nada se pierde para siempre: lo que llegó en el hueco se cuenta en cuanto el listener
        // vuelve, porque el dedupe (`watchRepository.alertedSpotIds`) es durable, no de memoria de
        // esta suscripción. [BATT-002]
        fakeZoneRepo.zones = listOf(zoneWithAlerts)
        entitlementRepo.emitEntitlement(Entitlement.PLUS)
        val vm = viewModel()
        runCurrent()

        // Primer foreground, sin plazas todavía: toma el baseline de la zona (vacío).
        vm.handleIntent(AppIntent.SetAppForeground(true))
        runCurrent()
        vm.handleIntent(AppIntent.SetAppForeground(false))
        runCurrent()

        // Llega mientras el proceso está vivo pero nadie lo mira — el listener está desuscrito.
        spotRepoForAlerts.spots = listOf(nearbySpot.copy(id = "s-while-backgrounded"))
        runCurrent()
        assertTrue(fakeNotifications.zoneSpotAlerts.isEmpty(), "en segundo plano no debe sonar")

        vm.handleIntent(AppIntent.SetAppForeground(true))
        runCurrent()

        assertEquals(
            listOf("Casa"),
            fakeNotifications.zoneSpotAlerts,
            "al volver a foreground, lo que llegó en el hueco se cuenta — no se pierde",
        )
    }


    // ── El Plus de regalo: la bienvenida, el aviso, y una sola vez ────────────
    // [MONEY-FOUNDERS-WELCOME-001]

    /**
     * ⛔ El caso que hace que esto viva en la raíz y no en el arranque: el grant llega DESPUÉS.
     * La promo se concede tras el registro, así que en una cuenta nueva lo normal es que el
     * entitlement caiga con el usuario ya mirando el mapa. Un chequeo de primer arranque no lo
     * vería nunca.
     */
    @Test
    fun `should_celebrateTheGiftedPlus_when_theGrantLandsAfterStartup`() = runTest(testDispatcher) {
        markAsFounder()
        val vm = viewModel()
        // Contestada de entrada: el orden entre las dos superficies tiene su propio test abajo, y
        // dejarla abierta aquí mediría eso en vez de la llegada tardía del grant.
        vm.handleIntent(AppIntent.AnswerAdMeasurementConsent(granted = false))
        runCurrent()
        assertNull(vm.state.value.plusMoment, "sin grant no hay nada que celebrar")

        entitlementRepo.emitGrant(PlusGrant(PlusOrigin.PROMOTIONAL, inDays(90)))
        runCurrent()

        assertEquals(PlusMoment.FoundersWelcome(inDays(90)), vm.state.value.plusMoment)
        assertEquals(
            vm.state.value.plusMoment,
            vm.state.value.plusMomentToShow,
            "sin nada más en pantalla, el veredicto y lo que se enseña son lo mismo",
        )
    }

    @Test
    fun `should_sayNothing_when_thePlusWasBought`() = runTest(testDispatcher) {
        val vm = viewModel()
        entitlementRepo.emitGrant(PlusGrant(PlusOrigin.PURCHASED, null))
        runCurrent()

        assertNull(vm.state.value.plusMoment)
    }

    /** La mitad «una sola vez» del invariante: se apunta al cerrarlo, y el veredicto deja de
     *  darlo en cuanto lo relee. */
    @Test
    fun `should_rememberTheWelcome_when_theUserClosesIt`() = runTest(testDispatcher) {
        markAsFounder()
        val vm = viewModel()
        entitlementRepo.emitGrant(PlusGrant(PlusOrigin.PROMOTIONAL, inDays(90)))
        runCurrent()
        val moment = vm.state.value.plusMoment!!

        vm.handleIntent(AppIntent.DismissPlusMoment(moment))
        runCurrent()

        assertNull(vm.state.value.plusMoment, "cerrado = fuera de pantalla")
        assertEquals(setOf(UID), fakePrefs.plusPromoWelcomedUidsSet, "y apuntado, para que no vuelva")
        assertTrue(
            fakePrefs.plusPromoEndingNoticedUidsSet.isEmpty(),
            "⛔ sólo se apunta la memoria del momento que se enseñó: el aviso de fin sigue debiéndose",
        )
    }

    /** Y no vuelve en el arranque siguiente, que es donde un flag en memoria habría fallado. */
    @Test
    fun `should_notCelebrateAgain_when_theAccountWasAlreadyWelcomed`() = runTest(testDispatcher) {
        markAsFounder()
        fakePrefs.plusPromoWelcomedUidsSet = setOf(UID)
        val vm = viewModel()
        entitlementRepo.emitGrant(PlusGrant(PlusOrigin.PROMOTIONAL, inDays(90)))
        runCurrent()

        assertNull(vm.state.value.plusMoment)
    }

    @Test
    fun `should_warnAboutTheEnding_when_theGiftedPlusEntersItsLastWeek`() = runTest(testDispatcher) {
        fakePrefs.plusPromoWelcomedUidsSet = setOf(UID)
        val vm = viewModel()
        val endsAt = inDays(PLUS_PROMO_ENDING_WINDOW_DAYS - 1)
        entitlementRepo.emitGrant(PlusGrant(PlusOrigin.PROMOTIONAL, endsAt))
        runCurrent()

        assertEquals(PlusMoment.PromoEnding(endsAt), vm.state.value.plusMoment)

        vm.handleIntent(AppIntent.DismissPlusMoment(vm.state.value.plusMoment!!))
        runCurrent()
        assertEquals(setOf(UID), fakePrefs.plusPromoEndingNoticedUidsSet)
    }

    /**
     * ⛔ Nunca modal sobre modal, y una cuenta NUEVA es exactamente la que debe las dos cosas: la
     * pregunta de medición de anuncios y la bienvenida. El orden vive en el estado, no en la
     * composición, y por eso tiene testigo aquí.
     */
    @Test
    fun `should_holdTheCelebration_when_theAdMeasurementQuestionIsStillOpen`() = runTest(testDispatcher) {
        markAsFounder()
        val vm = viewModel()
        entitlementRepo.emitGrant(PlusGrant(PlusOrigin.PROMOTIONAL, inDays(90)))
        runCurrent()

        assertTrue(vm.state.value.askAdMeasurementConsent, "la pregunta se debe en este grafo")
        assertNull(vm.state.value.plusMomentToShow, "la noticia espera a que se conteste la pregunta")

        vm.handleIntent(AppIntent.AnswerAdMeasurementConsent(granted = false))
        runCurrent()

        assertEquals(
            PlusMoment.FoundersWelcome(inDays(90)),
            vm.state.value.plusMomentToShow,
            "contestada la pregunta, la celebración sale — no se ha perdido",
        )
    }

    /**
     * Una fecha a N días de AHORA, con el ahora leído UNA vez.
     *
     * ⛔ El ViewModel usa el reloj real (su veredicto es puro y ya tiene su propio test con un
     * `nowMs` fijo), así que la fecha que se emite y la que se comprueba tienen que salir del
     * mismo instante — si no, dos llamadas separadas por un milisegundo devolverían valores
     * distintos y la comparación fallaría por ese milisegundo.
     */
    private val nowBase = kotlin.time.Clock.System.now().toEpochMilliseconds()

    private fun inDays(days: Int): Long = nowBase + days * 24L * 60 * 60 * 1000

    private companion object {
        const val UID = "uid-founder"
    }

}
