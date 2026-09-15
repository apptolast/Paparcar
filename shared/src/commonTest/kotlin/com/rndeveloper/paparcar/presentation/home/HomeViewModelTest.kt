@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.presentation.home

import com.rndeveloper.paparcar.fakes.FakeFuelWatchRepository
import app.cash.turbine.test
import com.swmansion.kmpmaps.core.MapType
import com.rndeveloper.paparcar.domain.connectivity.ConnectivityStatus
import com.rndeveloper.paparcar.presentation.home.model.HomeNearbyLens
import com.rndeveloper.paparcar.domain.detection.ParkingStrategyResolver
import com.rndeveloper.paparcar.domain.error.PaparcarError
import com.rndeveloper.paparcar.domain.detection.StaticDetectionRuntimeState
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import com.rndeveloper.paparcar.domain.model.ParkingReleaseReason
import com.rndeveloper.paparcar.domain.model.Spot
import com.rndeveloper.paparcar.domain.model.SpotVoteOutcome
import com.rndeveloper.paparcar.domain.model.SpotType
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.DeviceCapabilities
import com.rndeveloper.paparcar.domain.usecase.detection.EvaluateDetectionReliabilityUseCase
import com.rndeveloper.paparcar.domain.usecase.detection.ObserveDetectionReadinessUseCase
import com.rndeveloper.paparcar.domain.usecase.detection.ObserveDetectionReliabilityUseCase
import com.rndeveloper.paparcar.domain.usecase.location.GetAddressAndPlaceUseCase
import com.rndeveloper.paparcar.domain.usecase.location.SearchAddressUseCase
import com.rndeveloper.paparcar.domain.usecase.parking.ConfirmParkingUseCase
import com.rndeveloper.paparcar.domain.usecase.parking.ObserveParkedVehiclesUseCase
import com.rndeveloper.paparcar.domain.usecase.parking.ReleaseActiveParkingSessionUseCase
import com.rndeveloper.paparcar.domain.usecase.parking.SaveManualParkingUseCase
import com.rndeveloper.paparcar.domain.usecase.parking.UpdateParkingLocationUseCase
import com.rndeveloper.paparcar.domain.usecase.spot.ObserveNearbySpotsUseCase
import com.rndeveloper.paparcar.domain.usecase.spot.ReportManualSpotUseCase
import com.rndeveloper.paparcar.domain.usecase.spot.ReportSpotReleasedUseCase
import com.rndeveloper.paparcar.domain.usecase.spot.SendSpotSignalUseCase
import com.rndeveloper.paparcar.domain.event.MapFocusEventBus
import com.rndeveloper.paparcar.domain.event.StartAddParkingEventBus
import com.rndeveloper.paparcar.domain.usecase.zone.SaveOrUpdateZoneUseCase
import com.rndeveloper.paparcar.domain.usecase.zone.SaveZoneUseCase
import com.rndeveloper.paparcar.fakes.FakeActivityRecognitionManager
import com.rndeveloper.paparcar.fakes.FakeDepartureEventBus
import com.rndeveloper.paparcar.fakes.FakeAppNotificationManager
import com.rndeveloper.paparcar.domain.onboarding.FirstStep
import com.rndeveloper.paparcar.domain.onboarding.NearbyComplement
import com.rndeveloper.paparcar.fakes.FakeAppPreferences
import com.rndeveloper.paparcar.fakes.FakeAuthRepository
import com.rndeveloper.paparcar.fakes.FakeBluetoothScanner
import com.rndeveloper.paparcar.fakes.FakeConnectivityObserver
import com.rndeveloper.paparcar.fakes.FakeGeocoderDataSource
import com.rndeveloper.paparcar.fakes.FakeGeofenceManager
import com.rndeveloper.paparcar.fakes.FakeOemBackgroundReliabilityManager
import com.rndeveloper.paparcar.fakes.FakeAddressAndPlaceRepository
import com.rndeveloper.paparcar.fakes.FakeLocationDataSource
import com.rndeveloper.paparcar.fakes.FakeParkingEnrichmentScheduler
import com.rndeveloper.paparcar.fakes.FakePermissionManager
import com.rndeveloper.paparcar.fakes.FakeReportSpotScheduler
import com.rndeveloper.paparcar.fakes.FakeSpotRepository
import com.rndeveloper.paparcar.fakes.FakeUserParkingRepository
import com.rndeveloper.paparcar.fakes.FakeVehicleRepository
import com.rndeveloper.paparcar.fakes.FakeZoneRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import com.rndeveloper.paparcar.presentation.home.model.warnsLocationMissing
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import com.rndeveloper.paparcar.domain.detection.PendingPromptWindow
import kotlin.test.assertTrue
import com.rndeveloper.paparcar.domain.fuel.FuelStationsQuery
import com.rndeveloper.paparcar.domain.fuel.FuelStationsResult
import com.rndeveloper.paparcar.domain.fuel.FuelStationsSource
import com.rndeveloper.paparcar.domain.geocoder.GeocoderDataSource
import com.rndeveloper.paparcar.domain.model.AddressInfo
import com.rndeveloper.paparcar.domain.model.SearchResult
import com.rndeveloper.paparcar.domain.usecase.fuel.GetNearbyFuelStationsUseCase
import com.rndeveloper.paparcar.domain.usecase.fuel.ToggleFollowFuelStationUseCase
import com.rndeveloper.paparcar.domain.usecase.fuel.ObserveFollowedFuelStationsUseCase
import com.rndeveloper.paparcar.domain.parkings.PublicParking
import com.rndeveloper.paparcar.domain.parkings.ParkingZoneGrid
import com.rndeveloper.paparcar.domain.parkings.PublicParkingKind
import com.rndeveloper.paparcar.domain.parkings.PublicParkingsQuery
import com.rndeveloper.paparcar.domain.parkings.PublicParkingsResult
import com.rndeveloper.paparcar.domain.parkings.PublicParkingsSource
import com.rndeveloper.paparcar.domain.usecase.parkings.GetNearbyPublicParkingsUseCase

class HomeViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val location = GpsPoint(40.416775, -3.703790, 10f, 0L, 0f)

    private lateinit var permissions: FakePermissionManager
    private lateinit var locationDataSource: FakeLocationDataSource
    private lateinit var spotRepo: FakeSpotRepository
    private lateinit var parkingRepo: FakeUserParkingRepository
    private lateinit var vehicleRepo: FakeVehicleRepository
    private lateinit var authRepo: FakeAuthRepository
    // [ONBOARDING-A-TUTORIAL-IS-REMEMBERED-BY-THE-ACCOUNT-NOT-THE-DEVICE-001] Where the tutorial's
    // dismissal is remembered now that it has to outlive the install.
    private lateinit var profileRepo: com.rndeveloper.paparcar.fakes.FakeUserProfileRepository
    private lateinit var connectivity: FakeConnectivityObserver
    private lateinit var prefs: FakeAppPreferences
    private lateinit var activityRecognition: FakeActivityRecognitionManager
    private lateinit var reportScheduler: FakeReportSpotScheduler
    private lateinit var zoneRepo: FakeZoneRepository
    private lateinit var entitlementRepo: com.rndeveloper.paparcar.fakes.FakeEntitlementRepository
    private lateinit var manualParkingDetection: com.rndeveloper.paparcar.fakes.data.repository.FakeManualParkingDetection
    private lateinit var departureWatchResumer: com.rndeveloper.paparcar.fakes.data.repository.FakeDepartureWatchResumer
    private lateinit var uiLocationLogger: com.rndeveloper.paparcar.fakes.FakeUiLocationLogger
    // [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001] Asked one question: has this phone any
    // bonded device? A test that wants the Bluetooth face sets its pairedDevices.
    private lateinit var bluetoothScanner: FakeBluetoothScanner
    private lateinit var fuelSource: SpyFuelStationsSource
    private lateinit var fuelWatchRepo: FakeFuelWatchRepository
    private lateinit var parkingsSource: SpyPublicParkingsSource
    private lateinit var geocoderFake: FakeGeocoderDataSource
    private lateinit var vm: HomeViewModel

    private fun buildVm(
        initialMapType: String = "NORMAL",
        foreground: Boolean = true,
        showParkingAskBus: com.rndeveloper.paparcar.domain.event.ShowParkingAskEventBus? = null,
        // Default fixture = a Home past its very first entry, so the one-shot map location ask
        // stays quiet and effect tests see only what they trigger. The first-entry tests opt out.
        // [ONB-PERMISSIONS-ASK-AT-THE-MOMENT-OF-VALUE-001]
        locationAlreadyAsked: Boolean = true,
    ): HomeViewModel {
        prefs = FakeAppPreferences(initialDefaultMapType = initialMapType)
        if (locationAlreadyAsked) prefs.setLocationPermissionRequested()
        manualParkingDetection = com.rndeveloper.paparcar.fakes.data.repository.FakeManualParkingDetection()
        departureWatchResumer = com.rndeveloper.paparcar.fakes.data.repository.FakeDepartureWatchResumer()
        val addressAndPlaceRepo = FakeAddressAndPlaceRepository()
        val getAddressAndPlace = GetAddressAndPlaceUseCase(repository = addressAndPlaceRepo)
        geocoderFake = FakeGeocoderDataSource()
        val searchAddress = SearchAddressUseCase(geocoderFake)
        val observeNearbySpots = ObserveNearbySpotsUseCase(spotRepo)
        val sendSpotSignal = SendSpotSignalUseCase(spotRepo)
        val reportSpotReleased = ReportSpotReleasedUseCase(reportScheduler, getAddressAndPlace, FakeAuthRepository(initialSession = null))
        val releaseSession = ReleaseActiveParkingSessionUseCase(
            reportSpotReleased, parkingRepo, FakeGeofenceManager(),
            com.rndeveloper.paparcar.domain.diagnostics.NoOpDetectionEventLogger(),
            zoneRepo,
        )
        val confirmParking = ConfirmParkingUseCase(
            userParkingRepository = parkingRepo,
            vehicleRepository = vehicleRepo,
            zoneRepository = zoneRepo,
            geofenceService = FakeGeofenceManager(),
            enrichmentScheduler = FakeParkingEnrichmentScheduler(),
            authRepository = authRepo,
            config = ParkingDetectionConfig(),
            departureEventBus = FakeDepartureEventBus(),
        )
        val updateParkingLocation = UpdateParkingLocationUseCase(
            userParkingRepository = parkingRepo,
            geofenceService = FakeGeofenceManager(),
            enrichmentScheduler = FakeParkingEnrichmentScheduler(),
            config = ParkingDetectionConfig(),
            departureEventBus = FakeDepartureEventBus(),
        )
        val observeParkedVehicles = ObserveParkedVehiclesUseCase(parkingRepo, vehicleRepo)
        val declareActiveVehicle = com.rndeveloper.paparcar.domain.usecase.vehicle.DeclareActiveVehicleUseCase(
            vehicleRepository = vehicleRepo,
            swapFences = com.rndeveloper.paparcar.domain.usecase.vehicle.SwapActiveVehicleFencesUseCase(
                userParkingRepository = parkingRepo,
                vehicleRepository = vehicleRepo,
                geofenceService = FakeGeofenceManager(),
                config = ParkingDetectionConfig(),
            ),
        )
        val observeDetectionReadiness = ObserveDetectionReadinessUseCase(
            vehicleRepository = vehicleRepo,
            userParkingRepository = parkingRepo,
            permissionManager = permissions,
            detectionRuntime = StaticDetectionRuntimeState(),
            strategyResolver = ParkingStrategyResolver(vehicleRepo, FakeBluetoothScanner(bluetoothEnabled = false)),
            appPreferences = FakeAppPreferences(),
            bluetoothScanner = FakeBluetoothScanner(bluetoothEnabled = false),
        )
        val saveManualParking = SaveManualParkingUseCase(
            confirmParking = confirmParking,
            updateParkingLocation = updateParkingLocation,
            notificationPort = FakeAppNotificationManager(),
            manualParkingDetection = manualParkingDetection,
        )
        val observeDetectionReliability = ObserveDetectionReliabilityUseCase(
            vehicleRepository = vehicleRepo,
            permissionManager = permissions,
            oemBackgroundReliabilityManager = FakeOemBackgroundReliabilityManager(),
            strategyResolver = ParkingStrategyResolver(vehicleRepo, FakeBluetoothScanner(bluetoothEnabled = false)),
            evaluateDetectionReliability = EvaluateDetectionReliabilityUseCase(
                capabilities = DeviceCapabilities(supportsBtStrategy = true, supportsBatteryExemption = true),
            ),
        )
        return HomeViewModel(
            permissionManager = permissions,
            locationDataSource = locationDataSource,
            observeDetectionReadiness = observeDetectionReadiness,
            observeDetectionReliability = observeDetectionReliability,
            detectionRuntime = StaticDetectionRuntimeState(),
            reportManualSpot = ReportManualSpotUseCase(reportSpotReleased, vehicleRepo),
            reportSpotReleased = reportSpotReleased,
            activityRecognitionManager = activityRecognition,
            userParkingRepository = parkingRepo,
            releaseSession = releaseSession,
            saveManualParking = saveManualParking,
            observeParkedVehicles = observeParkedVehicles,
            declareActiveVehicle = declareActiveVehicle,
            appPreferences = prefs,
            clearParkNudge = com.rndeveloper.paparcar.domain.usecase.parking.ClearParkNudgeUseCase(prefs, FakeAppNotificationManager()),
            // [DET-ASK-STATE-001] Read for one number: how long a posted question stays answerable.
            detectionConfig = com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig(),
            // Feature controllers — built with the same fakes the VM uses, mirroring the Koin
            // factories in PresentationModule. [HOMEVM-CTRL-002]
            geocoder = HomeGeocodingController(getAddressAndPlace),
            trip = HomeTripController(
                observeDetectionReadiness = { observeDetectionReadiness() },
                locationDataSource = locationDataSource,
                roadNetworkDataSource = null,
                vehicleRepository = vehicleRepo,
                userParkingRepository = parkingRepo,
                drivingRouteStore = null,
                permissionManager = permissions,
            ),
            search = HomeSearchController(searchAddress),
            spots = HomeSpotsController(permissions, observeNearbySpots),
            sendSpotSignal = sendSpotSignal,
            connectivityObserver = connectivity,
            zoneRepository = zoneRepo,
            saveOrUpdateZone = SaveOrUpdateZoneUseCase(zoneRepo, SaveZoneUseCase(zoneRepo, authRepo)),
            observeIsPlus = com.rndeveloper.paparcar.domain.usecase.entitlement.ObserveIsPlusUseCase(entitlementRepo),
            vehicleRepository = vehicleRepo,
            mapFocusEventBus = MapFocusEventBus(),
            startAddParkingEventBus = StartAddParkingEventBus(),
            manualParkingDetection = manualParkingDetection,
            departureWatchResumer = departureWatchResumer,
            uiLocationLogger = uiLocationLogger,
            bluetoothScanner = bluetoothScanner,
            // [ONBOARDING-A-TUTORIAL-IS-REMEMBERED-BY-THE-ACCOUNT-NOT-THE-DEVICE-001] Closing the
            // checklist writes the device preference AND the account fact.
            setFirstStepsDismissed = com.rndeveloper.paparcar.domain.usecase.user.SetFirstStepsDismissedUseCase(
                appPreferences = prefs,
                authRepository = authRepo,
                userProfileRepository = profileRepo,
            ),
            showParkingAskEventBus = showParkingAskBus,
            // A spy, so the toggle tests can assert the ONE thing that separates an honest empty
            // from a fabricated one: whether a question was actually asked.
            // [FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001]
            getNearbyFuelStations = GetNearbyFuelStationsUseCase(
                stations = fuelSource,
                geocoder = object : GeocoderDataSource {
                    override suspend fun getAddress(lat: Double, lon: Double) =
                        Result.failure<AddressInfo>(IllegalStateException("no geocoder in tests"))
                    override suspend fun searchByName(query: String, maxResults: Int) =
                        Result.success(emptyList<SearchResult>())
                },
            ),
            getNearbyPublicParkings = GetNearbyPublicParkingsUseCase(
                parkings = parkingsSource,
                geocoder = object : GeocoderDataSource {
                    override suspend fun getAddress(lat: Double, lon: Double) =
                        Result.failure<AddressInfo>(IllegalStateException("no geocoder in tests"))
                    override suspend fun searchByName(query: String, maxResults: Int) =
                        Result.success(emptyList<SearchResult>())
                },
            ),
            // [FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001] The two watch-store doors, at
            // the END of the constructor where PresentationModule's positional `get()`s expect them.
            toggleFollowFuelStation = ToggleFollowFuelStationUseCase(fuelWatchRepo),
            observeFollowedFuelStations = ObserveFollowedFuelStationsUseCase(fuelWatchRepo),
        ).also {
            // Default test fixture = map on screen, so the high-accuracy request is armed and location
            // emits reach state. The backgrounded case uses buildVm(foreground = false). [UI-LOC-FOREGROUND-001]
            if (foreground) it.handleIntent(HomeIntent.SetMapForeground(true))
        }
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        permissions = FakePermissionManager()
        locationDataSource = FakeLocationDataSource()
        spotRepo = FakeSpotRepository()
        parkingRepo = FakeUserParkingRepository()
        vehicleRepo = FakeVehicleRepository()
        authRepo = FakeAuthRepository(FakeAuthRepository.authenticatedSession())
        profileRepo = com.rndeveloper.paparcar.fakes.FakeUserProfileRepository()
        connectivity = FakeConnectivityObserver(ConnectivityStatus.Online)
        activityRecognition = FakeActivityRecognitionManager()
        reportScheduler = FakeReportSpotScheduler()
        zoneRepo = FakeZoneRepository()
        entitlementRepo = com.rndeveloper.paparcar.fakes.FakeEntitlementRepository()
        uiLocationLogger = com.rndeveloper.paparcar.fakes.FakeUiLocationLogger()
        bluetoothScanner = FakeBluetoothScanner(bluetoothEnabled = false)
        fuelSource = SpyFuelStationsSource()
        fuelWatchRepo = FakeFuelWatchRepository()
        parkingsSource = SpyPublicParkingsSource()
        vm = buildVm()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Init — map type from preferences ─────────────────────────────────────

    @Test
    fun `should_apply_normal_mapType_from_preferences_by_default`() = runTest {
        assertEquals(MapType.NORMAL, vm.state.value.mapType)
    }

    @Test
    fun `should_apply_hybrid_mapType_from_preferences`() = runTest {
        val vmHybrid = buildVm(initialMapType = "HYBRID")
        assertEquals(MapType.HYBRID, vmHybrid.state.value.mapType)
    }

    // Satellite was retired when the picker became a two-way toggle. [UI-MAP-TYPE-TOGGLE-001]
    @Test
    fun `should_migrate_legacy_satellite_preference_to_hybrid`() = runTest {
        val vmSat = buildVm(initialMapType = "SATELLITE")
        assertEquals(MapType.HYBRID, vmSat.state.value.mapType)
    }

    // Terrain was retired because Google applies JSON styling to the NORMAL map type only, so a
    // preference written by an earlier version is the one way a user could still land on the one
    // tile style where `MapStyles.kt` is silently discarded. [MAP-A-STYLE-THAT-NEVER-RAN-001]
    @Test
    fun `should_migrate_legacy_terrain_preference_to_normal`() = runTest {
        val vmTerrain = buildVm(initialMapType = "TERRAIN")
        assertEquals(MapType.NORMAL, vmTerrain.state.value.mapType)
    }

    // ── Init — permissions + GPS chain ────────────────────────────────────────

    @Test
    fun `should_start_with_hasCorePermissions_false`() = runTest {
        assertEquals(false, vm.state.value.hasCorePermissions)
    }

    @Test
    fun `should_update_hasCorePermissions_true_when_permissions_emitted`() = runTest {
        permissions.emit(FakePermissionManager.allGranted())
        assertEquals(true, vm.state.value.hasCorePermissions)
    }

    @Test
    fun `should_update_userGpsPoint_when_location_emitted_with_permissions_granted`() = runTest {
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)
        assertEquals(location, vm.state.value.userGpsPoint)
    }

    @Test
    fun `should_not_update_userGpsPoint_when_permissions_denied`() = runTest {
        locationDataSource.emitHighAccuracy(location)
        assertNull(vm.state.value.userGpsPoint)
    }

    // ── Foreground-scoped high-accuracy location [UI-LOC-FOREGROUND-001] ───────

    @Test
    fun `should_not_subscribe_location_when_map_backgrounded_even_with_permissions`() = runTest {
        val bgVm = buildVm(foreground = false)
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)
        // Backgrounded map ⇒ no high-accuracy request armed ⇒ the fix never reaches state.
        assertNull(bgVm.state.value.userGpsPoint)
    }

    @Test
    fun `should_start_subscription_when_map_returns_to_foreground`() = runTest {
        val bgVm = buildVm(foreground = false)
        permissions.emit(FakePermissionManager.allGranted())
        bgVm.handleIntent(HomeIntent.SetMapForeground(true))
        locationDataSource.emitHighAccuracy(location)
        assertEquals(location, bgVm.state.value.userGpsPoint)
    }

    @Test
    fun `should_log_SUBSCRIBED_then_FIX_samples_when_foreground_and_location_flows`() = runTest {
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)
        val kinds = uiLocationLogger.kinds()
        assertEquals(com.rndeveloper.paparcar.domain.diagnostics.UiLocationSample.Kind.SUBSCRIBED, kinds.first())
        assertTrue(kinds.contains(com.rndeveloper.paparcar.domain.diagnostics.UiLocationSample.Kind.FIX))
        val fix = uiLocationLogger.samples.first { it.kind == com.rndeveloper.paparcar.domain.diagnostics.UiLocationSample.Kind.FIX }
        assertEquals(location.accuracy, fix.accuracy)
        assertTrue(fix.foreground)
    }

    @Test
    fun `should_log_STOPPED_sample_when_map_goes_to_background`() = runTest {
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)
        vm.handleIntent(HomeIntent.SetMapForeground(false))
        assertTrue(uiLocationLogger.kinds().contains(com.rndeveloper.paparcar.domain.diagnostics.UiLocationSample.Kind.STOPPED))
    }

    @Test
    fun `should_set_nearbySpots_empty_when_permissions_denied`() = runTest {
        spotRepo.spots = listOf(
            Spot(id = "s1", location = location, reportedBy = "u1", address = null, placeInfo = null),
        )
        assertEquals(emptyList(), vm.state.value.nearbySpots)
    }

    @Test
    fun `should_update_nearbySpots_when_permissions_granted_and_location_emitted`() = runTest {
        val spot = Spot(id = "s1", location = location, reportedBy = "u1", address = null, placeInfo = null)
        spotRepo.spots = listOf(spot)

        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)

        assertEquals(1, vm.state.value.nearbySpots.size)
        assertEquals("s1", vm.state.value.nearbySpots.first().id)
    }

    @Test
    fun `should_register_activityRecognition_when_permissions_granted`() = runTest {
        permissions.emit(FakePermissionManager.allGranted())
        assertTrue(activityRecognition.registerCount >= 1)
    }

    @Test
    fun `should_survive_activityRecognition_registration_failure`() = runTest {
        activityRecognition.shouldThrowOnRegister = true
        permissions.emit(FakePermissionManager.allGranted())
        // GPS + spots chain must still work despite AR failure
        locationDataSource.emitHighAccuracy(location)
        assertEquals(location, vm.state.value.userGpsPoint)
    }

    // ── Init — active session ─────────────────────────────────────────────────

    @Test
    fun `should_update_userParking_when_active_session_exists`() = runTest {
        val session = UserParking(id = "s1", location = location, isActive = true)
        val repo = FakeUserParkingRepository(initialSession = session)
        parkingRepo = repo
        vm = buildVm()
        assertEquals("s1", vm.state.value.userParking?.id)
    }

    @Test
    fun `should_have_null_userParking_when_no_active_session`() = runTest {
        assertNull(vm.state.value.userParking)
    }

    // ── LoadNearbySpots ───────────────────────────────────────────────────────

    @Test
    fun `should_emit_RequestLocationPermission_when_map_first_comes_on_screen_and_never_asked`() = runTest {
        // [ONB-PERMISSIONS-ASK-AT-THE-MOMENT-OF-VALUE-001] The ask fires when the map comes on
        // screen (SetMapForeground(true)) — the dialog opens OVER the map, its own context — and
        // the one-shot latch is stamped so a denial is never re-asked at every entry.
        val freshVm = buildVm(foreground = false, locationAlreadyAsked = false)
        freshVm.effect.test {
            freshVm.handleIntent(HomeIntent.SetMapForeground(true))
            assertIs<HomeEffect.RequestLocationPermission>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(prefs.hasRequestedLocationPermission)
    }

    @Test
    fun `should_not_emit_RequestLocationPermission_when_already_asked_once`() = runTest {
        // Default fixture = already-asked: coming on screen again must stay quiet.
        vm.effect.test {
            vm.handleIntent(HomeIntent.SetMapForeground(true))
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should_show_detection_pitch_after_first_entry_grant`() = runTest {
        // Location granted right after the first-entry ask → the one-time star-feature pitch.
        // [ONB-PERMISSIONS-ASK-AT-THE-MOMENT-OF-VALUE-001]
        val freshVm = buildVm(foreground = false, locationAlreadyAsked = false)
        freshVm.handleIntent(HomeIntent.SetMapForeground(true))
        permissions.emit(
            com.rndeveloper.paparcar.domain.permissions.AppPermissionState(
                hasLocationPermission = true,
                isLocationServicesEnabled = true,
            ),
        )
        assertTrue(freshVm.state.value.showDetectionPitch)

        // "Ahora no" is one tap out and it never comes back on later grants.
        freshVm.handleIntent(HomeIntent.DismissDetectionPitch)
        assertFalse(freshVm.state.value.showDetectionPitch)
    }

    @Test
    fun `should_not_show_detection_pitch_when_grant_was_not_the_first_entry_ask`() = runTest {
        // Default fixture never armed the ask — a grant from anywhere else stays silent.
        vm.handleIntent(HomeIntent.SetMapForeground(true))
        permissions.emit(FakePermissionManager.allGranted())
        assertFalse(vm.state.value.showDetectionPitch)
    }

    // ── Refused first-entry ask → the sheet opens on the location warning ────
    // [HOME-LOCATION-WARNING-IS-A-BADGE-NOT-A-MODAL-001]

    private suspend fun saveParkingCar() = vehicleRepo.saveVehicle(
        com.rndeveloper.paparcar.domain.model.Vehicle(
            id = "v1", userId = "mock_user_001", brand = "Ford", model = "Focus",
            sizeCategory = com.rndeveloper.paparcar.domain.model.VehicleSize.MEDIUM_SUV,
            vehicleType = com.rndeveloper.paparcar.domain.model.VehicleType.CAR, isActive = true,
        ),
    )

    /** The system dialog pauses the map and closing it resumes it — that bracket IS the answer. */
    private fun HomeViewModel.answerFirstEntryAsk(granted: Boolean) {
        handleIntent(HomeIntent.SetMapForeground(true)) // map on screen → the ask fires
        handleIntent(HomeIntent.SetMapForeground(false)) // the dialog covers the map
        if (granted) {
            permissions.emit(
                com.rndeveloper.paparcar.domain.permissions.AppPermissionState(
                    hasLocationPermission = true,
                    isLocationServicesEnabled = true,
                ),
            )
        }
        handleIntent(HomeIntent.SetMapForeground(true)) // the dialog is gone
    }

    @Test
    fun `should_cue_the_location_warning_when_the_first_entry_ask_is_refused_with_a_car`() = runTest(testDispatcher) {
        saveParkingCar()
        val freshVm = buildVm(foreground = false, locationAlreadyAsked = false)
        assertTrue(freshVm.state.value.detectionUiState.warnsLocationMissing)

        freshVm.answerFirstEntryAsk(granted = false)

        assertEquals(1, freshVm.state.value.locationWarningCue)
        // Later trips to the background and back are not a new refusal.
        freshVm.handleIntent(HomeIntent.SetMapForeground(false))
        freshVm.handleIntent(HomeIntent.SetMapForeground(true))
        assertEquals(1, freshVm.state.value.locationWarningCue)
    }

    @Test
    fun `should_stay_quiet_about_location_while_the_first_entry_ask_is_unanswered`() = runTest(testDispatcher) {
        // [HOME-A-PENDING-ASK-IS-NOT-A-REFUSAL-001] El aviso no puede llegar antes que la respuesta:
        // detrás del diálogo la hoja está abierta (la abre el checklist) y el aviso diría que active
        // la ubicación mientras el botón que la activa está encima.
        val freshVm = buildVm(foreground = false, locationAlreadyAsked = false)
        // Antes incluso de que el mapa dispare la pregunta: la pref aún no está estampada.
        assertFalse(freshVm.state.value.toFabsSlice().locationMissing)

        freshVm.handleIntent(HomeIntent.SetMapForeground(true)) // dispara la pregunta
        assertTrue(freshVm.state.value.locationEverAsked)
        assertTrue(freshVm.state.value.locationAnswerInFlight)
        assertFalse(freshVm.state.value.toFabsSlice().locationMissing)

        freshVm.handleIntent(HomeIntent.SetMapForeground(false)) // el diálogo tapa el mapa
        assertFalse(freshVm.state.value.toFabsSlice().locationMissing)

        freshVm.handleIntent(HomeIntent.SetMapForeground(true)) // contestado (no)
        assertFalse(freshVm.state.value.locationAnswerInFlight)
        assertTrue(freshVm.state.value.toFabsSlice().locationMissing)
    }

    @Test
    fun `should_warn_about_location_from_the_first_frame_when_the_ask_was_already_refused`() = runTest(testDispatcher) {
        // Segunda entrada: ya preguntamos y dijeron que no, así que no hay nada que esperar.
        // [HOME-A-PENDING-ASK-IS-NOT-A-REFUSAL-001]
        val vmAsked = buildVm(foreground = false, locationAlreadyAsked = true)
        assertTrue(vmAsked.state.value.locationEverAsked)
        assertFalse(vmAsked.state.value.locationAnswerInFlight)
        assertTrue(vmAsked.state.value.toFabsSlice().locationMissing)
    }

    @Test
    fun `should_lift_the_location_silence_when_the_dialog_never_paused_the_map`() = runTest(testDispatcher) {
        // Un OEM que no pausa la pantalla nos cuesta la auto-apertura, que es una cortesía; callar el
        // aviso una sesión entera sería la app enmudeciendo sobre lo único que necesita.
        // [HOME-A-PENDING-ASK-IS-NOT-A-REFUSAL-001]
        val freshVm = buildVm(foreground = false, locationAlreadyAsked = false)
        freshVm.handleIntent(HomeIntent.SetMapForeground(true)) // dispara la pregunta
        freshVm.handleIntent(HomeIntent.SetMapForeground(true)) // vuelve sin haber pasado por pause

        assertFalse(freshVm.state.value.locationAnswerInFlight)
        assertTrue(freshVm.state.value.toFabsSlice().locationMissing)
        // …y sin el paréntesis completo la hoja NO se auto-abre: eso sigue exigiendo la prueba de que
        // el diálogo estuvo delante. [HOME-LOCATION-WARNING-IS-A-BADGE-NOT-A-MODAL-001]
        assertEquals(0, freshVm.state.value.locationWarningCue)
    }

    @Test
    fun `should_not_cue_the_location_warning_when_there_is_no_car`() = runTest(testDispatcher) {
        // No car → no BlockedCore → no warning to open onto: Home lives without location.
        val freshVm = buildVm(foreground = false, locationAlreadyAsked = false)

        freshVm.answerFirstEntryAsk(granted = false)

        assertEquals(0, freshVm.state.value.locationWarningCue)
    }

    @Test
    fun `should_not_cue_the_location_warning_when_the_first_entry_ask_is_granted`() = runTest(testDispatcher) {
        saveParkingCar()
        val freshVm = buildVm(foreground = false, locationAlreadyAsked = false)

        freshVm.answerFirstEntryAsk(granted = true)

        assertEquals(0, freshVm.state.value.locationWarningCue)
    }

    @Test
    fun `should_not_emit_RequestLocationPermission_on_LoadNearbySpots_when_granted`() = runTest {
        permissions.emit(FakePermissionManager.allGranted())
        vm.effect.test {
            vm.handleIntent(HomeIntent.LoadNearbySpots)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── ConfirmDetectedParking ────────────────────────────────────────────────

    // ── AddingParking ─────────────────────────────────────────────────────────
    // `ManualPark` was retired — the empty-state CTA now opens AddingParking
    // (`EnterAddParkingMode` → user positions the pin → `ConfirmAddParking`).
    // The validation rules that used to live in the snap-to-GPS `manualPark()`
    // method now live in `confirmAddParking()` and are exercised on Confirm.

    @Test
    fun `should_emit_ShowError_on_ConfirmAddParking_when_no_GPS`() = runTest {
        vm.handleIntent(HomeIntent.EnterAddParkingMode(initialGps = null))
        vm.effect.test {
            vm.handleIntent(HomeIntent.ConfirmAddParking())
            assertIs<HomeEffect.ShowError>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should_save_parking_locally_on_ConfirmAddParking_when_offline`() = runTest(testDispatcher) {
        // Offline must NOT block the save — sessions are local-first (Room now, Firestore
        // mirrored by the WorkManager sync queue), same as the auto-detection confirm.
        // [OFFLINE-PARK-001]
        vehicleRepo.saveVehicle(
            com.rndeveloper.paparcar.domain.model.Vehicle(
                id = "v1", userId = "mock_user_001", brand = "Ford", model = "Focus",
                sizeCategory = com.rndeveloper.paparcar.domain.model.VehicleSize.MEDIUM_SUV,
                vehicleType = com.rndeveloper.paparcar.domain.model.VehicleType.CAR, isActive = true,
            ),
        )
        connectivity.emit(ConnectivityStatus.Offline)
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)

        vm.handleIntent(HomeIntent.EnterAddParkingMode(initialGps = location))
        vm.handleIntent(HomeIntent.ConfirmAddParking())

        assertEquals(1, parkingRepo.saveNewParkingSessionCallCount)
        assertIs<HomeMode.Browse>(vm.state.value.mode)
    }

    @Test
    fun `should_keep_detection_provenance_on_ConfirmAddParking_when_entered_from_detection_nudge`() = runTest(testDispatcher) {
        // The nudge ("Marcar mi plaza") is a DETECTION-nominated ask — the pin the user confirms
        // must keep detection provenance so the freed spot later publishes as AUTO_DETECTED
        // instead of a 15-min manual report. [DET-NUDGE-PIN-PROVENANCE-001]
        vehicleRepo.saveVehicle(
            com.rndeveloper.paparcar.domain.model.Vehicle(
                id = "v1", userId = "mock_user_001", brand = "Ford", model = "Focus",
                sizeCategory = com.rndeveloper.paparcar.domain.model.VehicleSize.MEDIUM_SUV,
                vehicleType = com.rndeveloper.paparcar.domain.model.VehicleType.CAR, isActive = true,
            ),
        )
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)

        vm.handleIntent(HomeIntent.EnterAddParkingMode(initialGps = location, fromDetectionNudge = true))
        vm.handleIntent(HomeIntent.ConfirmAddParking())
        advanceUntilIdle()

        val saved = parkingRepo.observeActiveSessions().first().single()
        assertEquals(SpotType.AUTO_DETECTED, saved.spotType)
        assertEquals("nudge", saved.detectionPath)
    }

    @Test
    fun `should_stamp_which_lane_asked_when_a_pending_nudge_is_on_record`() = runTest(testDispatcher) {
        // [DET-A-NUDGE-MUST-SAY-WHO-ASKED-001] The source is resolved HERE, from the pending nudge
        // this VM already observes for the banner — not plumbed through the notification intent.
        vehicleRepo.saveVehicle(
            com.rndeveloper.paparcar.domain.model.Vehicle(
                id = "v1", userId = "mock_user_001", brand = "Ford", model = "Focus",
                sizeCategory = com.rndeveloper.paparcar.domain.model.VehicleSize.MEDIUM_SUV,
                vehicleType = com.rndeveloper.paparcar.domain.model.VehicleType.CAR, isActive = true,
            ),
        )
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)
        prefs.setPendingParkNudge(
            com.rndeveloper.paparcar.domain.detection.PendingParkNudge(
                createdAtMs = 1L, source = "bt_no_move", vehicleId = "v1",
            ),
        )
        advanceUntilIdle()

        vm.handleIntent(HomeIntent.EnterAddParkingMode(initialGps = location, fromDetectionNudge = true))
        vm.handleIntent(HomeIntent.ConfirmAddParking())
        advanceUntilIdle()

        val saved = parkingRepo.observeActiveSessions().first().single()
        assertEquals("nudge_bt_no_move", saved.detectionPath)
        assertEquals(SpotType.AUTO_DETECTED, saved.spotType)
    }

    // ── The DRAG is the pin ───────────────────────────────────────────────────
    // Every test above confirms straight after entering the mode, so they all pass with a pin that
    // never moved — the whole "user drags the map, THAT is the place" path was untested.

    @Test
    fun `should_save_the_dragged_place_not_the_users_own_position_on_ConfirmAddParking`() = runTest(testDispatcher) {
        vehicleRepo.saveVehicle(
            Vehicle(
                id = "v1", userId = "mock_user_001", brand = "Ford", model = "Focus",
                sizeCategory = com.rndeveloper.paparcar.domain.model.VehicleSize.MEDIUM_SUV,
                vehicleType = com.rndeveloper.paparcar.domain.model.VehicleType.CAR, isActive = true,
            ),
        )
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)
        advanceUntilIdle()

        // Enter pre-centred on the user, then drag the map ~350 m away, as the coach mark asks.
        vm.handleIntent(HomeIntent.EnterAddParkingMode(initialGps = location))
        vm.handleIntent(HomeIntent.CameraPositionChanged(lat = 40.419900, lon = -3.700100))
        vm.handleIntent(HomeIntent.ConfirmAddParking())
        advanceUntilIdle()

        val saved = parkingRepo.observeActiveSessions().first().single()
        assertEquals(40.419900, saved.location.latitude, absoluteTolerance = 1e-6)
        assertEquals(-3.700100, saved.location.longitude, absoluteTolerance = 1e-6)
    }

    @Test
    fun `should_keep_the_dragged_place_when_a_new_gps_fix_lands_before_the_user_confirms`() = runTest(testDispatcher) {
        // The pin is placed for a car that is NOT where the phone is; a fix arriving mid-placement
        // (they always do) must not drag the pending pin back onto the user.
        vehicleRepo.saveVehicle(
            Vehicle(
                id = "v1", userId = "mock_user_001", brand = "Ford", model = "Focus",
                sizeCategory = com.rndeveloper.paparcar.domain.model.VehicleSize.MEDIUM_SUV,
                vehicleType = com.rndeveloper.paparcar.domain.model.VehicleType.CAR, isActive = true,
            ),
        )
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)
        advanceUntilIdle()

        vm.handleIntent(HomeIntent.EnterAddParkingMode(initialGps = location))
        vm.handleIntent(HomeIntent.CameraPositionChanged(lat = 40.419900, lon = -3.700100))
        locationDataSource.emitHighAccuracy(location.copy(latitude = 40.416800, timestamp = 5_000L))
        advanceUntilIdle()
        vm.handleIntent(HomeIntent.ConfirmAddParking())
        advanceUntilIdle()

        val saved = parkingRepo.observeActiveSessions().first().single()
        assertEquals(40.419900, saved.location.latitude, absoluteTolerance = 1e-6)
    }

    // ── ConfirmAddParking from EDIT: correct-in-place vs re-park ───────────────
    // From the edit sheet the user decides at confirm: "Correct location" moves THIS
    // session's pin; "I parked somewhere else" creates a NEW session for the same
    // vehicle (the model supersedes the old one). [UX-PARKED-STATE-001]

    @Test
    fun `should_move_session_on_ConfirmAddParking_from_edit_when_not_asNewSession`() = runTest(testDispatcher) {
        val session = UserParking(
            id = "session-1", userId = "user-123", vehicleId = "veh-1",
            location = location, isActive = true,
        )
        parkingRepo = FakeUserParkingRepository(initialSessions = listOf(session))
        vm = buildVm()
        advanceUntilIdle()

        vm.handleIntent(HomeIntent.EnterAddParkingMode(initialGps = location, editingParkingId = session.id))
        vm.handleIntent(HomeIntent.ConfirmAddParking(asNewSession = false))
        advanceUntilIdle()

        // Correct = move the same session in place, never a second session.
        assertEquals(1, parkingRepo.updateParkingSessionPositionCallCount)
        assertEquals(0, parkingRepo.saveNewParkingSessionCallCount)
    }

    @Test
    fun `should_create_new_session_on_ConfirmAddParking_from_edit_when_asNewSession`() = runTest(testDispatcher) {
        val session = UserParking(
            id = "session-1", userId = "user-123", vehicleId = "veh-1",
            location = location, isActive = true,
        )
        vehicleRepo.saveVehicle(
            Vehicle(
                id = "veh-1", userId = "user-123", brand = "Ford", model = "Focus",
                sizeCategory = com.rndeveloper.paparcar.domain.model.VehicleSize.MEDIUM_SUV,
                vehicleType = com.rndeveloper.paparcar.domain.model.VehicleType.CAR, isActive = true,
            ),
        )
        parkingRepo = FakeUserParkingRepository(initialSessions = listOf(session))
        vm = buildVm()
        advanceUntilIdle()

        vm.handleIntent(HomeIntent.EnterAddParkingMode(initialGps = location, editingParkingId = session.id))
        vm.handleIntent(HomeIntent.ConfirmAddParking(asNewSession = true))
        advanceUntilIdle()

        // Re-park = a NEW session for the same vehicle; the pin of the old one is never moved.
        assertEquals(1, parkingRepo.saveNewParkingSessionCallCount)
        assertEquals(0, parkingRepo.updateParkingSessionPositionCallCount)
    }

    // ── ReleaseParking ────────────────────────────────────────────────────────

    @Test
    fun `should_emit_SpotReported_on_ReleaseParking_success`() = runTest {
        val session = UserParking(
            id = "session-1",
            userId = "user-1",
            vehicleId = "veh-1",
            location = location,
            isActive = true,
        )
        parkingRepo = FakeUserParkingRepository(initialSessions = listOf(session))
        vm = buildVm()
        vm.effect.test {
            vm.handleIntent(HomeIntent.ReleaseParking(sessionId = session.id, reason = ParkingReleaseReason.DEPARTURE))
            assertIs<HomeEffect.SpotReported>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Which action is in flight [UI-A-SPINNER-LIVES-IN-THE-BUTTON-THAT-WAS-PRESSED-001] ─────
    // The state used to hold a bare `isReleasingParking: Boolean`. The release dialog has one
    // button again since [SPOT-A-PRIVATE-ZONE-DECIDES-THE-PUBLICATION-NOT-A-BUTTON-001], but the
    // discriminator still earns its place: RECORD_DELETED runs the same code from the edit peek,
    // and only the surface that started a release may spin. These two tests pin it, in flight and
    // after: the reason the user chose is what the state holds.
    // They run on a StandardTestDispatcher on purpose — the suite's Unconfined one would finish the
    // release before there is any in-flight window left to look at.

    @Test
    fun `should_remember_the_chosen_reason_while_the_release_is_in_flight`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val session = UserParking(
            id = "session-1", userId = "user-1", vehicleId = "veh-1",
            location = location, isActive = true,
        )
        parkingRepo = FakeUserParkingRepository(initialSessions = listOf(session))
        vm = buildVm()
        advanceUntilIdle()

        vm.handleIntent(
            HomeIntent.ReleaseParking(
                sessionId = session.id,
                reason = ParkingReleaseReason.DEPARTURE,
            ),
        )

        // Not merely "a release is running": WHICH one, so only the surface that asked spins.
        assertEquals(ParkingReleaseReason.DEPARTURE, vm.state.value.releasingReason)
        assertTrue(vm.state.value.isReleasingParking)

        advanceUntilIdle()
        assertNull(vm.state.value.releasingReason)
    }

    @Test
    fun `should_remember_which_confirm_is_in_flight_on_ConfirmAddParking_from_edit`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val session = UserParking(
            id = "session-1", userId = "user-123", vehicleId = "veh-1",
            location = location, isActive = true,
        )
        parkingRepo = FakeUserParkingRepository(initialSessions = listOf(session))
        vm = buildVm()
        advanceUntilIdle()

        vm.handleIntent(HomeIntent.EnterAddParkingMode(initialGps = location, editingParkingId = session.id))
        advanceUntilIdle()
        vm.handleIntent(HomeIntent.ConfirmAddParking(asNewSession = true))

        // Re-park and correct-in-place sit in the same peek: `true` is what lets the re-park button
        // carry its own spinner instead of lighting the confirm above it.
        assertEquals(true, vm.state.value.savingParkingAsNewSession)

        advanceUntilIdle()
        assertNull(vm.state.value.savingParkingAsNewSession)
    }

    @Test
    fun `should_clear_selection_on_ReleaseParking_success`() = runTest {
        val session = UserParking(
            id = "session-1",
            userId = "user-1",
            vehicleId = "veh-1",
            location = location,
            isActive = true,
        )
        parkingRepo = FakeUserParkingRepository(initialSessions = listOf(session))
        vm = buildVm()
        vm.handleIntent(HomeIntent.SelectItem(HomeSelection.Parking(session.id)))
        vm.handleIntent(HomeIntent.ReleaseParking(sessionId = session.id, reason = ParkingReleaseReason.DEPARTURE))
        assertNull(vm.state.value.selection)
    }

    // ── Release dialog lifecycle [PARK-RELEASE-THE-SCREEN-NEVER-SAW-001] ────────────────────
    // The dialog used to be screen state, closed when the screen SAW `isReleasingParking` flip
    // true → false. A release faster than a frame never showed the `true`, the dialog stayed open over
    // a session that no longer existed, and every further tap "did nothing". It is VM state now.

    private fun releasableSession(id: String = "session-1") = UserParking(
        id = id, userId = "user-1", vehicleId = "veh-1", location = location, isActive = true,
    )

    @Test
    fun should_closeReleaseDialog_when_releaseFinishes_evenWithNoInFlightWindowToObserve() = runTest {
        // Unconfined: the release runs to completion inside handleIntent — exactly the case a
        // screen watching for the in-flight flag could never see.
        val session = releasableSession()
        parkingRepo = FakeUserParkingRepository(initialSessions = listOf(session))
        vm = buildVm()

        vm.handleIntent(HomeIntent.OpenReleaseDialog(session.id))
        assertEquals(session.id, vm.state.value.releaseDialogSessionId)

        vm.handleIntent(HomeIntent.ReleaseParking(session.id, ParkingReleaseReason.DEPARTURE))

        assertNull(vm.state.value.releaseDialogSessionId)
        assertEquals(1, parkingRepo.clearActiveParkingSessionCallCount)
    }

    @Test
    fun should_closeReleaseDialog_when_releaseFails() = runTest {
        val session = releasableSession()
        parkingRepo = FakeUserParkingRepository(initialSessions = listOf(session))
        parkingRepo.clearActiveParkingSessionResult = Result.failure(IllegalStateException("disk full"))
        vm = buildVm()

        vm.effect.test {
            vm.handleIntent(HomeIntent.OpenReleaseDialog(session.id))
            vm.handleIntent(HomeIntent.ReleaseParking(session.id, ParkingReleaseReason.DEPARTURE))

            // Closed, so the snackbar carrying the error is not hidden behind it.
            assertNull(vm.state.value.releaseDialogSessionId)
            assertIs<HomeEffect.ShowError>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun should_runOnlyOneRelease_when_theConfirmIsTappedTwiceWithinTheSameFrame() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val session = releasableSession()
        parkingRepo = FakeUserParkingRepository(initialSessions = listOf(session))
        vm = buildVm()
        advanceUntilIdle()

        vm.handleIntent(HomeIntent.OpenReleaseDialog(session.id))
        vm.handleIntent(HomeIntent.ReleaseParking(session.id, ParkingReleaseReason.DEPARTURE))
        // Second tap before any recomposition could disable the button.
        vm.handleIntent(HomeIntent.ReleaseParking(session.id, ParkingReleaseReason.DEPARTURE))
        advanceUntilIdle()

        assertEquals(1, parkingRepo.clearActiveParkingSessionCallCount)
        assertEquals(1, reportScheduler.scheduleCallCount, "one tap, one plaza")
        // The first answer is the one that counted: the spot went out AND Room says so.
        assertEquals(true, parkingRepo.getSessionById(session.id)?.publishedSpot)
        assertNull(vm.state.value.releaseDialogSessionId)
    }

    @Test
    fun should_keepReleaseDialogOpen_when_dismissedWhileTheReleaseIsInFlight() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val session = releasableSession()
        parkingRepo = FakeUserParkingRepository(initialSessions = listOf(session))
        vm = buildVm()
        advanceUntilIdle()

        vm.handleIntent(HomeIntent.OpenReleaseDialog(session.id))
        vm.handleIntent(HomeIntent.ReleaseParking(session.id, ParkingReleaseReason.DEPARTURE))
        vm.handleIntent(HomeIntent.DismissReleaseDialog)
        assertEquals(session.id, vm.state.value.releaseDialogSessionId)

        advanceUntilIdle()
        assertNull(vm.state.value.releaseDialogSessionId)
    }

    @Test
    fun should_closeReleaseDialog_when_dismissedWithNoReleaseRunning() = runTest {
        val session = releasableSession()
        parkingRepo = FakeUserParkingRepository(initialSessions = listOf(session))
        vm = buildVm()

        vm.handleIntent(HomeIntent.OpenReleaseDialog(session.id))
        vm.handleIntent(HomeIntent.DismissReleaseDialog)

        assertNull(vm.state.value.releaseDialogSessionId)
        assertEquals(0, parkingRepo.clearActiveParkingSessionCallCount)
    }

    @Test
    fun should_closeReleaseDialog_when_itsSessionIsClosedByAnotherPath() = runTest {
        // A detected departure (or the safety net) closes the session while the user looks at the
        // dialog: releasing it again can only fail, so the dialog has nothing left to offer.
        val session = releasableSession()
        parkingRepo = FakeUserParkingRepository(initialSessions = listOf(session))
        vm = buildVm()
        vm.handleIntent(HomeIntent.OpenReleaseDialog(session.id))

        parkingRepo.clearActiveParkingSession(session.id, endedAtMs = 1L, publishedSpot = true)

        assertNull(vm.state.value.releaseDialogSessionId)
    }

    @Test
    fun should_keepReleaseDialogOpen_when_anotherCarsSessionCloses() = runTest {
        val mine = releasableSession("session-mine")
        val other = releasableSession("session-other").copy(vehicleId = "veh-2")
        parkingRepo = FakeUserParkingRepository(initialSessions = listOf(mine, other))
        vm = buildVm()
        vm.handleIntent(HomeIntent.OpenReleaseDialog(mine.id))

        parkingRepo.clearActiveParkingSession(other.id, endedAtMs = 1L, publishedSpot = false)

        assertEquals(mine.id, vm.state.value.releaseDialogSessionId)
    }

    // ── SelectItem ────────────────────────────────────────────────────────────

    @Test
    fun should_set_selection_on_SelectItem() = runTest {
        vm.handleIntent(HomeIntent.SelectItem(HomeSelection.Spot("spot-42")))
        assertEquals(HomeSelection.Spot("spot-42"), vm.state.value.selection)
    }

    @Test
    fun `should_clear_selection_on_SelectItem_null`() = runTest {
        vm.handleIntent(HomeIntent.SelectItem(HomeSelection.Spot("spot-42")))
        vm.handleIntent(HomeIntent.SelectItem(null))
        assertNull(vm.state.value.selection)
    }

    // ── Mode atomicity invariant ──────────────────────────────────────────────
    // Selecting a marker while in any add-mode must exit that add-mode cleanly:
    // the modal, pin and per-mode form state cannot coexist with a selection.
    // (Without this enforcement, the AddingParking pin used to remain on screen
    //  underneath a freshly selected marker's peek card.)

    @Test
    fun `should_exit_AddingParking_and_clear_pin_state_on_SelectItem`() = runTest {
        vm.handleIntent(HomeIntent.EnterAddParkingMode(initialGps = location))
        assertEquals(HomeMode.AddingParking, vm.state.value.mode)

        vm.handleIntent(HomeIntent.SelectItem(HomeSelection.Spot("spot-42")))

        val s = vm.state.value
        assertEquals(HomeMode.Browse, s.mode)
        assertEquals(HomeSelection.Spot("spot-42"), s.selection)
        assertNull(s.pinCameraLat)
        assertNull(s.pinCameraLon)
        assertNull(s.editingParkingId)
        assertNull(s.addingParkingVehicleId)
    }

    @Test
    fun `should_exit_Reporting_and_clear_reportingSize_on_SelectItem`() = runTest {
        vm.handleIntent(HomeIntent.EnterReportMode(lat = 40.0, lon = -3.7))
        vm.handleIntent(HomeIntent.SetReportingSize(com.rndeveloper.paparcar.domain.model.VehicleSize.MEDIUM_SUV))
        assertEquals(HomeMode.Reporting, vm.state.value.mode)

        vm.handleIntent(HomeIntent.SelectItem(HomeSelection.Spot("spot-42")))

        val s = vm.state.value
        assertEquals(HomeMode.Browse, s.mode)
        assertEquals(HomeSelection.Spot("spot-42"), s.selection)
        assertNull(s.reportingSize)
        assertNull(s.pinCameraLat)
    }

    @Test
    fun `should_exit_AddingZone_and_clear_zone_form_on_SelectItem`() = runTest {
        vm.handleIntent(HomeIntent.EnterAddZoneMode(lat = 40.0, lon = -3.7))
        vm.handleIntent(HomeIntent.UpdateAddingZoneName("Casa"))
        vm.handleIntent(HomeIntent.SetZoneIsPrivate(true))
        assertEquals(HomeMode.AddingZone, vm.state.value.mode)

        vm.handleIntent(HomeIntent.SelectItem(HomeSelection.Spot("spot-42")))

        val s = vm.state.value
        assertEquals(HomeMode.Browse, s.mode)
        assertEquals(HomeSelection.Spot("spot-42"), s.selection)
        assertEquals("", s.addingZoneName)
        assertEquals(false, s.addingZoneIsPrivate)
        assertNull(s.editingZoneId)
    }

    // ── SetMapType ────────────────────────────────────────────────────────────

    @Test
    fun `should_set_mapType_on_SetMapType`() = runTest {
        vm.handleIntent(HomeIntent.SetMapType(MapType.HYBRID))
        assertEquals(MapType.HYBRID, vm.state.value.mapType)
    }

    @Test
    fun `should_persist_hybrid_mapType_to_preferences_on_SetMapType`() = runTest {
        vm.handleIntent(HomeIntent.SetMapType(MapType.HYBRID))
        assertEquals("HYBRID", prefs.defaultMapType)
    }

    @Test
    fun `should_persist_mapType_to_preferences_on_SetMapType`() = runTest {
        vm.handleIntent(HomeIntent.SetMapType(MapType.HYBRID))
        vm.handleIntent(HomeIntent.SetMapType(MapType.NORMAL))
        assertEquals("NORMAL", prefs.defaultMapType)
    }

    @Test
    fun `should_not_update_preferences_when_same_mapType_set`() = runTest {
        vm.handleIntent(HomeIntent.SetMapType(MapType.NORMAL))
        // First SetMapType with same value as init — prefs should remain untouched
        assertEquals("NORMAL", prefs.defaultMapType)
        // Verify by switching and switching back
        vm.handleIntent(HomeIntent.SetMapType(MapType.HYBRID))
        val countAfterFirst = prefs.defaultMapType
        vm.handleIntent(HomeIntent.SetMapType(MapType.HYBRID))
        // calling with same type should NOT change the prefs value (no second write)
        assertEquals(countAfterFirst, prefs.defaultMapType)
    }


    // ── Search ────────────────────────────────────────────────────────────────

    @Test
    fun `should_update_searchQuery_and_activate_search_on_SearchQueryChanged`() = runTest {
        vm.handleIntent(HomeIntent.SearchQueryChanged("Madrid"))
        assertEquals("Madrid", vm.state.value.searchQuery)
        assertEquals(true, vm.state.value.isSearchActive)
    }

    @Test
    fun `should_clear_searchResults_on_blank_SearchQueryChanged`() = runTest {
        vm.handleIntent(HomeIntent.SearchQueryChanged(""))
        assertEquals(emptyList(), vm.state.value.searchResults)
        assertEquals(false, vm.state.value.isSearching)
    }

    @Test
    fun `should_clear_search_state_on_ClearSearch`() = runTest {
        vm.handleIntent(HomeIntent.SearchQueryChanged("Madrid"))
        vm.handleIntent(HomeIntent.ClearSearch)
        assertEquals("", vm.state.value.searchQuery)
        assertEquals(emptyList(), vm.state.value.searchResults)
        assertEquals(false, vm.state.value.isSearchActive)
        assertEquals(false, vm.state.value.searchNoResults)
    }

    @Test
    fun `should_flag_searchNoResults_when_a_search_succeeds_empty`() = runTest {
        geocoderFake.searchResults = Result.success(emptyList())
        vm.handleIntent(HomeIntent.SearchQueryChanged("Nowhere Street 999"))
        advanceUntilIdle()
        assertEquals(true, vm.state.value.searchNoResults)
        assertEquals(emptyList(), vm.state.value.searchResults)

        // Blanking the query dismisses the "no results" row.
        vm.handleIntent(HomeIntent.SearchQueryChanged(""))
        assertEquals(false, vm.state.value.searchNoResults)
    }

    @Test
    fun `should_emit_ShowError_and_not_flag_noResults_when_the_search_fails`() = runTest {
        geocoderFake.searchResults = Result.failure(RuntimeException("geocoder down"))
        vm.effect.test {
            vm.handleIntent(HomeIntent.SearchQueryChanged("Madrid"))
            advanceUntilIdle()
            val effect = awaitItem()
            assertIs<HomeEffect.ShowError>(effect)
            assertIs<com.rndeveloper.paparcar.domain.error.PaparcarError.Location.SearchFailed>(effect.error)
            cancelAndIgnoreRemainingEvents()
        }
        // A broken geocoder must not read like "nothing found". [UX-PARK-FLOW-001 H3]
        assertEquals(false, vm.state.value.searchNoResults)
        assertEquals(emptyList(), vm.state.value.searchResults)
        assertEquals(false, vm.state.value.isSearching)
    }

    // ── SetSizeFilter ─────────────────────────────────────────────────────────

    @Test
    fun `should_set_sizeFilter_on_SetSizeFilter`() = runTest {
        vm.handleIntent(HomeIntent.SetSizeFilter(com.rndeveloper.paparcar.domain.model.VehicleSize.MICRO_SMALL))
        assertEquals(com.rndeveloper.paparcar.domain.model.VehicleSize.MICRO_SMALL, vm.state.value.sizeFilter)
    }

    @Test
    fun `should_clear_sizeFilter_on_SetSizeFilter_null`() = runTest {
        vm.handleIntent(HomeIntent.SetSizeFilter(com.rndeveloper.paparcar.domain.model.VehicleSize.MICRO_SMALL))
        vm.handleIntent(HomeIntent.SetSizeFilter(null))
        assertNull(vm.state.value.sizeFilter)
    }

    // ── SendSpotSignal ────────────────────────────────────────────────────────

    /**
     * Puts the user AND a spot at the same coordinates, so the vote is cast by a witness.
     * [SPOT-COMMUNITY-VOTES-NEED-A-CONSEQUENCE-001] — before this ticket a vote needed no location
     * at all, so these tests used to pass with a spot the VM had never heard of.
     */
    private suspend fun standAtSpot(spotId: String = "spot-1") {
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)
        spotRepo.spots = listOf(
            Spot(id = spotId, location = location, reportedBy = "u1", address = null, placeInfo = null),
        )
    }

    @Test
    fun `should_emit_SpotSignalSent_on_SendSpotSignal_success`() = runTest {
        standAtSpot()
        vm.effect.test {
            vm.handleIntent(HomeIntent.SendSpotSignal("spot-1", accepted = true))
            val effect = assertIs<HomeEffect.SpotSignalSent>(awaitItem())
            assertEquals(SpotVoteOutcome.REFRESH, effect.outcome)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should_emit_ShowError_on_SendSpotSignal_failure`() = runTest {
        standAtSpot()
        spotRepo.signalResult = Result.failure(RuntimeException("network error"))
        vm.effect.test {
            vm.handleIntent(HomeIntent.SendSpotSignal("spot-1", accepted = false))
            assertIs<HomeEffect.ShowError>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should_retract_the_spot_when_a_witness_reports_it_gone`() = runTest {
        standAtSpot()

        vm.handleIntent(HomeIntent.SendSpotSignal("spot-1", accepted = false))

        assertEquals(listOf("spot-1"), spotRepo.retractedSpotIds)
        assertTrue("spot-1" in vm.state.value.votedSpotIds)
    }

    @Test
    fun `should_ignore_the_vote_when_the_user_is_nowhere_near_the_spot`() = runTest {
        // The VM measures the distance itself, from the state that owns both the fix and the spot,
        // so a UI that forgot to gate the buttons still cannot withdraw a spot from across town.
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)
        val farAway = location.copy(latitude = location.latitude + 1.0)
        spotRepo.spots = listOf(
            Spot(id = "spot-1", location = farAway, reportedBy = "u1", address = null, placeInfo = null),
        )

        vm.handleIntent(HomeIntent.SendSpotSignal("spot-1", accepted = false))

        assertTrue(spotRepo.retractedSpotIds.isEmpty())
        assertEquals(0, spotRepo.signalCallCount)
        assertTrue(vm.state.value.votedSpotIds.isEmpty())
    }

    // ── Connectivity reconnect tick ───────────────────────────────────────────

    @Test
    fun `should_trigger_spot_resubscription_on_offline_to_online_reconnect`() = runTest {
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)

        val spot1 = Spot(id = "s1", location = location, reportedBy = "u1", address = null, placeInfo = null)
        spotRepo.spots = listOf(spot1)
        assertEquals(1, vm.state.value.nearbySpots.size)

        // Simulate Offline → Online transition
        connectivity.emit(ConnectivityStatus.Offline)
        connectivity.emit(ConnectivityStatus.Online)

        val spot2 = Spot(id = "s2", location = location, reportedBy = "u2", address = null, placeInfo = null)
        spotRepo.spots = listOf(spot1, spot2)
        assertEquals(2, vm.state.value.nearbySpots.size)
    }

    // ── selection deselected when spot disappears ────────────────────────

    @Test
    fun `should_clear_selection_when_selected_spot_disappears_from_nearbySpots`() = runTest {
        val spot = Spot(id = "s1", location = location, reportedBy = "u1", address = null, placeInfo = null)
        spotRepo.spots = listOf(spot)
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)

        vm.handleIntent(HomeIntent.SelectItem(HomeSelection.Spot("s1")))
        assertEquals(HomeSelection.Spot("s1"), vm.state.value.selection)

        spotRepo.spots = emptyList()
        assertNull(vm.state.value.selection)
    }

    @Test
    fun `should_keep_session_selection_when_spots_change`() = runTest {
        val session = UserParking(
            id = "session-1",
            userId = "user-1",
            vehicleId = "v-1",
            location = location,
            isActive = true,
        )
        parkingRepo = FakeUserParkingRepository(initialSession = session)
        vm = buildVm()

        val spot = Spot(id = "s1", location = location, reportedBy = "u1", address = null, placeInfo = null)
        spotRepo.spots = listOf(spot)
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)

        vm.handleIntent(HomeIntent.SelectItem(HomeSelection.Parking(session.id)))
        spotRepo.spots = emptyList()

        assertEquals(HomeSelection.Parking(session.id), vm.state.value.selection)
    }

    // ── Multi-vehicle vehicleCards projection ─────────────────────────────────

    @Test
    fun `should_emit_one_vehicleCard_per_registered_vehicle_with_session_joined_by_vehicleId`() = runTest {
        val parked = UserParking(
            id = "session-parked",
            userId = "user-1",
            vehicleId = "veh-A",
            location = location,
            isActive = true,
        )
        parkingRepo = FakeUserParkingRepository(initialSession = parked)
        vehicleRepo = FakeVehicleRepository(
            defaultVehicle = Vehicle(
                id = "veh-A",
                userId = "user-1",
                brand = "Toyota",
                model = "Corolla",
                sizeCategory = com.rndeveloper.paparcar.domain.model.VehicleSize.MEDIUM_SUV,
            ),
            extraVehicles = listOf(
                Vehicle(
                    id = "veh-B",
                    userId = "user-1",
                    brand = "Ford",
                    model = "Transit",
                    sizeCategory = com.rndeveloper.paparcar.domain.model.VehicleSize.LARGE_SEDAN,
                ),
            ),
        )
        vm = buildVm()

        val cards = vm.state.value.toBrowseListSlice().vehicleCards
        assertEquals(2, cards.size)
        val parkedCard = cards.first { it.vehicle.id == "veh-A" }
        val emptyCard = cards.first { it.vehicle.id == "veh-B" }
        assertEquals("session-parked", parkedCard.session?.id)
        assertNull(emptyCard.session)
    }

    @Test
    fun `should_emit_empty_vehicleCards_when_no_vehicles_registered`() = runTest {
        // Default FakeVehicleRepository in setUp has no vehicles
        assertEquals(emptyList(), vm.state.value.toBrowseListSlice().vehicleCards)
    }

    // ── Release-by-sessionId (multi-parking) ──────────────────────────────────

    @Test
    fun `should_release_only_the_selected_session_when_multiple_active_sessions_exist`() = runTest {
        val sessionA = UserParking(
            id = "session-A",
            userId = "user-1",
            vehicleId = "veh-A",
            location = location,
            isActive = true,
        )
        val sessionB = UserParking(
            id = "session-B",
            userId = "user-1",
            vehicleId = "veh-B",
            location = location,
            isActive = true,
        )
        parkingRepo = FakeUserParkingRepository(initialSessions = listOf(sessionA, sessionB))
        vm = buildVm()

        // The peek fires the tapped card's id — release targets THAT session explicitly.
        vm.handleIntent(HomeIntent.ReleaseParking(sessionId = sessionB.id, reason = ParkingReleaseReason.DEPARTURE))

        // sessionA must remain active; sessionB cleared.
        val activeAfter = vm.state.value.activeSessions.map { it.id }.toSet()
        assertEquals(setOf("session-A"), activeAfter)
    }

    @Test
    fun `should_noop_and_error_when_releasing_an_unknown_sessionId`() = runTest {
        // Regression for the Beat "release fantasma": with two active sessions, a release whose id
        // resolves to nothing must NOT fall back to the first-ranked session. [VEH-ACTIVE-FENCE-001]
        val sessionA = UserParking(
            id = "session-A",
            userId = "user-1",
            vehicleId = "veh-A",
            location = location,
            isActive = true,
        )
        val sessionB = UserParking(
            id = "session-B",
            userId = "user-1",
            vehicleId = "veh-B",
            location = location,
            isActive = true,
        )
        parkingRepo = FakeUserParkingRepository(initialSessions = listOf(sessionA, sessionB))
        vm = buildVm()

        vm.effect.test {
            vm.handleIntent(HomeIntent.ReleaseParking(sessionId = "ghost-session", reason = ParkingReleaseReason.DEPARTURE))
            val effect = awaitItem()
            assertIs<HomeEffect.ShowError>(effect)
            assertIs<com.rndeveloper.paparcar.domain.error.PaparcarError.Parking.ReleaseFailed>(effect.error)
            cancelAndIgnoreRemainingEvents()
        }

        // Nothing was released — both sessions remain active.
        val activeAfter = vm.state.value.activeSessions.map { it.id }.toSet()
        assertEquals(setOf("session-A", "session-B"), activeAfter)
    }

    @Test
    fun `should_set_the_released_vehicle_active_when_it_was_inactive`() = runTest {
        // Releasing an inactive car's spot IS the declaration that you now drive it. [VEH-ACTIVE-FENCE-001]
        val activeVeh = Vehicle(
            id = "veh-active", userId = "user-1", brand = "Seat", model = "Leon",
            sizeCategory = com.rndeveloper.paparcar.domain.model.VehicleSize.MEDIUM_SUV,
        )
        val inactiveVeh = Vehicle(
            id = "veh-inactive", userId = "user-1", brand = "Toyota", model = "Corolla",
            sizeCategory = com.rndeveloper.paparcar.domain.model.VehicleSize.MEDIUM_SUV,
        )
        vehicleRepo = FakeVehicleRepository(defaultVehicle = activeVeh, extraVehicles = listOf(inactiveVeh))
        val session = UserParking(
            id = "session-inactive", userId = "user-1", vehicleId = "veh-inactive",
            location = location, isActive = true,
        )
        parkingRepo = FakeUserParkingRepository(initialSessions = listOf(session))
        vm = buildVm()

        vm.handleIntent(HomeIntent.ReleaseParking(sessionId = "session-inactive", reason = ParkingReleaseReason.DEPARTURE))
        advanceUntilIdle()

        assertEquals(listOf("veh-inactive"), vehicleRepo.setActiveCalls)
    }

    @Test
    fun `should_keep_the_active_vehicle_when_the_released_car_is_bluetooth_paired`() = runTest {
        // [PARK-DELETE-NO-DECLARE-001] [DET-BT-OWNERSHIP-001] The active flag is the identity of the
        // car that has no other one. Leaving in the BT Kamiq (identified by its MAC) must not strip
        // the coordinator-watched Focus of its flag — and of its fences via the swap.
        val activeVeh = Vehicle(
            id = "veh-focus", userId = "user-1", brand = "Ford", model = "Focus",
            sizeCategory = com.rndeveloper.paparcar.domain.model.VehicleSize.MEDIUM_SUV,
        )
        val btVeh = Vehicle(
            id = "veh-kamiq", userId = "user-1", brand = "Skoda", model = "Kamiq",
            sizeCategory = com.rndeveloper.paparcar.domain.model.VehicleSize.MEDIUM_SUV,
            bluetoothDeviceId = "AA:BB:CC:DD:EE:FF",
        )
        vehicleRepo = FakeVehicleRepository(defaultVehicle = activeVeh, extraVehicles = listOf(btVeh))
        val session = UserParking(
            id = "session-kamiq", userId = "user-1", vehicleId = "veh-kamiq",
            location = location, isActive = true,
        )
        parkingRepo = FakeUserParkingRepository(initialSessions = listOf(session))
        vm = buildVm()
        advanceUntilIdle()

        vm.handleIntent(HomeIntent.ReleaseParking(sessionId = "session-kamiq", reason = ParkingReleaseReason.DEPARTURE))
        advanceUntilIdle()

        assertEquals(emptyList(), vehicleRepo.setActiveCalls)
        assertEquals("veh-focus", vehicleRepo.observeActiveVehicle().first()?.id)
        // The plaza is still published — only the identity claim is vetoed.
        assertEquals(1, reportScheduler.scheduleCallCount)
    }

    // ── [PARK-DELETE-NO-DECLARE-001] Deleting a record declares nothing ───────

    @Test
    fun `should_keep_the_active_vehicle_when_deleting_another_cars_parking_record`() = runTest {
        // Field report 14-08: deleting the Kamiq's parking record stole the active flag (and with it
        // the geofences) from the Ford Focus. Deleting a wrong record says nothing about which car
        // the user drives.
        val activeVeh = Vehicle(
            id = "veh-focus", userId = "user-1", brand = "Ford", model = "Focus",
            sizeCategory = com.rndeveloper.paparcar.domain.model.VehicleSize.MEDIUM_SUV,
        )
        val otherVeh = Vehicle(
            id = "veh-kamiq", userId = "user-1", brand = "Skoda", model = "Kamiq",
            sizeCategory = com.rndeveloper.paparcar.domain.model.VehicleSize.MEDIUM_SUV,
        )
        vehicleRepo = FakeVehicleRepository(defaultVehicle = activeVeh, extraVehicles = listOf(otherVeh))
        val session = UserParking(
            id = "session-kamiq", userId = "user-1", vehicleId = "veh-kamiq",
            location = location, isActive = true,
        )
        parkingRepo = FakeUserParkingRepository(initialSessions = listOf(session))
        vm = buildVm()

        vm.handleIntent(HomeIntent.ReleaseParking(sessionId = "session-kamiq", reason = ParkingReleaseReason.RECORD_DELETED))
        advanceUntilIdle()

        assertEquals(emptyList(), vehicleRepo.setActiveCalls)
        assertEquals("veh-focus", vehicleRepo.observeActiveVehicle().first()?.id)
    }

    @Test
    fun `should_clear_the_session_without_reporting_a_spot_when_deleting_a_parking_record`() = runTest {
        val session = UserParking(
            id = "session-1", userId = "user-1", vehicleId = "veh-1",
            location = location, isActive = true,
        )
        parkingRepo = FakeUserParkingRepository(initialSessions = listOf(session))
        vm = buildVm()

        vm.handleIntent(HomeIntent.ReleaseParking(sessionId = "session-1", reason = ParkingReleaseReason.RECORD_DELETED))
        advanceUntilIdle()

        assertEquals(emptyList(), vm.state.value.activeSessions.map { it.id })
        assertEquals(0, reportScheduler.scheduleCallCount)
    }

    // ── StartDrivingDetection — vehicle-scoped manual arming ──────────────────

    @Test
    fun `should_set_the_declared_vehicle_active_before_arming_when_it_is_inactive`() = runTest {
        val activeVeh = Vehicle(
            id = "veh-active", userId = "user-1", brand = "Seat", model = "Leon",
            sizeCategory = com.rndeveloper.paparcar.domain.model.VehicleSize.MEDIUM_SUV,
        )
        val inactiveVeh = Vehicle(
            id = "veh-inactive", userId = "user-1", brand = "Toyota", model = "Corolla",
            sizeCategory = com.rndeveloper.paparcar.domain.model.VehicleSize.MEDIUM_SUV,
        )
        vehicleRepo = FakeVehicleRepository(defaultVehicle = activeVeh, extraVehicles = listOf(inactiveVeh))
        vm = buildVm()

        // Declaring the inactive car makes it active first, then arms. [VEH-ACTIVE-FENCE-001]
        vm.handleIntent(HomeIntent.StartDrivingDetection(vehicleId = "veh-inactive"))
        advanceUntilIdle()

        assertEquals(listOf("veh-inactive"), vehicleRepo.setActiveCalls)
        assertEquals(1, manualParkingDetection.startCallCount)
    }

    @Test
    fun `should_arm_without_re-setting_active_when_the_declared_vehicle_is_already_active`() = runTest {
        val activeVeh = Vehicle(
            id = "veh-active", userId = "user-1", brand = "Seat", model = "Leon",
            sizeCategory = com.rndeveloper.paparcar.domain.model.VehicleSize.MEDIUM_SUV,
        )
        vehicleRepo = FakeVehicleRepository(defaultVehicle = activeVeh)
        vm = buildVm()

        // Already the active car — no redundant setActive, just arm.
        vm.handleIntent(HomeIntent.StartDrivingDetection(vehicleId = "veh-active"))
        advanceUntilIdle()

        assertTrue(vehicleRepo.setActiveCalls.isEmpty())
        assertEquals(1, manualParkingDetection.startCallCount)
    }

    // ── StopDetection — the user's veto on the live trip [DET-STOP-BUTTON-001] ─

    @Test
    fun `should_stop_the_live_trip_by_user_command_when_StopDetection_is_handled`() = runTest {
        vm = buildVm()

        vm.handleIntent(HomeIntent.StopDetection)
        advanceUntilIdle()

        // The dedicated command, NOT the manual-park cancel: only this one stamps the outcome and
        // opens the quiet period. [DET-MANUAL-CANCEL-001] keeps its own meaning.
        assertEquals(1, manualParkingDetection.stopByUserCallCount)
        assertEquals(0, manualParkingDetection.stopCallCount)
    }

    // ── The in-app "did you park?" question [DET-ASK-STATE-001] ───────────────

    @Test
    fun `should_surface_the_open_question_when_a_prompt_was_posted`() = runTest {
        vm = buildVm()

        prefs.pendingPromptWindow.value = PendingPromptWindow(
            shownAtMs = Clock.System.now().toEpochMilliseconds(),
            vehicleName = "Škoda Kamiq",
        )
        advanceUntilIdle()

        assertEquals("Škoda Kamiq", vm.state.value.promptWindow?.vehicleName)
    }

    @Test
    fun `should_not_surface_a_question_whose_response_window_has_passed`() = runTest {
        // The swipe / process-death case: the slot is still written because nothing called dismiss.
        vm = buildVm()
        val timeout = com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig().confirmationResponseTimeoutMs

        prefs.pendingPromptWindow.value = PendingPromptWindow(
            shownAtMs = Clock.System.now().toEpochMilliseconds() - timeout - 1_000L,
        )
        advanceUntilIdle()

        assertNull(vm.state.value.promptWindow)
    }

    @Test
    fun `should_send_the_confirm_command_when_the_row_answers_yes`() = runTest {
        vm = buildVm()

        vm.handleIntent(HomeIntent.AnswerParkingPrompt(parked = true))
        advanceUntilIdle()

        assertEquals(listOf(true), manualParkingDetection.promptAnswers)
        // The row answers the QUESTION; it never doubles as the trip veto. [DET-STOP-BUTTON-001]
        assertEquals(0, manualParkingDetection.stopByUserCallCount)
    }

    @Test
    fun `should_send_the_deny_command_when_the_row_answers_still_driving`() = runTest {
        vm = buildVm()

        vm.handleIntent(HomeIntent.AnswerParkingPrompt(parked = false))
        advanceUntilIdle()

        // The two buttons must not collapse into one command — the whole point of asking.
        assertEquals(listOf(false), manualParkingDetection.promptAnswers)
    }

    @Test
    fun `should_clear_the_question_when_the_notification_channel_closes_it`() = runTest {
        // Answering in the TRAY dismisses the notification, which clears the slot. The row must
        // disappear through that same flow — no second bookkeeping path in the app.
        vm = buildVm()
        prefs.pendingPromptWindow.value = PendingPromptWindow(Clock.System.now().toEpochMilliseconds())
        advanceUntilIdle()
        assertNotNull(vm.state.value.promptWindow)

        prefs.clearPendingPromptWindow()
        advanceUntilIdle()

        assertNull(vm.state.value.promptWindow)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // [F1] BUG-1..5 + F1-bis regression coverage — added 2026-06-10
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `should_persist_radiusMeters_and_isPrivate_on_ConfirmAddZone`() = runTest {
        vm.handleIntent(HomeIntent.EnterAddZoneMode(lat = 40.0, lon = -3.7))
        vm.handleIntent(HomeIntent.UpdateAddingZoneName("Garaje"))
        vm.handleIntent(HomeIntent.SetZoneRadius(420f))
        vm.handleIntent(HomeIntent.SetZoneIsPrivate(true))
        vm.handleIntent(HomeIntent.ConfirmAddZone)

        val saved = zoneRepo.savedZone
        assertEquals("Garaje", saved?.name)
        assertEquals(420f, saved?.radiusMeters)
        assertEquals(true, saved?.isPrivate)
    }

    @Test
    fun `should_reset_radius_and_isPrivate_to_defaults_on_EnterAddZoneMode`() = runTest {
        // Leak state from a prior session
        vm.handleIntent(HomeIntent.EnterAddZoneMode(lat = 40.0, lon = -3.7))
        vm.handleIntent(HomeIntent.SetZoneRadius(420f))
        vm.handleIntent(HomeIntent.SetZoneIsPrivate(true))
        vm.handleIntent(HomeIntent.ExitAddZoneMode)

        // Re-enter — form must NOT pre-fill the previous values.
        vm.handleIntent(HomeIntent.EnterAddZoneMode(lat = 41.0, lon = -4.0))

        val s = vm.state.value
        assertEquals(com.rndeveloper.paparcar.domain.model.Zone.DEFAULT_RADIUS_METERS, s.addingZoneRadius)
        assertEquals(false, s.addingZoneIsPrivate)
        assertEquals("", s.addingZoneName)
    }

    @Test
    fun `should_emit_ZoneSaved_only_on_successful_save`() = runTest {
        vm.handleIntent(HomeIntent.EnterAddZoneMode(lat = 40.0, lon = -3.7))
        vm.handleIntent(HomeIntent.UpdateAddingZoneName("Casa"))
        vm.effect.test {
            vm.handleIntent(HomeIntent.ConfirmAddZone)
            assertIs<HomeEffect.ZoneSaved>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should_emit_ShowError_and_keep_form_when_save_zone_fails`() = runTest {
        // Inject failure on the repo
        zoneRepo.saveZoneResult = Result.failure(RuntimeException("firestore down"))
        vm.handleIntent(HomeIntent.EnterAddZoneMode(lat = 40.0, lon = -3.7))
        vm.handleIntent(HomeIntent.UpdateAddingZoneName("Casa"))
        vm.handleIntent(HomeIntent.SetZoneRadius(150f))
        vm.handleIntent(HomeIntent.SetZoneIsPrivate(true))
        vm.effect.test {
            vm.handleIntent(HomeIntent.ConfirmAddZone)
            assertIs<HomeEffect.ShowError>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        // Form stays intact so user can retry — VM stays in AddingZone.
        val s = vm.state.value
        assertEquals(HomeMode.AddingZone, s.mode)
        assertEquals("Casa", s.addingZoneName)
        assertEquals(150f, s.addingZoneRadius)
        assertEquals(true, s.addingZoneIsPrivate)
        assertEquals(false, s.isSavingZone)
    }

    // ── Widget "show parking" ask [WIDGET-ACTIONS-002] ───────────────────────

    @Test
    fun `should_selectTheParkingAndFocusTheCamera_when_theWidgetSessionIsStillActive`() = runTest(testDispatcher) {
        val session = UserParking(
            id = "session-widget", userId = "user-123", vehicleId = "veh-1",
            location = location, isActive = true,
        )
        parkingRepo = FakeUserParkingRepository(initialSessions = listOf(session))
        val bus = com.rndeveloper.paparcar.domain.event.ShowParkingAskEventBus()
        vm = buildVm(showParkingAskBus = bus)
        advanceUntilIdle()

        vm.effect.test {
            bus.ask("session-widget")
            advanceUntilIdle()
            // The card opens with EVERY option — the launcher never got a one-tap verb.
            assertEquals(HomeSelection.Parking("session-widget"), vm.state.value.selection)
            val move = awaitItem()
            assertIs<HomeEffect.MoveCameraTo>(move)
            assertEquals(location.latitude, move.lat)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should_stayQuiet_when_theWidgetSessionWasAlreadyReleased`() = runTest(testDispatcher) {
        // A STALE ask has nothing left to show.
        val other = UserParking(
            id = "session-other", userId = "user-123", vehicleId = "veh-1",
            location = location, isActive = true,
        )
        parkingRepo = FakeUserParkingRepository(initialSessions = listOf(other))
        val bus = com.rndeveloper.paparcar.domain.event.ShowParkingAskEventBus()
        vm = buildVm(showParkingAskBus = bus)
        advanceUntilIdle()

        vm.effect.test {
            bus.ask("session-gone")
            advanceUntilIdle()
            assertEquals(null, vm.state.value.selection)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Zone limit [FREE-LIMITS-001] ─────────────────────────────────────────

    private fun zoneFixture(id: String, name: String) = com.rndeveloper.paparcar.domain.model.Zone(
        id = id, userId = "u1", name = name, lat = 40.0, lon = -3.7,
        iconKey = "home", createdAt = 0L,
    )

    @Test
    fun `should_openThePaywall_andNotPersist_when_freeCreatesAThirdZone`() = runTest {
        zoneRepo.zones = listOf(zoneFixture("z1", "Casa"), zoneFixture("z2", "Trabajo"))
        vm = buildVm()

        vm.handleIntent(HomeIntent.EnterAddZoneMode(lat = 40.0, lon = -3.7))
        vm.handleIntent(HomeIntent.UpdateAddingZoneName("Gimnasio"))
        vm.effect.test {
            // EnterAddZoneMode frames the camera first — drain until the paywall shows up.
            vm.handleIntent(HomeIntent.ConfirmAddZone)
            var paywall = awaitItem()
            while (paywall !is HomeEffect.NavigateToPaywall) paywall = awaitItem()
            assertEquals(
                HomeEffect.NavigateToPaywall(com.rndeveloper.paparcar.domain.entitlement.PaywallReason.ZONES),
                paywall,
            )
            cancelAndIgnoreRemainingEvents()
        }
        // Soft gate: nothing persisted, and the form survives for after the upgrade.
        assertNull(zoneRepo.savedZone)
        val s = vm.state.value
        assertEquals(HomeMode.AddingZone, s.mode)
        assertEquals("Gimnasio", s.addingZoneName)
        assertEquals(false, s.isSavingZone)
    }

    @Test
    fun `should_stillSaveAnEdit_when_freeIsAtTheZoneLimit`() = runTest {
        // Editing is always free — the limit gates CREATION only, zones above it are conserved.
        zoneRepo.zones = listOf(zoneFixture("z1", "Casa"), zoneFixture("z2", "Trabajo"))
        vm = buildVm()

        vm.handleIntent(HomeIntent.EnterEditZoneMode("z1"))
        vm.handleIntent(HomeIntent.UpdateAddingZoneName("Casa nueva"))
        vm.handleIntent(HomeIntent.ConfirmAddZone)

        assertEquals("Casa nueva", zoneRepo.savedZone?.name)
    }

    @Test
    fun `should_saveTheThirdZone_when_plus`() = runTest {
        zoneRepo.zones = listOf(zoneFixture("z1", "Casa"), zoneFixture("z2", "Trabajo"))
        vm = buildVm()
        entitlementRepo.emitEntitlement(com.rndeveloper.paparcar.domain.model.Entitlement.PLUS)

        vm.handleIntent(HomeIntent.EnterAddZoneMode(lat = 40.0, lon = -3.7))
        vm.handleIntent(HomeIntent.UpdateAddingZoneName("Gimnasio"))
        vm.handleIntent(HomeIntent.ConfirmAddZone)

        assertEquals("Gimnasio", zoneRepo.savedZone?.name)
    }

    @Test
    fun `should_prefill_radius_and_isPrivate_on_EnterEditZoneMode`() = runTest {
        val zone = com.rndeveloper.paparcar.domain.model.Zone(
            id = "z1",
            userId = "u1",
            name = "Trabajo",
            lat = 41.0,
            lon = -4.0,
            iconKey = "work",
            createdAt = 0L,
            radiusMeters = 175f,
            isPrivate = true,
        )
        zoneRepo.zones = listOf(zone)
        // Recreate VM so observeZones picks it up at init
        vm = buildVm()

        vm.handleIntent(HomeIntent.EnterEditZoneMode("z1"))

        val s = vm.state.value
        assertEquals(HomeMode.AddingZone, s.mode)
        assertEquals("z1", s.editingZoneId)
        assertEquals("Trabajo", s.addingZoneName)
        assertEquals(175f, s.addingZoneRadius)
        assertEquals(true, s.addingZoneIsPrivate)
    }

    // ── UI-ZONE-MANAGE-001 · zone framing + deleting the zone you are editing ──

    @Test
    fun `should_frame_the_camera_for_zone_editing_when_entering_add_zone_mode`() = runTest {
        vm.effect.test {
            vm.handleIntent(HomeIntent.EnterAddZoneMode(lat = 40.0, lon = -3.7))
            val move = awaitItem()
            assertIs<HomeEffect.MoveCameraTo>(move)
            // A 500 m radius doesn't fit at the navigation zoom — the frame says so, the screen
            // turns it into a number.
            assertEquals(CameraFrame.ZoneEditing, move.frame)
            assertEquals(40.0, move.lat)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should_frame_the_camera_for_zone_editing_when_entering_edit_zone_mode`() = runTest {
        zoneRepo.zones = listOf(
            com.rndeveloper.paparcar.domain.model.Zone(
                id = "z1", userId = "u1", name = "Trabajo", lat = 41.0, lon = -4.0,
                iconKey = "work", createdAt = 0L,
            ),
        )
        vm = buildVm()

        vm.effect.test {
            vm.handleIntent(HomeIntent.EnterEditZoneMode("z1"))
            val move = awaitItem()
            assertIs<HomeEffect.MoveCameraTo>(move)
            assertEquals(CameraFrame.ZoneEditing, move.frame)
            assertEquals(41.0, move.lat)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should_leave_zone_mode_when_deleting_the_zone_being_edited`() = runTest {
        zoneRepo.zones = listOf(
            com.rndeveloper.paparcar.domain.model.Zone(
                id = "z1", userId = "u1", name = "Casa", lat = 41.0, lon = -4.0,
                iconKey = "home", createdAt = 0L,
            ),
        )
        vm = buildVm()
        vm.handleIntent(HomeIntent.EnterEditZoneMode("z1"))

        vm.handleIntent(HomeIntent.DeleteZone("z1"))
        advanceUntilIdle()

        // The modal can't stay open editing a zone that no longer exists.
        val s = vm.state.value
        assertEquals(HomeMode.Browse, s.mode)
        assertEquals(null, s.editingZoneId)
    }

    @Test
    fun `should_keep_the_zone_peek_open_while_its_delete_is_in_flight`() = runTest {
        // [UI-A-SPINNER-LIVES-IN-THE-BUTTON-THAT-WAS-PRESSED-001] The peek used to close the moment
        // the tap landed, which left the destructive write running with nothing on screen saying so.
        // It now holds — with the confirm's own spinner — until the zone is really gone.
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        zoneRepo.zones = listOf(
            com.rndeveloper.paparcar.domain.model.Zone(
                id = "z1", userId = "u1", name = "Casa", lat = 41.0, lon = -4.0,
                iconKey = "home", createdAt = 0L,
            ),
        )
        vm = buildVm()
        advanceUntilIdle()
        vm.handleIntent(HomeIntent.EnterEditZoneMode("z1"))
        advanceUntilIdle()

        vm.handleIntent(HomeIntent.DeleteZone("z1"))

        assertEquals(HomeMode.AddingZone, vm.state.value.mode)
        assertTrue("z1" in vm.state.value.deletingZoneIds)
        assertTrue(vm.state.value.toPeekSlice().isDeletingZone)

        advanceUntilIdle()
        assertEquals(HomeMode.Browse, vm.state.value.mode)
    }

    @Test
    fun `should_keep_the_parking_peek_open_while_the_record_delete_is_in_flight`() = runTest {
        // The third button of the "correct parking" modal. Same contract as deleting a vehicle:
        // the confirm holds its spinner and the peek only leaves once the write landed.
        // [UI-A-SPINNER-LIVES-IN-THE-BUTTON-THAT-WAS-PRESSED-001]
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val session = UserParking(
            id = "session-1", userId = "user-123", vehicleId = "veh-1",
            location = location, isActive = true,
        )
        parkingRepo = FakeUserParkingRepository(initialSessions = listOf(session))
        vm = buildVm()
        advanceUntilIdle()
        vm.handleIntent(HomeIntent.EnterAddParkingMode(initialGps = location, editingParkingId = session.id))
        advanceUntilIdle()

        vm.handleIntent(
            HomeIntent.ReleaseParking(sessionId = session.id, reason = ParkingReleaseReason.RECORD_DELETED),
        )

        assertIs<HomeMode.AddingParking>(vm.state.value.mode)
        assertEquals(ParkingReleaseReason.RECORD_DELETED, vm.state.value.releasingReason)

        advanceUntilIdle()
        assertEquals(HomeMode.Browse, vm.state.value.mode)
        assertNull(vm.state.value.releasingReason)
    }

    @Test
    fun `should_stay_in_zone_mode_when_deleting_a_different_zone`() = runTest {
        zoneRepo.zones = listOf(
            com.rndeveloper.paparcar.domain.model.Zone(
                id = "z1", userId = "u1", name = "Casa", lat = 41.0, lon = -4.0,
                iconKey = "home", createdAt = 0L,
            ),
            com.rndeveloper.paparcar.domain.model.Zone(
                id = "z2", userId = "u1", name = "Trabajo", lat = 41.1, lon = -4.1,
                iconKey = "work", createdAt = 0L,
            ),
        )
        vm = buildVm()
        vm.handleIntent(HomeIntent.EnterEditZoneMode("z1"))

        vm.handleIntent(HomeIntent.DeleteZone("z2"))
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals(HomeMode.AddingZone, s.mode)
        assertEquals("z1", s.editingZoneId)
    }

    @Test
    fun `should_emit_ShowError_when_confirmReportSpot_use_case_throws`() = runTest {
        reportScheduler.shouldThrow = true
        vm.handleIntent(HomeIntent.EnterReportMode(lat = 40.0, lon = -3.7))
        vm.effect.test {
            vm.handleIntent(HomeIntent.ConfirmReportSpot)
            // First effect is the ShowError from the failure branch.
            val first = awaitItem()
            assertIs<HomeEffect.ShowError>(first)
            cancelAndIgnoreRemainingEvents()
        }
        // isReporting must reset; mode stays Reporting so user can retry the report.
        val s = vm.state.value
        assertEquals(false, s.isReporting)
        assertEquals(HomeMode.Reporting, s.mode)
    }

    @Test
    fun `should_pass_active_vehicle_carbody_and_null_size_when_user_did_not_pick`() = runTest {
        // Behaviour [F1-bis]: when the user opens "Avisar plaza" and confirms without
        // picking a size, null is interpreted as the explicit "Indefinido" choice and
        // is forwarded verbatim. Carbody, on the other hand, has no picker in the
        // report flow — it falls back to the active vehicle so the public Spot still
        // shows "Left by …".
        val activeVehicle = Vehicle(
            id = "v1",
            userId = "u1",
            sizeCategory = com.rndeveloper.paparcar.domain.model.VehicleSize.LARGE_SEDAN,
            carbodyType = com.rndeveloper.paparcar.domain.model.CarbodyType.SEDAN,
            isActive = true,
        )
        vehicleRepo = FakeVehicleRepository(defaultVehicle = activeVehicle)
        vm = buildVm()

        vm.handleIntent(HomeIntent.EnterReportMode(lat = 40.0, lon = -3.7))
        vm.handleIntent(HomeIntent.ConfirmReportSpot)
        advanceUntilIdle()

        assertEquals(com.rndeveloper.paparcar.domain.model.CarbodyType.SEDAN, reportScheduler.lastCarbodyType)
        assertNull(reportScheduler.lastSizeCategory)
    }

    @Test
    fun `should_prefer_user_selected_size_over_active_vehicle_size_on_reportSpot`() = runTest {
        val activeVehicle = Vehicle(
            id = "v1",
            userId = "u1",
            sizeCategory = com.rndeveloper.paparcar.domain.model.VehicleSize.LARGE_SEDAN,
            carbodyType = com.rndeveloper.paparcar.domain.model.CarbodyType.SEDAN,
            isActive = true,
        )
        vehicleRepo = FakeVehicleRepository(defaultVehicle = activeVehicle)
        vm = buildVm()

        vm.handleIntent(HomeIntent.EnterReportMode(lat = 40.0, lon = -3.7))
        vm.handleIntent(HomeIntent.SetReportingSize(com.rndeveloper.paparcar.domain.model.VehicleSize.MICRO_SMALL))
        vm.handleIntent(HomeIntent.ConfirmReportSpot)
        advanceUntilIdle()

        // User-picked size wins; carbody still comes from the active vehicle.
        assertEquals(com.rndeveloper.paparcar.domain.model.VehicleSize.MICRO_SMALL, reportScheduler.lastSizeCategory)
        assertEquals(com.rndeveloper.paparcar.domain.model.CarbodyType.SEDAN, reportScheduler.lastCarbodyType)
    }

    @Test
    fun `should_clear_selection_when_entering_AddingZone`() = runTest {
        vm = buildVm()

        // Pre-condition: an item (spot / parking) is selected.
        // Zones are no longer selectable — SelectZone just moves the camera — so the
        // selection field exercised here is the unified [selection]. [ZONE-NOSEL-001]
        vm.handleIntent(HomeIntent.SelectItem(HomeSelection.Spot("item-1")))
        assertEquals(HomeSelection.Spot("item-1"), vm.state.value.selection)

        // Entering an add-mode must clear any active selection: selection and the
        // add-modes (Reporting / AddingZone / AddingParking) are mutually exclusive.
        vm.handleIntent(HomeIntent.EnterAddZoneMode(lat = 40.0, lon = -3.7))

        val s = vm.state.value
        assertEquals(HomeMode.AddingZone, s.mode)
        assertNull(s.selection)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // [DET-WATCH-REACTIVATE-001] "Reactivate" on the interrupted-watch row
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `should_rebuild_the_departure_watch_when_the_user_taps_reactivate`() = runTest {
        vm = buildVm()

        vm.handleIntent(HomeIntent.ResumeWatch)
        advanceUntilIdle()

        // The bug this replaces: the CTA fired the battery-exemption request, which restarts nothing.
        assertEquals(1, departureWatchResumer.resumeCallCount)
        assertEquals("home-cta", departureWatchResumer.lastSource)
        assertEquals(true, departureWatchResumer.lastForce, "an explicit tap must bypass the retry cooldown")
    }

    @Test
    fun `should_not_show_an_error_when_the_watch_comes_back`() = runTest {
        vm = buildVm()

        vm.effect.test {
            vm.handleIntent(HomeIntent.ResumeWatch)
            // Success speaks for itself: the row becomes "watching" when presence flips. No snackbar.
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should_report_the_failure_when_the_watch_cannot_be_rebuilt`() = runTest {
        vm = buildVm()
        departureWatchResumer.resumeSucceeds = false

        vm.effect.test {
            vm.handleIntent(HomeIntent.ResumeWatch)
            // A tap that achieves nothing must SAY so — the original complaint was a mute button.
            assertEquals(HomeEffect.ShowError(PaparcarError.Detection.WatchResumeFailed), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Paparcar Plus: el toggle de alerta del form de zona [MONEY-001] ────────
    // La campana de la cabecera y su diálogo se retiraron: el maestro vive en Ajustes y el
    // por-zona en el modal de la zona. [UI-ZONE-ALERTS-ARE-CONFIGURED-IN-SETTINGS-001]

    @Test
    fun `should_gateEnablingTheFormToggle_when_free`() = runTest {
        vm = buildVm()

        vm.effect.test {
            vm.handleIntent(HomeIntent.SetZoneNotify(true))
            assertEquals(
                HomeEffect.NavigateToPaywall(com.rndeveloper.paparcar.domain.entitlement.PaywallReason.ZONE_ALERTS),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(false, vm.state.value.addingZoneNotify, "the switch stays OFF after the gated attempt")
    }

    /**
     * El escenario que cubría el test del diálogo — "un Plus enciende la alerta de UNA zona y
     * queda escrita" — sigue existiendo: sólo cambió de puerta. Ahora se prueba por la que queda,
     * el modal de la zona. Sin este test, retirar la campana habría borrado en silencio la única
     * prueba de que `notifyOnSpot` llega al repositorio.
     * [UI-ZONE-ALERTS-ARE-CONFIGURED-IN-SETTINGS-001]
     */
    @Test
    fun `should_writeTheZoneAlertFlag_when_plusEnablesItInTheZoneForm`() = runTest {
        entitlementRepo.emitEntitlement(com.rndeveloper.paparcar.domain.model.Entitlement.PLUS)
        zoneRepo.zones = listOf(zoneFixture("z1", "Casa"))
        vm = buildVm()

        vm.handleIntent(HomeIntent.EnterEditZoneMode("z1"))
        vm.handleIntent(HomeIntent.SetZoneNotify(true))
        vm.handleIntent(HomeIntent.ConfirmAddZone)

        assertEquals("z1", zoneRepo.savedZone!!.id)
        assertEquals(true, zoneRepo.savedZone!!.notifyOnSpot)
    }

    // ── Closing the guided checklist ──────────────────────────────────────────
    // [ONBOARDING-A-TUTORIAL-IS-REMEMBERED-BY-THE-ACCOUNT-NOT-THE-DEVICE-001]

    /**
     * Both stores, from one tap. The device preference is what makes the card disappear now; the
     * account is what stops it coming back on the next install — which is the whole bug: the user
     * reinstalled, signed in, and the tutorial reappeared mid-way.
     */
    @Test
    fun `should_rememberTheDismissalOnDeviceAndAccount_when_theChecklistIsClosed`() = runTest {
        vm = buildVm()

        vm.handleIntent(HomeIntent.DismissFirstSteps)

        assertTrue(prefs.firstStepsDismissed.value, "the card must go now, without waiting for the network")
        assertEquals(1, profileRepo.firstStepsDismissedWrites.size, "and the account has to learn it")
        assertTrue(profileRepo.firstStepsDismissedWrites.single() != null, "stamped, not cleared")
    }

    /**
     * A remote write that fails is logged, never surfaced: the user's tap already did what they
     * asked. Pinned because the alternative — an error snackbar about a profile field — would be
     * app plumbing shown to someone who just wanted the card gone, and the splash repairs the miss
     * on its own at the next online start.
     */
    @Test
    fun `should_stayQuiet_when_theAccountWriteFails`() = runTest {
        vm = buildVm()
        profileRepo.firstStepsDismissedResult = Result.failure(IllegalStateException("offline"))

        vm.effect.test {
            vm.handleIntent(HomeIntent.DismissFirstSteps)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(prefs.firstStepsDismissed.value, "the local half still landed")
    }

    // ── Dónde repostar — la sección solo responde lo que preguntó ─────────────
    // [FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001]

    private suspend fun saveActiveCar(fuelType: com.rndeveloper.paparcar.domain.model.FuelType?) {
        vehicleRepo.saveVehicle(
            com.rndeveloper.paparcar.domain.model.Vehicle(
                id = "v1", userId = "mock_user_001", brand = "Acura", model = "EL",
                sizeCategory = com.rndeveloper.paparcar.domain.model.VehicleSize.LARGE_SEDAN,
                vehicleType = com.rndeveloper.paparcar.domain.model.VehicleType.CAR, isActive = true,
                fuelType = fuelType,
            ),
        )
    }

    @Test
    fun `should_sayMissingFuel_andAskNobody_when_theActiveCarHasNoFuelType`() = runTest(testDispatcher) {
        // ⛔ Measured on device: a pre-fuelType car made the section unfold into a fabricated
        // "no stations within 5 km" without a single request leaving the phone. The empty state is
        // a verdict about the world; this is its own sentence.
        fuelSource.result = FuelStationsResult(listOf(spyStation()))
        saveActiveCar(fuelType = null)
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)
        advanceUntilIdle()

        vm.handleIntent(HomeIntent.SetNearbyLens(HomeNearbyLens.FUEL))
        advanceUntilIdle()

        val fuel = vm.state.value.fuel
        assertEquals(HomeNearbyLens.FUEL, vm.state.value.nearbyLens, "the user tapped the chip")
        assertTrue(fuel.missingFuelType)
        assertTrue(!fuel.failed)
        // The LIST says its own sentence (the lane renders `missingFuelType` before any row)…
        // …but the MAP still has a question: the stations are there whatever the car burns, and
        // capsules without a figure are honest where an empty map would just be wrong. ONE fuel-less
        // ask, into the ONE store both read. [NEARBY-ONE-STORE-FOLLOWS-THE-CAMERA-001]
        assertEquals(null, fuel.fetchedFor, "asked with no fuel, and it says so")
        assertEquals(1, fuelSource.askCount)
        assertTrue(vm.state.value.toMapSlice().fuelStations.isNotEmpty())
        assertTrue(vm.state.value.toMapSlice().fuelStations.all { it.price == null })
    }

    // ── The provenance line of a complement. [ONB-THE-MAP-ANSWERS-MORE-THAN-SPOTS-001] ──

    @Test
    fun `should_introduceTheComplement_andBankIt_when_itsLaneIsEnteredForTheFirstTime`() =
        runTest(testDispatcher) {
            permissions.emit(FakePermissionManager.allGranted())
            locationDataSource.emitHighAccuracy(location)
            advanceUntilIdle()

            vm.handleIntent(HomeIntent.SetNearbyLens(HomeNearbyLens.PARKINGS))
            advanceUntilIdle()

            assertEquals(NearbyComplement.CAR_PARKS, vm.state.value.nearbyIntro)
            // BANKED at the same moment it is shown: the line stays up for this visit because it is
            // a second field, not because the write was postponed.
            assertTrue(NearbyComplement.CAR_PARKS in prefs.introducedComplements.value)
        }

    @Test
    fun `should_notIntroduceItAgain_when_theLaneIsLeftAndEnteredAgain`() = runTest(testDispatcher) {
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)
        advanceUntilIdle()

        vm.handleIntent(HomeIntent.SetNearbyLens(HomeNearbyLens.PARKINGS))
        advanceUntilIdle()
        vm.handleIntent(HomeIntent.SetNearbyLens(HomeNearbyLens.SPOTS))
        advanceUntilIdle()
        // Leaving takes the line with it, so coming back cannot re-run an introduction.
        assertEquals(null, vm.state.value.nearbyIntro)

        vm.handleIntent(HomeIntent.SetNearbyLens(HomeNearbyLens.PARKINGS))
        advanceUntilIdle()

        assertEquals(null, vm.state.value.nearbyIntro, "it was already introduced")
    }

    @Test
    fun `should_neverIntroduceSpots_when_theirLaneIsEntered`() = runTest(testDispatcher) {
        // ⛔ A free spot has no third party to name: the user IS where it comes from.
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)
        advanceUntilIdle()

        vm.handleIntent(HomeIntent.SetNearbyLens(HomeNearbyLens.FUEL))
        advanceUntilIdle()
        vm.handleIntent(HomeIntent.SetNearbyLens(HomeNearbyLens.SPOTS))
        advanceUntilIdle()

        assertEquals(null, vm.state.value.nearbyIntro)
        assertEquals(setOf(NearbyComplement.FUEL), prefs.introducedComplements.value)
    }

    @Test
    fun `should_completeTheExploreStep_when_aComplementLaneHasBeenEntered`() = runTest(testDispatcher) {
        // The step and the line read the SAME persisted fact — this is the assertion that keeps
        // them from becoming two latches that can disagree.
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)
        advanceUntilIdle()
        assertTrue(FirstStep.EXPLORE_NEARBY !in vm.state.value.firstSteps.done)

        vm.handleIntent(HomeIntent.SetNearbyLens(HomeNearbyLens.FUEL))
        advanceUntilIdle()

        assertTrue(FirstStep.EXPLORE_NEARBY in vm.state.value.firstSteps.done)
    }

    @Test
    fun `should_reportFailure_notAnEmptyList_when_thereIsNoFix`() = runTest(testDispatcher) {
        // No fix means the question could not be asked — that is the failure sentence, and it must
        // never surface as "no stations within 5 km".
        saveActiveCar(fuelType = com.rndeveloper.paparcar.domain.model.FuelType.GASOLINE)
        advanceUntilIdle()

        vm.handleIntent(HomeIntent.SetNearbyLens(HomeNearbyLens.FUEL))
        advanceUntilIdle()

        val fuel = vm.state.value.fuel
        assertEquals(HomeNearbyLens.FUEL, vm.state.value.nearbyLens)
        assertTrue(fuel.failed)
        assertTrue(!fuel.missingFuelType)
        // Not even the map asked: with no fix there is no centre to ask around.
        assertEquals(0, fuelSource.askCount)
    }

    @Test
    fun `should_ask_andShowTheAnswer_when_theCarHasFuelAndAFix`() = runTest(testDispatcher) {
        fuelSource.result = FuelStationsResult(
            listOf(
                com.rndeveloper.paparcar.domain.fuel.FuelStation(
                    id = "1", name = "Repsol", address = null, town = null,
                    latitude = 40.4268, longitude = -3.7038,
                    prices = mapOf(
                        com.rndeveloper.paparcar.domain.model.FuelType.GASOLINE to
                            com.rndeveloper.paparcar.domain.fuel.StationPrice(
                                com.rndeveloper.paparcar.domain.model.Money(1.789, "EUR"),
                            ),
                    ),
                ),
            ),
        )
        saveActiveCar(fuelType = com.rndeveloper.paparcar.domain.model.FuelType.GASOLINE)
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)
        advanceUntilIdle()

        vm.handleIntent(HomeIntent.SetNearbyLens(HomeNearbyLens.FUEL))
        advanceUntilIdle()

        val fuel = vm.state.value.fuel
        assertEquals(1, fuelSource.askCount)
        assertEquals(1, fuel.stations.size)
        assertTrue(!fuel.missingFuelType)
        assertTrue(!fuel.failed)
    }

    @Test
    fun `should_askAgain_onReopen_after_theCarGainsAFuelType`() = runTest(testDispatcher) {
        // The recovery path the state exists for: the user follows the sentence, sets the fuel in
        // Vehicles, comes back, and reopening now asks instead of repeating the excuse.
        saveActiveCar(fuelType = null)
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)
        advanceUntilIdle()

        vm.handleIntent(HomeIntent.SetNearbyLens(HomeNearbyLens.FUEL))
        advanceUntilIdle()
        assertTrue(vm.state.value.fuel.missingFuelType)

        saveActiveCar(fuelType = com.rndeveloper.paparcar.domain.model.FuelType.GASOLINE)
        advanceUntilIdle()
        vm.handleIntent(HomeIntent.SetNearbyLens(HomeNearbyLens.SPOTS)) // leave the lane
        vm.handleIntent(HomeIntent.SetNearbyLens(HomeNearbyLens.FUEL))  // come back — now there is a question
        advanceUntilIdle()

        val fuel = vm.state.value.fuel
        // The fuel-less car's entry asked ONCE for the map (capsules without a figure are honest);
        // coming back with a fuel type asks the priced question. Two questions, two asks.
        assertEquals(2, fuelSource.askCount)
        assertTrue(!fuel.missingFuelType)
        assertEquals(
            com.rndeveloper.paparcar.domain.model.FuelType.GASOLINE,
            fuel.fetchedFor,
        )
    }

    // ── El carril de "cerca de ti" [HOME-A-SECTION-MUST-NOT-SIT-BEHIND-ANOTHERS-LIST-001] ──

    @Test
    fun `should_openOnSpots_when_theViewModelStarts`() = runTest(testDispatcher) {
        // ⛔ Nothing is restored: the lens is not a preference. Home is the AHORA, and a cold start
        // opens on the spots the community is offering — never on wherever the last session wandered.
        advanceUntilIdle()
        assertEquals(HomeNearbyLens.SPOTS, vm.state.value.nearbyLens)
        // …and with no complement painted, so a fresh map is spot pins and nothing else.
        assertTrue(vm.state.value.toMapSlice().fuelStations.isEmpty())
        assertTrue(vm.state.value.toMapSlice().publicParkings.isEmpty())
    }

    @Test
    fun `should_notReask_when_theSameLensIsChosenTwice`() = runTest(testDispatcher) {
        // The chip of the lane you are already in is a no-op, not a second request. A toggle here
        // would have made a double tap mean "leave", which is not what a chip that names its
        // destination says.
        fuelSource.result = FuelStationsResult(listOf(spyStation()))
        saveActiveCar(fuelType = com.rndeveloper.paparcar.domain.model.FuelType.GASOLINE)
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)
        advanceUntilIdle()

        vm.handleIntent(HomeIntent.SetNearbyLens(HomeNearbyLens.FUEL))
        vm.handleIntent(HomeIntent.SetNearbyLens(HomeNearbyLens.FUEL))
        vm.handleIntent(HomeIntent.SetNearbyLens(HomeNearbyLens.FUEL))
        advanceUntilIdle()

        assertEquals(HomeNearbyLens.FUEL, vm.state.value.nearbyLens)
        assertEquals(1, fuelSource.askCount)
    }

    // ── La capa de gasolineras del mapa [FUEL-STATIONS-MAP-AND-PEEK-001] ─────

    private fun spyStation(id: String = "st1") = com.rndeveloper.paparcar.domain.fuel.FuelStation(
        id = id, name = "Repsol", address = null, town = null,
        latitude = 40.4268, longitude = -3.7038,
        prices = mapOf(
            com.rndeveloper.paparcar.domain.model.FuelType.GASOLINE to
                com.rndeveloper.paparcar.domain.fuel.StationPrice(
                    com.rndeveloper.paparcar.domain.model.Money(1.789, "EUR"),
                ),
        ),
    )

    @Test
    fun `should_loadStations_andPersistTheChoice_when_theLayerTurnsOn`() = runTest(testDispatcher) {
        fuelSource.result = FuelStationsResult(listOf(spyStation()))
        saveActiveCar(fuelType = com.rndeveloper.paparcar.domain.model.FuelType.GASOLINE)
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)
        advanceUntilIdle()

        vm.handleIntent(HomeIntent.SetNearbyLens(HomeNearbyLens.FUEL))
        advanceUntilIdle()

        assertEquals(HomeNearbyLens.FUEL, vm.state.value.nearbyLens)
        // ⛔ Nothing persisted to check any more: the lane is where you are LOOKING, and every cold
        // start opens on the spots. And ONE request, not two: the lane asks around the user and the
        // map reads that same answer. [HOME-A-SECTION-MUST-NOT-SIT-BEHIND-ANOTHERS-LIST-001]
        assertEquals(1, fuelSource.askCount)
        // Asserted on the MAP SLICE — what the user actually sees — and not on one store: which of
        // the two holds the answer is an implementation detail, "there are pins on the map" is not.
        assertEquals(1, vm.state.value.toMapSlice().fuelStations.size)
    }

    @Test
    fun `should_askFuelLess_when_theLayerTurnsOn_withACarWithoutFuel`() = runTest(testDispatcher) {
        // The map wants markers even for a fuel-less car, and they come UNPRICED — a capsule with no
        // figure is honest, an empty map is not. (That the LIST says its own sentence instead is
        // covered by should_sayMissingFuel_andAskNobody_when_theActiveCarHasNoFuelType; this test is
        // about what the pins carry.) [FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001]
        fuelSource.result = FuelStationsResult(listOf(spyStation()))
        saveActiveCar(fuelType = null)
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)
        advanceUntilIdle()

        vm.handleIntent(HomeIntent.SetNearbyLens(HomeNearbyLens.FUEL))
        advanceUntilIdle()

        assertEquals(1, fuelSource.askCount)
        val map = vm.state.value.toMapSlice()
        assertEquals(1, map.fuelStations.size)
        assertEquals(null, map.fuelStations.first().price, "no car fuel, no figure on the capsule")
        assertEquals(null, map.fuelPricesFor, "and no fuel label to put beside a price nobody has")
    }

    @Test
    fun `should_reprice_when_theSectionOpens_afterAFuelLessLayerLoad`() = runTest(testDispatcher) {
        // A fuel-less answer is a DIFFERENT question from a priced one — fetchedFor is what stops a
        // list filtered for no fuel from posing as the answer for this car.
        fuelSource.result = FuelStationsResult(listOf(spyStation()))
        saveActiveCar(fuelType = null)
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)
        advanceUntilIdle()
        vm.handleIntent(HomeIntent.SetNearbyLens(HomeNearbyLens.FUEL))
        advanceUntilIdle()
        assertEquals(1, fuelSource.askCount)

        saveActiveCar(fuelType = com.rndeveloper.paparcar.domain.model.FuelType.GASOLINE)
        advanceUntilIdle()
        vm.handleIntent(HomeIntent.SetNearbyLens(HomeNearbyLens.SPOTS))
        vm.handleIntent(HomeIntent.SetNearbyLens(HomeNearbyLens.FUEL))
        advanceUntilIdle()

        assertEquals(2, fuelSource.askCount, "a priced lane cannot serve the fuel-less answer")
        assertEquals(
            com.rndeveloper.paparcar.domain.model.FuelType.GASOLINE,
            vm.state.value.fuel.fetchedFor,
        )
    }

    @Test
    fun `should_dropAStationSelection_when_theLayerTurnsOff`() = runTest(testDispatcher) {
        fuelSource.result = FuelStationsResult(listOf(spyStation()))
        saveActiveCar(fuelType = com.rndeveloper.paparcar.domain.model.FuelType.GASOLINE)
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)
        advanceUntilIdle()
        vm.handleIntent(HomeIntent.SetNearbyLens(HomeNearbyLens.FUEL))
        advanceUntilIdle()
        vm.handleIntent(HomeIntent.SelectItem(HomeSelection.FuelStation("st1")))
        advanceUntilIdle()
        assertTrue(vm.state.value.selectedFuelStation != null)

        // Leaving the lane is what stops the pins being drawn, so it is what drops the selection.
        // [HOME-A-SECTION-MUST-NOT-SIT-BEHIND-ANOTHERS-LIST-001]
        vm.handleIntent(HomeIntent.SetNearbyLens(HomeNearbyLens.SPOTS))
        advanceUntilIdle()

        assertEquals(HomeNearbyLens.SPOTS, vm.state.value.nearbyLens)
        assertEquals(null, vm.state.value.selection, "no peek may keep talking about an invisible pin")
    }

    // ── La capa sigue a la cámara [FUEL-LAYER-FOLLOWS-THE-CAMERA-001] ────────

    @Test
    fun `should_askAroundTheSettledCamera_when_itMovedPastHalfTheRadius`() = runTest(testDispatcher) {
        fuelSource.result = FuelStationsResult(listOf(spyStation()))
        saveActiveCar(fuelType = com.rndeveloper.paparcar.domain.model.FuelType.GASOLINE)
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)
        advanceUntilIdle()
        vm.handleIntent(HomeIntent.SetNearbyLens(HomeNearbyLens.FUEL))
        advanceUntilIdle()
        assertEquals(1, fuelSource.askCount)

        // The camera settles ~11 km away — past half the 5 km radius, inside the 20 km follow limit:
        // a different question, asked exactly once, THERE. (It used to be Alcalá at ~29 km, which
        // silently stopped being reachable when the limit dropped from 50 to 20 km.)
        vm.handleIntent(HomeIntent.CameraPositionChanged(lat = OTRO_BARRIO_LAT, lon = OTRO_BARRIO_LON))
        advanceUntilIdle()

        assertEquals(2, fuelSource.askCount)
        assertEquals(OTRO_BARRIO_LAT, fuelSource.lastQuery?.latitude)
        assertEquals(OTRO_BARRIO_LON, fuelSource.lastQuery?.longitude)
    }

    @Test
    fun `should_stayQuiet_when_theCameraBarelyMoved_orTheLayerIsOff`() = runTest(testDispatcher) {
        fuelSource.result = FuelStationsResult(listOf(spyStation()))
        saveActiveCar(fuelType = com.rndeveloper.paparcar.domain.model.FuelType.GASOLINE)
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)
        advanceUntilIdle()

        // El calentamiento de entrada ya preguntó una vez por el carril oculto.
        val trasCalentar = fuelSource.askCount
        assertEquals(1, trasCalentar)

        // Capa apagada: panear no pregunta a nadie.
        vm.handleIntent(HomeIntent.CameraPositionChanged(lat = OTRO_BARRIO_LAT, lon = OTRO_BARRIO_LON))
        advanceUntilIdle()
        assertEquals(trasCalentar, fuelSource.askCount)

        // Abrirla SÍ pregunta, porque la cámara se fue a otro barrio mientras estaba oculta.
        vm.handleIntent(HomeIntent.SetNearbyLens(HomeNearbyLens.FUEL))
        advanceUntilIdle()
        assertEquals(trasCalentar + 1, fuelSource.askCount)

        // A nervous ~1 km pan is not a new question.
        val trasAbrir = fuelSource.askCount
        vm.handleIntent(
            HomeIntent.CameraPositionChanged(
                lat = fuelSource.lastQuery!!.latitude + 0.01,
                lon = fuelSource.lastQuery!!.longitude,
            ),
        )
        advanceUntilIdle()
        assertEquals(trasAbrir, fuelSource.askCount)
    }

    // ── A substitute answer does not seal the ground ─────────────────────────
    // [FUEL-AN-UNKNOWN-COUNTRY-IS-NOT-A-COUNTRY-WITHOUT-PRICES-001]

    @Test
    fun `should_askAgainAtItsAnchor_when_theAnswerOnShowIsASubstitute`() = runTest(testDispatcher) {
        // The forecourts are real, but they came from the worldwide feed because the national one
        // could not be used — usually a geocoder that had not woken up yet, which is a moment long.
        // Measured on a fresh Play Store install in Spain: the anchor stamped on that attempt held
        // the whole area, and panning under 2,5 km never asked again, so the rows stayed priceless
        // until the user drove out of the zone.
        fuelSource.result = FuelStationsResult(listOf(spyStation()), didFallBack = true)
        saveActiveCar(fuelType = com.rndeveloper.paparcar.domain.model.FuelType.GASOLINE)
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)
        advanceUntilIdle()
        vm.handleIntent(HomeIntent.SetNearbyLens(HomeNearbyLens.FUEL))
        advanceUntilIdle()
        val afterOpening = fuelSource.askCount
        val anchor = vm.state.value.fuel.anchorLat to vm.state.value.fuel.anchorLon
        assertTrue(vm.state.value.fuel.didFallBack, "the lane is showing a substitute")

        // The SAME ~1 km pan that `should_stayQuiet_when_theCameraBarelyMoved_orTheLayerIsOff`
        // proves is not a new question. On a substitute it is a re-ask.
        vm.handleIntent(
            HomeIntent.CameraPositionChanged(lat = anchor.first!! + 0.01, lon = anchor.second!!),
        )
        advanceUntilIdle()

        assertEquals(afterOpening + 1, fuelSource.askCount, "a substitute is asked again")
        // ⛔ AT THE ANCHOR, not at the camera — same coordinates, so the same cache key, so no
        // network while the situation is unchanged. Asking at the camera instead would mint a new
        // key every 110 m of panning and put one Overpass request behind every camera settle.
        assertEquals(anchor.first, fuelSource.lastQuery?.latitude, "re-asked where the answer is about")
        assertEquals(anchor.second, fuelSource.lastQuery?.longitude)
        assertEquals(anchor, vm.state.value.fuel.anchorLat to vm.state.value.fuel.anchorLon)
    }

    @Test
    fun `should_stopReAsking_when_theSubstituteBecomesARealAnswer`() = runTest(testDispatcher) {
        // The re-ask exists to catch the moment the situation changes; once it has, the lane goes
        // back to the ordinary rule — a pan under half the radius owes the server nothing.
        fuelSource.result = FuelStationsResult(listOf(spyStation()), didFallBack = true)
        saveActiveCar(fuelType = com.rndeveloper.paparcar.domain.model.FuelType.GASOLINE)
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)
        advanceUntilIdle()
        vm.handleIntent(HomeIntent.SetNearbyLens(HomeNearbyLens.FUEL))
        advanceUntilIdle()
        val anchor = vm.state.value.fuel.anchorLat!! to vm.state.value.fuel.anchorLon!!

        // The geocoder wakes up: the national feed answers, and the substitute is gone.
        fuelSource.result = FuelStationsResult(listOf(spyStation()), didFallBack = false)
        vm.handleIntent(HomeIntent.CameraPositionChanged(lat = anchor.first + 0.01, lon = anchor.second))
        advanceUntilIdle()
        val afterRecovery = fuelSource.askCount
        assertTrue(!vm.state.value.fuel.didFallBack)

        vm.handleIntent(HomeIntent.CameraPositionChanged(lat = anchor.first + 0.015, lon = anchor.second))
        advanceUntilIdle()

        assertEquals(afterRecovery, fuelSource.askCount, "a real answer settles the ground again")
    }

    @Test
    fun `should_pruneAnOrphanedStationSelection_when_theViewportMovesOn`() = runTest(testDispatcher) {
        fuelSource.result = FuelStationsResult(listOf(spyStation("st1")))
        // A car with no fuel type, so the fuel-less path is the one exercised: the pruning is the same
        // for a priced answer, it is the one store either way. [NEARBY-ONE-STORE-FOLLOWS-THE-CAMERA-001]
        saveActiveCar(fuelType = null)
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)
        advanceUntilIdle()
        vm.handleIntent(HomeIntent.SetNearbyLens(HomeNearbyLens.FUEL))
        advanceUntilIdle()
        vm.handleIntent(HomeIntent.SelectItem(HomeSelection.FuelStation("st1")))
        advanceUntilIdle()
        assertTrue(vm.state.value.selectedFuelStation != null)

        // The next area has other stations; st1 is no longer on show → the selection goes too.
        fuelSource.result = FuelStationsResult(listOf(spyStation("st2")))
        vm.handleIntent(HomeIntent.CameraPositionChanged(lat = OTRO_BARRIO_LAT, lon = OTRO_BARRIO_LON))
        advanceUntilIdle()

        assertEquals(null, vm.state.value.selection)
    }

    @Test
    fun `should_emptyTheLayer_when_theCameraLeavesTheUsersRegion`() = runTest(testDispatcher) {
        // Beyond the follow limit the map AND the list show NOTHING — never your own city's stations
        // over a distant one — and the lane says why. Coming back must ask again, so the anchor is
        // dropped with them. [NEARBY-ONE-STORE-FOLLOWS-THE-CAMERA-001]
        fuelSource.result = FuelStationsResult(listOf(spyStation()))
        saveActiveCar(fuelType = com.rndeveloper.paparcar.domain.model.FuelType.GASOLINE)
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)
        advanceUntilIdle()
        vm.handleIntent(HomeIntent.SetNearbyLens(HomeNearbyLens.FUEL))
        advanceUntilIdle()
        vm.handleIntent(HomeIntent.SelectItem(HomeSelection.FuelStation("st1")))
        advanceUntilIdle()
        assertEquals(1, vm.state.value.fuel.stations.size)

        // Paris, from Madrid: far past the limit.
        vm.handleIntent(HomeIntent.CameraPositionChanged(48.8566, 2.3522))
        advanceUntilIdle()

        assertTrue(vm.state.value.toMapSlice().fuelStations.isEmpty(), "no capsules out there")
        assertTrue(vm.state.value.toBrowseListSlice().fuel.stations.isEmpty(), "and no rows either")
        assertTrue(vm.state.value.fuel.tooFar, "the lane says it is too far, not that there are none")
        assertEquals(null, vm.state.value.selection, "no peek may survive its pin")
        assertEquals(1, fuelSource.askCount, "nothing was asked out there")

        // Back home: the emptied layer asks again instead of restoring a store it threw away.
        vm.handleIntent(HomeIntent.CameraPositionChanged(location.latitude, location.longitude))
        advanceUntilIdle()
        assertEquals(2, fuelSource.askCount)
        assertEquals(1, vm.state.value.fuel.stations.size)
        assertTrue(!vm.state.value.fuel.tooFar)
    }

    @Test
    fun `should_askAgainInTheSamePlace_when_theFirstAskFailed`() = runTest(testDispatcher) {
        // ⛔ A transient Overpass 504 must not leave the area permanently blank. Measured on device:
        // with the anchor advancing on the ATTEMPT, Paris stayed empty and no pan short of
        // kilometres would retry. Asks only fire on camera events, so nothing is being hammered.
        saveActiveCar(fuelType = com.rndeveloper.paparcar.domain.model.FuelType.GASOLINE)
        // ⛔ El fallo se arma ANTES del primer fix: desde el calentamiento de entrada, la PRIMERA
        // pregunta del carril ya no es la del usuario abriéndolo, es la de la precarga — y es esa la
        // que este test necesita que fracase. [HOME-THE-THREE-LANES-WARM-UP-ONCE-ON-ENTRY-001]
        fuelSource.failNext = true
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)
        advanceUntilIdle()
        assertEquals(1, fuelSource.askCount)
        // Un fallo no deja ancla, así que abrir el carril vuelve a preguntar por el mismo sitio —
        // y esa también falla, que es el estado que el paneo de abajo tiene que rescatar.
        fuelSource.failNext = true
        vm.handleIntent(HomeIntent.SetNearbyLens(HomeNearbyLens.FUEL))
        advanceUntilIdle()
        assertEquals(2, fuelSource.askCount)
        // Asertado sobre el MAPA — lo que el usuario ve: "hay o no hay pines" es el contrato.
        // [NEARBY-ONE-STORE-FOLLOWS-THE-CAMERA-001]
        assertTrue(vm.state.value.toMapSlice().fuelStations.isEmpty())

        // A nudge of the camera — far too small to count as a new area — retries the failed one.
        fuelSource.result = FuelStationsResult(listOf(spyStation()))
        vm.handleIntent(
            HomeIntent.CameraPositionChanged(location.latitude + 0.001, location.longitude),
        )
        advanceUntilIdle()

        assertEquals(3, fuelSource.askCount)
        assertEquals(1, vm.state.value.toMapSlice().fuelStations.size)
    }

    // ── Los tres carriles se calientan al entrar ─────────────────────────────
    // [HOME-THE-THREE-LANES-WARM-UP-ONCE-ON-ENTRY-001]

    @Test
    fun `should_fillBothHiddenLanes_when_homeOpens`() = runTest(testDispatcher) {
        fuelSource.result = FuelStationsResult(listOf(spyStation()))
        parkingsSource.result = Result.success(listOf(spyParking()))
        saveActiveCar(fuelType = com.rndeveloper.paparcar.domain.model.FuelType.GASOLINE)

        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)
        advanceUntilIdle()

        // La lente sigue en plazas: nadie ha abierto nada, y sin embargo los otros dos ya tienen
        // respuesta esperando.
        assertEquals(HomeNearbyLens.SPOTS, vm.state.value.nearbyLens)
        assertEquals(1, fuelSource.askCount)
        assertEquals(1, parkingsSource.askCount)
        assertEquals(1, vm.state.value.fuel.stations.size)
        assertEquals(1, vm.state.value.parkings.parkings.size)
    }

    /** El objetivo del ticket, dicho como lo vive el usuario: pulsar y que ya esté. */
    @Test
    fun `should_showTheAnswerWithoutAskingAgain_when_theUserOpensALaneAfterTheWarmUp`() =
        runTest(testDispatcher) {
            parkingsSource.result = Result.success(listOf(spyParking()))
            permissions.emit(FakePermissionManager.allGranted())
            locationDataSource.emitHighAccuracy(location)
            advanceUntilIdle()
            val trasCalentar = parkingsSource.askCount

            vm.handleIntent(HomeIntent.SetNearbyLens(HomeNearbyLens.PARKINGS))
            advanceUntilIdle()

            assertEquals(trasCalentar, parkingsSource.askCount, "abrir el carril no vuelve a preguntar")
            assertTrue(!vm.state.value.parkings.isLoading, "y no hay spinner que esperar")
            assertEquals(1, vm.state.value.toBrowseListSlice().parkings.parkings.size)
        }

    @Test
    fun `should_warmUpOnlyOnce_when_moreFixesArrive`() = runTest(testDispatcher) {
        // ⛔ El calentamiento es de ENTRADA, no de cada fix. Un fix por segundo multiplicando
        // peticiones es la tormenta que dejó a esta máquina fuera de Overpass el 09-09.
        parkingsSource.result = Result.success(listOf(spyParking()))
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)
        advanceUntilIdle()
        val trasCalentar = parkingsSource.askCount

        repeat(5) {
            locationDataSource.emitHighAccuracy(location)
            advanceUntilIdle()
        }

        assertEquals(trasCalentar, parkingsSource.askCount)
    }

    @Test
    fun `should_askOneLaneAtATime_when_warmingUp`() = runTest(testDispatcher) {
        // ⛔ El presupuesto de Overpass es UNA pregunta a la vez (OverpassQuota): el servidor da 2
        // por IP y una IP es un operador entero de usuarios. Lanzar los dos carriles a la vez
        // pondría al segundo en una cola que abandona a los 10 s — un calentamiento que falla de
        // forma fiable es peor que ninguno, porque además deja al carril creyendo que ya preguntó.
        saveActiveCar(fuelType = com.rndeveloper.paparcar.domain.model.FuelType.GASOLINE)
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        fuelSource.holdNextUntil = gate

        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)
        advanceUntilIdle()

        assertEquals(1, fuelSource.askCount, "el primer carril ya preguntó")
        assertEquals(0, parkingsSource.askCount, "y el segundo espera su turno, no se solapa")

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, parkingsSource.askCount, "al soltarse el primero, entra el segundo")
    }

    /** Counts the questions actually asked — what separates an honest empty from a fabricated one. */
    private class SpyFuelStationsSource : FuelStationsSource {
        var askCount = 0
        var result = FuelStationsResult(emptyList())
        var lastQuery: FuelStationsQuery? = null
        /** One-shot failure — the transient-outage case. */
        var failNext = false
        /** One-shot hold, so a test can watch what happens WHILE an ask is in flight. */
        var holdNextUntil: kotlinx.coroutines.CompletableDeferred<Unit>? = null
        override fun covers(countryCode: String) = true
        override suspend fun stationsNear(query: FuelStationsQuery): Result<FuelStationsResult> {
            askCount++
            lastQuery = query
            holdNextUntil?.let { gate ->
                holdNextUntil = null
                gate.await()
            }
            if (failNext) {
                failNext = false
                return Result.failure(IllegalStateException("Overpass answered 504"))
            }
            return Result.success(result)
        }
    }

    // ── Public parkings [PARKINGS-ON-THE-MAP-001] ────────────────────────────

    /**
     * ⛔ Lleva coordenadas desde [PARKINGS-A-ZONE-IS-ASKED-ONCE-AND-PANNED-FOR-FREE-001]: el caso de
     * uso ahora descarta lo que cae fuera del radio de PANTALLA, así que un fixture plantado en Sol
     * mientras la cámara viaja 7,8 km deja de aparecer — y debe dejar de aparecer. Un parking que no
     * se puede ver desde donde miras no es una fila, es un error que antes nadie filtraba.
     */
    private fun spyParking(
        id: String = "osm/node/1",
        latitude: Double = 40.4157,
        longitude: Double = -3.7028,
    ) = PublicParking(
        id = id,
        name = "Parking Benavente",
        operator = null,
        kind = PublicParkingKind.UNDERGROUND,
        latitude = latitude,
        longitude = longitude,
    )

    /** El mismo fixture, pero donde se para la cámara en los tests de paneo. */
    private fun spyParkingLejos(id: String) = spyParking(id, LEJOS_LAT, LEJOS_LON)

    @Test
    fun `should_loadParkings_andPersistTheChoice_when_theLayerTurnsOn`() = runTest(testDispatcher) {
        parkingsSource.result = Result.success(listOf(spyParking()))
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)
        advanceUntilIdle()

        vm.handleIntent(HomeIntent.SetNearbyLens(HomeNearbyLens.PARKINGS))
        advanceUntilIdle()

        assertEquals(HomeNearbyLens.PARKINGS, vm.state.value.nearbyLens)
        assertEquals(1, parkingsSource.askCount, "one entry, ONE request")
        // The answer lands in the ONE store, and the list and the map read the same projection of it.
        // [NEARBY-ONE-STORE-FOLLOWS-THE-CAMERA-001] [PARKINGS-ON-THE-MAP-001]
        assertEquals(1, vm.state.value.parkings.parkings.size)
        assertEquals(1, vm.state.value.toMapSlice().publicParkings.size, "and the pins are drawn")
    }

    @Test
    fun `should_askItsOwnQuestion_when_theSectionOpens_afterTheLayerAlreadyLoaded`() = runTest(testDispatcher) {
        // ONE fetch feeds both surfaces: the list and the map read one store. Unlike fuel
        // there is no per-car question to re-ask — a car park is a car park whatever the car burns.
        parkingsSource.result = Result.success(listOf(spyParking()))
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)
        advanceUntilIdle()
        vm.handleIntent(HomeIntent.SetNearbyLens(HomeNearbyLens.PARKINGS))
        advanceUntilIdle()

        // Re-entering the lane you are already in, and then again after leaving and returning.
        vm.handleIntent(HomeIntent.SetNearbyLens(HomeNearbyLens.PARKINGS))
        vm.handleIntent(HomeIntent.SetNearbyLens(HomeNearbyLens.SPOTS))
        vm.handleIntent(HomeIntent.SetNearbyLens(HomeNearbyLens.PARKINGS))
        advanceUntilIdle()

        // ⛔ ONE ask: entering costs one request and coming back to the same place costs none,
        // because a car park is a car park whatever the car burns.
        // [NEARBY-ONE-STORE-FOLLOWS-THE-CAMERA-001]
        assertEquals(1, parkingsSource.askCount)
        assertEquals(HomeNearbyLens.PARKINGS, vm.state.value.nearbyLens)
        assertEquals(1, vm.state.value.parkings.parkings.size)
    }

    @Test
    fun `should_sayItCouldNotAsk_ratherThanNoneNearby_when_theLookupFails`() = runTest(testDispatcher) {
        // ⛔ A failure is NOT an empty list: "none within 1,5 km" sends the user driving on a claim
        // nobody checked. [FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001]
        parkingsSource.result = Result.failure(IllegalStateException("Overpass answered 504"))
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)
        advanceUntilIdle()

        vm.handleIntent(HomeIntent.SetNearbyLens(HomeNearbyLens.PARKINGS))
        advanceUntilIdle()

        val slice = vm.state.value.parkings
        assertTrue(slice.failed)
        assertTrue(slice.parkings.isEmpty())
        assertTrue(!slice.isLoading)
    }

    @Test
    fun `should_askNobody_when_thereIsNoFix_andSayItCouldNotAsk`() = runTest(testDispatcher) {
        permissions.emit(FakePermissionManager.allGranted())
        advanceUntilIdle()

        vm.handleIntent(HomeIntent.SetNearbyLens(HomeNearbyLens.PARKINGS))
        advanceUntilIdle()

        assertEquals(0, parkingsSource.askCount, "with no fix there was no question to ask")
        assertTrue(vm.state.value.parkings.failed)
    }

    @Test
    fun `should_dropAParkingSelection_when_theLayerTurnsOff`() = runTest(testDispatcher) {
        parkingsSource.result = Result.success(listOf(spyParking()))
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)
        advanceUntilIdle()
        vm.handleIntent(HomeIntent.SetNearbyLens(HomeNearbyLens.PARKINGS))
        advanceUntilIdle()
        vm.handleIntent(HomeIntent.SelectItem(HomeSelection.PublicParking("osm/node/1")))
        advanceUntilIdle()
        assertTrue(vm.state.value.selectedPublicParking != null)

        // Leaving the lane is what stops the pins being drawn, so it is what drops the selection.
        vm.handleIntent(HomeIntent.SetNearbyLens(HomeNearbyLens.SPOTS))
        advanceUntilIdle()

        assertEquals(HomeNearbyLens.SPOTS, vm.state.value.nearbyLens)
        assertEquals(null, vm.state.value.selection, "no peek may keep talking about an invisible pin")
    }

    @Test
    fun `should_notReaskOnEveryFix_when_theCameraHasNotMoved`() = runTest(testDispatcher) {
        // ⛔ THE regression, measured on device on 09-09-2026: the layer re-checked itself on every
        // GPS fix, so a failed ask over an empty store asked AGAIN on the next fix — one Overpass
        // request every ~5 s, indefinitely, which is what rate-limited that machine out of the API
        // mid-verification. A fix only supplies a missing centre; asking follows the CAMERA, and a
        // phone standing still produces no camera movement to justify another.
        // [PARKINGS-ON-THE-MAP-001] [NEARBY-ONE-STORE-FOLLOWS-THE-CAMERA-001]
        parkingsSource.result = Result.success(listOf(spyParking()))
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)
        advanceUntilIdle()
        vm.handleIntent(HomeIntent.SetNearbyLens(HomeNearbyLens.PARKINGS))
        advanceUntilIdle()
        assertEquals(1, parkingsSource.askCount)

        // Four more fixes at the same place — the cadence a standing phone produces in 20 seconds.
        repeat(4) {
            locationDataSource.emitHighAccuracy(location)
            advanceUntilIdle()
        }

        assertEquals(1, parkingsSource.askCount, "a still camera must not re-ask on every fix")
    }

    @Test
    fun `should_retryOnTheNextCameraMove_butNeverOnAFix_when_theAskFailed`() = runTest(testDispatcher) {
        // ⛔ Both halves of the same lesson, now in ONE store. An anchor kept on a failure means
        // "already asked here" and the area stays blank until the user pans kilometres away — so the
        // anchor goes. But a failure retried on every GPS fix is the one-request-every-5-s storm that
        // rate-limited this machine out of Overpass on 09-09 — so a fix never retries.
        // [NEARBY-ONE-STORE-FOLLOWS-THE-CAMERA-001] [PARKINGS-ON-THE-MAP-001]
        parkingsSource.result = Result.failure(IllegalStateException("Overpass answered 504"))
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)
        advanceUntilIdle()
        // La primera la hizo el calentamiento de entrada, y falló; abrir el carril reintenta.
        assertEquals(1, parkingsSource.askCount, "el calentamiento ya preguntó, y falló")
        vm.handleIntent(HomeIntent.SetNearbyLens(HomeNearbyLens.PARKINGS))
        advanceUntilIdle()
        assertEquals(2, parkingsSource.askCount)
        assertEquals(null, vm.state.value.parkings.anchorLat, "a failed ask keeps no anchor")

        parkingsSource.result = Result.success(listOf(spyParking()))
        repeat(3) {
            locationDataSource.emitHighAccuracy(location)
            advanceUntilIdle()
        }
        assertEquals(2, parkingsSource.askCount, "a fix is not a reason to ask again")

        // A camera nudge far too small to count as a new area retries the SAME place.
        vm.handleIntent(HomeIntent.CameraPositionChanged(location.latitude + 0.0005, location.longitude))
        advanceUntilIdle()

        assertEquals(3, parkingsSource.askCount)
        assertEquals(1, vm.state.value.toMapSlice().publicParkings.size)
        assertEquals(1, vm.state.value.toBrowseListSlice().parkings.parkings.size)
    }

    // ── Un almacén que sigue a la cámara [NEARBY-ONE-STORE-FOLLOWS-THE-CAMERA-001] ──

    @Test
    fun `should_answerForTheLatestPlace_when_theCameraSettlesWhileAnAskIsInFlight`() = runTest(testDispatcher) {
        // ⛔ THE reported bug: a pan while Overpass was still answering was DROPPED behind the
        // in-flight ask, so the store stayed on the previous area. Latest wins, like the spots.
        // ⛔ El gate se arma ANTES del primer fix: desde el calentamiento de entrada, la petición
        // que puede quedarse en vuelo es la de la precarga, no la de abrir el carril — abrirlo ya no
        // pregunta porque el ancla se fija en el INTENTO.
        // [HOME-THE-THREE-LANES-WARM-UP-ONCE-ON-ENTRY-001]
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        parkingsSource.holdNextUntil = gate
        parkingsSource.result = Result.success(listOf(spyParking("aqui")))
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)
        advanceUntilIdle()
        assertTrue(vm.state.value.parkings.isLoading, "the first ask is still flying")
        vm.handleIntent(HomeIntent.SetNearbyLens(HomeNearbyLens.PARKINGS))
        advanceUntilIdle()

        parkingsSource.result = Result.success(listOf(spyParkingLejos("alli")))
        vm.handleIntent(HomeIntent.CameraPositionChanged(LEJOS_LAT, LEJOS_LON))
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(2, parkingsSource.askCount, "la que se quedó en vuelo, y la nueva")
        val parkings = vm.state.value.parkings
        assertEquals(LEJOS_LAT, parkings.anchorLat)
        assertTrue(!parkings.isLoading)
        assertEquals(listOf("alli"), vm.state.value.toMapSlice().publicParkings.map { it.parking.id })
        assertEquals(listOf("alli"), vm.state.value.toBrowseListSlice().parkings.parkings.map { it.parking.id })
    }

    @Test
    fun `should_emptyTheMapAndTheList_andSayTooFar_when_theCameraLeavesTheFollowRange`() = runTest(testDispatcher) {
        parkingsSource.result = Result.success(listOf(spyParking()))
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)
        advanceUntilIdle()
        vm.handleIntent(HomeIntent.SetNearbyLens(HomeNearbyLens.PARKINGS))
        advanceUntilIdle()
        vm.handleIntent(HomeIntent.SelectItem(HomeSelection.PublicParking("osm/node/1")))
        advanceUntilIdle()

        // ⛔ Before: only the layer emptied, the lane kept Madrid's car parks — and the union painted
        // them over wherever the user was looking.
        vm.handleIntent(HomeIntent.CameraPositionChanged(48.8566, 2.3522))
        advanceUntilIdle()

        assertTrue(vm.state.value.toMapSlice().publicParkings.isEmpty())
        assertTrue(vm.state.value.toBrowseListSlice().parkings.parkings.isEmpty())
        assertTrue(vm.state.value.parkings.tooFar)
        assertTrue(!vm.state.value.parkings.failed, "too far is not a failure")
        assertEquals(null, vm.state.value.selection)
        assertEquals(1, parkingsSource.askCount, "nothing was asked out there")
    }

    @Test
    fun `should_askOnTheFirstFix_when_theLaneWasEnteredWithoutACentre`() = runTest(testDispatcher) {
        // The lane said it could not ask; the moment there is a centre, it asks — once.
        parkingsSource.result = Result.success(listOf(spyParking()))
        permissions.emit(FakePermissionManager.allGranted())
        advanceUntilIdle()
        vm.handleIntent(HomeIntent.SetNearbyLens(HomeNearbyLens.PARKINGS))
        advanceUntilIdle()
        assertEquals(0, parkingsSource.askCount)
        assertTrue(vm.state.value.parkings.failed)

        repeat(3) {
            locationDataSource.emitHighAccuracy(location)
            advanceUntilIdle()
        }

        assertEquals(1, parkingsSource.askCount)
        assertTrue(!vm.state.value.parkings.failed)
        assertEquals(1, vm.state.value.toMapSlice().publicParkings.size)
    }

    @Test
    fun `should_listWhatTheMapPaints_forEveryStepOfAPan`() = runTest(testDispatcher) {
        // The invariant itself, walked through the real VM: whatever the camera does, the rows and
        // the pins are the same set.
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)
        advanceUntilIdle()
        parkingsSource.result = Result.success(listOf(spyParking("a"), spyParking("b")))
        vm.handleIntent(HomeIntent.SetNearbyLens(HomeNearbyLens.PARKINGS))
        advanceUntilIdle()

        fun assertSame() = assertEquals(
            vm.state.value.toBrowseListSlice().parkings.parkings.map { it.parking.id },
            vm.state.value.toMapSlice().publicParkings.map { it.parking.id },
        )
        assertSame()
        parkingsSource.result = Result.success(listOf(spyParkingLejos("c")))
        vm.handleIntent(HomeIntent.CameraPositionChanged(LEJOS_LAT, LEJOS_LON))
        advanceUntilIdle()
        assertSame()
        assertEquals(listOf("c"), vm.state.value.toMapSlice().publicParkings.map { it.parking.id })
        vm.handleIntent(HomeIntent.CameraPositionChanged(48.8566, 2.3522))
        advanceUntilIdle()
        assertSame()
    }

    // ── La sección responde sobre DONDE MIRAS [A-SECTION-ANSWERS-ABOUT-WHERE-YOU-LOOK-001] ──

    @Test
    fun `should_askAboutTheCamera_not_theUser_when_theLaneOpens`() = runTest(testDispatcher) {
        // ⛔ EL bug, visto en device: con el usuario en Lugar la Cañada y el mapa arrastrado sobre
        // Jerez, el mapa pintaba seis parkings mientras la sección decía "no hay ninguno a 1,5 km".
        // Las dos afirmaciones eran ciertas y juntas eran inútiles.
        parkingsSource.result = Result.success(listOf(spyParking()))
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)
        advanceUntilIdle()

        // El usuario arrastra el mapa lejos y despliega la sección.
        vm.handleIntent(HomeIntent.CameraPositionChanged(LEJOS_LAT, LEJOS_LON))
        advanceUntilIdle()
        vm.handleIntent(HomeIntent.SetNearbyLens(HomeNearbyLens.PARKINGS))
        advanceUntilIdle()

        val zona = ParkingZoneGrid.zoneOf(LEJOS_LAT, LEJOS_LON)
        assertEquals(zona.centreLatitude, parkingsSource.seen?.latitude)
        assertEquals(zona.centreLongitude, parkingsSource.seen?.longitude)
        // …y esa zona es la de la CÁMARA, que es el bug que este test existe para impedir.
        assertTrue(!ParkingZoneGrid.sameZone(LEJOS_LAT, LEJOS_LON, location.latitude, location.longitude))
    }

    @Test
    fun `should_refetchTheOpenLane_when_theCameraSettlesFarEnough`() = runTest(testDispatcher) {
        parkingsSource.result = Result.success(listOf(spyParking()))
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)
        advanceUntilIdle()
        vm.handleIntent(HomeIntent.SetNearbyLens(HomeNearbyLens.PARKINGS))
        advanceUntilIdle()
        val tras = parkingsSource.askCount

        vm.handleIntent(HomeIntent.CameraPositionChanged(LEJOS_LAT, LEJOS_LON))
        advanceUntilIdle()

        assertEquals(tras + 1, parkingsSource.askCount, "la sección abierta viaja con la cámara")
        // ⛔ Se pregunta por la ZONA de la cámara, no por su coordenada exacta — eso es lo que hace
        // que panear dentro de la zona no cueste peticiones.
        // [PARKINGS-A-ZONE-IS-ASKED-ONCE-AND-PANNED-FOR-FREE-001]
        assertEquals(
            ParkingZoneGrid.zoneOf(LEJOS_LAT, LEJOS_LON).centreLatitude,
            parkingsSource.seen?.latitude,
        )
    }

    @Test
    fun `should_notRefetch_when_theLaneIsElsewhere`() = runTest(testDispatcher) {
        // ⛔ La regla se ESTRECHA, no desaparece, y el matiz es todo:
        // antes — «un sujeto que no está a la vista no descarga NADA»
        // ahora — «…no descarga nada CUANDO LA CÁMARA SE MUEVE».
        // El calentamiento de entrada llena el carril UNA vez para que el primer toque sea
        // instantáneo [HOME-THE-THREE-LANES-WARM-UP-ONCE-ON-ENTRY-001]; a partir de ahí un usuario
        // que solo arrastra el mapa sigue sin pagar peticiones por una lista que nunca abrió, que es
        // lo que este test existe para proteger.
        parkingsSource.result = Result.success(listOf(spyParking()))
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)
        advanceUntilIdle()
        val trasCalentar = parkingsSource.askCount
        assertEquals(1, trasCalentar, "el calentamiento pregunta UNA vez, al entrar")

        vm.handleIntent(HomeIntent.CameraPositionChanged(LEJOS_LAT, LEJOS_LON))
        advanceUntilIdle()

        assertEquals(
            trasCalentar,
            parkingsSource.askCount,
            "otro carril no pregunta ni aunque la cámara viaje",
        )
    }

    @Test
    fun `should_notRefetch_when_theCameraBarelyMoved`() = runTest(testDispatcher) {
        // Medio radio, como las capas: un temblor del dedo no es una pregunta nueva.
        parkingsSource.result = Result.success(listOf(spyParking()))
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)
        advanceUntilIdle()
        vm.handleIntent(HomeIntent.SetNearbyLens(HomeNearbyLens.PARKINGS))
        advanceUntilIdle()
        val tras = parkingsSource.askCount

        // ~200 m: muy por debajo de la mitad de los 1.500 m del radio.
        vm.handleIntent(HomeIntent.CameraPositionChanged(location.latitude + 0.0018, location.longitude))
        advanceUntilIdle()

        assertEquals(tras, parkingsSource.askCount)
    }

    /** Otra zona: lo bastante lejos para exigir una pregunta nueva de parkings (~10 km), dentro del
     *  límite de 20 km. ⛔ Estaba a +0,15° (~21 km) y dejó de ser alcanzable al bajar el límite de 50 a
     *  20 km — dos tests fallaban en master por eso. [NEARBY-ONE-STORE-FOLLOWS-THE-CAMERA-001] */
    private val LEJOS_LAT = location.latitude + 0.07
    private val LEJOS_LON = location.longitude + 0.07

    /** ~11 km: más de medio radio de gasolineras (2,5 km), dentro del límite de 20 km. */
    private val OTRO_BARRIO_LAT = 40.4800
    private val OTRO_BARRIO_LON = -3.6000

    /** Counts the questions actually asked, like its fuel twin. */
    private class SpyPublicParkingsSource : PublicParkingsSource {
        var askCount = 0
        /** DONDE se preguntó por última vez — lo que distingue "contestó" de "contestó del sitio
         *  correcto". [A-SECTION-ANSWERS-ABOUT-WHERE-YOU-LOOK-001] */
        var seen: PublicParkingsQuery? = null
        var result: Result<List<PublicParking>> = Result.success(emptyList())

        /** Non-null makes the spy answer as the DISK does. [CACHE-A-MAP-MUST-NOT-EMPTY-ON-COLD-START-001] */
        var rememberedAtMs: Long? = null

        /** One-shot: the next ask suspends until this completes — an Overpass answer still flying. */
        var holdNextUntil: kotlinx.coroutines.CompletableDeferred<Unit>? = null
        override fun covers(countryCode: String) = true
        override suspend fun parkingsNear(query: PublicParkingsQuery): Result<PublicParkingsResult> {
            askCount++
            seen = query
            val answer = result.map { PublicParkingsResult(it, rememberedAtMs) }
            holdNextUntil?.let { gate ->
                holdNextUntil = null
                gate.await()
            }
            return answer
        }
    }
}
