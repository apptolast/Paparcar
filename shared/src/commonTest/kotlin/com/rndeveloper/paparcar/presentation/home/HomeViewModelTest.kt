@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.presentation.home

import app.cash.turbine.test
import com.swmansion.kmpmaps.core.MapType
import com.rndeveloper.paparcar.domain.connectivity.ConnectivityStatus
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
import com.rndeveloper.paparcar.domain.parkings.PublicParking
import com.rndeveloper.paparcar.domain.parkings.PublicParkingKind
import com.rndeveloper.paparcar.domain.parkings.PublicParkingsQuery
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
    private lateinit var parkingsSource: SpyPublicParkingsSource
    private lateinit var geocoderFake: FakeGeocoderDataSource
    private lateinit var vm: HomeViewModel

    private fun buildVm(
        initialMapType: String = "TERRAIN",
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
        )
        val confirmParking = ConfirmParkingUseCase(
            userParkingRepository = parkingRepo,
            vehicleRepository = vehicleRepo,
            zoneRepository = FakeZoneRepository(),
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
            evaluateDetectionReliability = EvaluateDetectionReliabilityUseCase(),
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
        parkingsSource = SpyPublicParkingsSource()
        vm = buildVm()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Init — map type from preferences ─────────────────────────────────────

    @Test
    fun `should_apply_terrain_mapType_from_preferences_by_default`() = runTest {
        assertEquals(MapType.TERRAIN, vm.state.value.mapType)
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
            vm.handleIntent(HomeIntent.ReleaseParking(sessionId = session.id, reason = ParkingReleaseReason.DEPARTURE_PUBLISHED))
            assertIs<HomeEffect.SpotReported>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Which action is in flight [UI-A-SPINNER-LIVES-IN-THE-BUTTON-THAT-WAS-PRESSED-001] ─────
    // The state used to hold a bare `isReleasingParking: Boolean`, so the release dialog could only
    // ever spin its PRIMARY button — even when the user had pressed the secondary, which means the
    // opposite thing (don't hand the freed space to the community). These two tests pin the
    // discriminator itself, in flight and after: the reason the user chose is what the state holds.
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
                reason = ParkingReleaseReason.DEPARTURE_UNPUBLISHED,
            ),
        )

        // Not merely "a release is running": WHICH one, so the secondary button can spin itself.
        assertEquals(ParkingReleaseReason.DEPARTURE_UNPUBLISHED, vm.state.value.releasingReason)
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
        vm.handleIntent(HomeIntent.ReleaseParking(sessionId = session.id, reason = ParkingReleaseReason.DEPARTURE_PUBLISHED))
        assertNull(vm.state.value.selection)
    }

    // ── SelectItem ────────────────────────────────────────────────────────────

    @Test
    fun `should_set_selection_on_SelectItem`() = runTest {
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
        vm.handleIntent(HomeIntent.SetMapType(MapType.TERRAIN))
        assertEquals("TERRAIN", prefs.defaultMapType)
    }

    @Test
    fun `should_not_update_preferences_when_same_mapType_set`() = runTest {
        vm.handleIntent(HomeIntent.SetMapType(MapType.TERRAIN))
        // First SetMapType with same value as init — prefs should remain untouched
        assertEquals("TERRAIN", prefs.defaultMapType)
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
        vm.handleIntent(HomeIntent.ReleaseParking(sessionId = sessionB.id, reason = ParkingReleaseReason.DEPARTURE_UNPUBLISHED))

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
            vm.handleIntent(HomeIntent.ReleaseParking(sessionId = "ghost-session", reason = ParkingReleaseReason.DEPARTURE_UNPUBLISHED))
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

        vm.handleIntent(HomeIntent.ReleaseParking(sessionId = "session-inactive", reason = ParkingReleaseReason.DEPARTURE_UNPUBLISHED))
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

        vm.handleIntent(HomeIntent.ReleaseParking(sessionId = "session-kamiq", reason = ParkingReleaseReason.DEPARTURE_PUBLISHED))
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
        saveActiveCar(fuelType = null)
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)
        advanceUntilIdle()

        vm.handleIntent(HomeIntent.ToggleFuelStations)
        advanceUntilIdle()

        val fuel = vm.state.value.fuel
        assertTrue(fuel.isExpanded, "the user tapped — the section opens, honestly")
        assertTrue(fuel.missingFuelType)
        assertTrue(!fuel.failed)
        assertEquals(0, fuelSource.askCount, "no fuel type means there was no question to ask")
    }

    @Test
    fun `should_reportFailure_notAnEmptyList_when_thereIsNoFix`() = runTest(testDispatcher) {
        // No fix means the question could not be asked — that is the failure sentence, and it must
        // never surface as "no stations within 5 km".
        saveActiveCar(fuelType = com.rndeveloper.paparcar.domain.model.FuelType.GASOLINE)
        advanceUntilIdle()

        vm.handleIntent(HomeIntent.ToggleFuelStations)
        advanceUntilIdle()

        val fuel = vm.state.value.fuel
        assertTrue(fuel.isExpanded)
        assertTrue(fuel.failed)
        assertTrue(!fuel.missingFuelType)
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

        vm.handleIntent(HomeIntent.ToggleFuelStations)
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

        vm.handleIntent(HomeIntent.ToggleFuelStations)
        advanceUntilIdle()
        assertTrue(vm.state.value.fuel.missingFuelType)

        saveActiveCar(fuelType = com.rndeveloper.paparcar.domain.model.FuelType.GASOLINE)
        advanceUntilIdle()
        vm.handleIntent(HomeIntent.ToggleFuelStations) // fold
        vm.handleIntent(HomeIntent.ToggleFuelStations) // unfold — now there is a question
        advanceUntilIdle()

        val fuel = vm.state.value.fuel
        assertEquals(1, fuelSource.askCount)
        assertTrue(!fuel.missingFuelType)
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

        vm.handleIntent(HomeIntent.ToggleFuelStationsLayer)
        advanceUntilIdle()

        assertTrue(vm.state.value.showFuelStationsLayer)
        assertTrue(prefs.showFuelStationsLayer, "the choice must survive the process")
        assertEquals(1, fuelSource.askCount)
        assertEquals(1, vm.state.value.fuelLayer.stations.size)
    }

    @Test
    fun `should_askFuelLess_when_theLayerTurnsOn_withACarWithoutFuel`() = runTest(testDispatcher) {
        // The layer wants markers even for a fuel-less car — unpriced capsules are honest; the
        // SECTION keeps its missingFuelType sentence for the priced list.
        fuelSource.result = FuelStationsResult(listOf(spyStation()))
        saveActiveCar(fuelType = null)
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)
        advanceUntilIdle()

        vm.handleIntent(HomeIntent.ToggleFuelStationsLayer)
        advanceUntilIdle()

        assertEquals(1, fuelSource.askCount)
        assertEquals(1, vm.state.value.fuelLayer.stations.size)
        assertTrue(!vm.state.value.fuel.missingFuelType)
    }

    @Test
    fun `should_reprice_when_theSectionOpens_afterAFuelLessLayerLoad`() = runTest(testDispatcher) {
        // The layer's fuel-less answer is a DIFFERENT question from the section's priced one —
        // fetchedFor is what stops a priceless list from posing as Spain without prices.
        fuelSource.result = FuelStationsResult(listOf(spyStation()))
        saveActiveCar(fuelType = null)
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)
        advanceUntilIdle()
        vm.handleIntent(HomeIntent.ToggleFuelStationsLayer)
        advanceUntilIdle()
        assertEquals(1, fuelSource.askCount)

        saveActiveCar(fuelType = com.rndeveloper.paparcar.domain.model.FuelType.GASOLINE)
        advanceUntilIdle()
        vm.handleIntent(HomeIntent.ToggleFuelStations)
        advanceUntilIdle()

        assertEquals(2, fuelSource.askCount, "a priced section cannot serve the fuel-less answer")
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
        vm.handleIntent(HomeIntent.ToggleFuelStationsLayer)
        advanceUntilIdle()
        vm.handleIntent(HomeIntent.SelectItem(HomeSelection.FuelStation("st1")))
        advanceUntilIdle()
        assertTrue(vm.state.value.selectedFuelStation != null)

        vm.handleIntent(HomeIntent.ToggleFuelStationsLayer)
        advanceUntilIdle()

        assertTrue(!vm.state.value.showFuelStationsLayer)
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
        vm.handleIntent(HomeIntent.ToggleFuelStationsLayer)
        advanceUntilIdle()
        assertEquals(1, fuelSource.askCount)

        // The camera settles on Alcalá (~30 km — "I am driving there"): a different question,
        // asked exactly once, THERE.
        vm.handleIntent(HomeIntent.CameraPositionChanged(lat = 40.4820, lon = -3.3635))
        advanceUntilIdle()

        assertEquals(2, fuelSource.askCount)
        assertEquals(40.4820, fuelSource.lastQuery?.latitude)
        assertEquals(-3.3635, fuelSource.lastQuery?.longitude)
    }

    @Test
    fun `should_stayQuiet_when_theCameraBarelyMoved_orTheLayerIsOff`() = runTest(testDispatcher) {
        fuelSource.result = FuelStationsResult(listOf(spyStation()))
        saveActiveCar(fuelType = com.rndeveloper.paparcar.domain.model.FuelType.GASOLINE)
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)
        advanceUntilIdle()

        // Layer OFF: panning asks nobody.
        vm.handleIntent(HomeIntent.CameraPositionChanged(lat = 40.4820, lon = -3.3635))
        advanceUntilIdle()
        assertEquals(0, fuelSource.askCount)

        vm.handleIntent(HomeIntent.ToggleFuelStationsLayer)
        advanceUntilIdle()
        assertEquals(1, fuelSource.askCount)

        // A nervous ~1 km pan is not a new question.
        vm.handleIntent(
            HomeIntent.CameraPositionChanged(
                lat = fuelSource.lastQuery!!.latitude + 0.01,
                lon = fuelSource.lastQuery!!.longitude,
            ),
        )
        advanceUntilIdle()
        assertEquals(1, fuelSource.askCount)
    }

    @Test
    fun `should_pruneAnOrphanedStationSelection_when_theViewportMovesOn`() = runTest(testDispatcher) {
        fuelSource.result = FuelStationsResult(listOf(spyStation("st1")))
        saveActiveCar(fuelType = com.rndeveloper.paparcar.domain.model.FuelType.GASOLINE)
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)
        advanceUntilIdle()
        vm.handleIntent(HomeIntent.ToggleFuelStationsLayer)
        advanceUntilIdle()
        vm.handleIntent(HomeIntent.SelectItem(HomeSelection.FuelStation("st1")))
        advanceUntilIdle()
        assertTrue(vm.state.value.selectedFuelStation != null)

        // The next town has other stations; st1 survives in NEITHER store → the selection goes too.
        fuelSource.result = FuelStationsResult(listOf(spyStation("st2")))
        vm.handleIntent(HomeIntent.CameraPositionChanged(lat = 40.4820, lon = -3.3635))
        advanceUntilIdle()

        assertEquals(null, vm.state.value.selection)
    }

    @Test
    fun `should_emptyTheLayer_when_theCameraLeavesTheUsersRegion`() = runTest(testDispatcher) {
        // Beyond the follow limit the layer shows NOTHING — never your own city's stations over a
        // distant one. Coming back must ask again, so the anchor is dropped with them.
        fuelSource.result = FuelStationsResult(listOf(spyStation()))
        saveActiveCar(fuelType = com.rndeveloper.paparcar.domain.model.FuelType.GASOLINE)
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)
        advanceUntilIdle()
        vm.handleIntent(HomeIntent.ToggleFuelStationsLayer)
        advanceUntilIdle()
        vm.handleIntent(HomeIntent.SelectItem(HomeSelection.FuelStation("st1")))
        advanceUntilIdle()
        assertEquals(1, vm.state.value.fuelLayer.stations.size)

        // Paris, from Madrid: far past the limit.
        vm.handleIntent(HomeIntent.CameraPositionChanged(48.8566, 2.3522))
        advanceUntilIdle()

        assertTrue(vm.state.value.fuelLayer.stations.isEmpty())
        assertEquals(null, vm.state.value.selection, "no peek may survive its pin")
        assertEquals(1, fuelSource.askCount, "nothing was asked out there")

        // Back home: the emptied layer asks again instead of restoring a store it threw away.
        vm.handleIntent(HomeIntent.CameraPositionChanged(location.latitude, location.longitude))
        advanceUntilIdle()
        assertEquals(2, fuelSource.askCount)
        assertEquals(1, vm.state.value.fuelLayer.stations.size)
    }

    @Test
    fun `should_askAgainInTheSamePlace_when_theFirstAskFailed`() = runTest(testDispatcher) {
        // ⛔ A transient Overpass 504 must not leave the area permanently blank. Measured on device:
        // with the anchor advancing on the ATTEMPT, Paris stayed empty and no pan short of
        // kilometres would retry. Asks only fire on camera events, so nothing is being hammered.
        saveActiveCar(fuelType = com.rndeveloper.paparcar.domain.model.FuelType.GASOLINE)
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)
        advanceUntilIdle()
        fuelSource.failNext = true
        vm.handleIntent(HomeIntent.ToggleFuelStationsLayer)
        advanceUntilIdle()
        assertEquals(1, fuelSource.askCount)
        assertTrue(vm.state.value.fuelLayer.stations.isEmpty())

        // A nudge of the camera — far too small to count as a new area — retries the failed one.
        fuelSource.result = FuelStationsResult(listOf(spyStation()))
        vm.handleIntent(
            HomeIntent.CameraPositionChanged(location.latitude + 0.001, location.longitude),
        )
        advanceUntilIdle()

        assertEquals(2, fuelSource.askCount)
        assertEquals(1, vm.state.value.fuelLayer.stations.size)
    }

    /** Counts the questions actually asked — what separates an honest empty from a fabricated one. */
    private class SpyFuelStationsSource : FuelStationsSource {
        var askCount = 0
        var result = FuelStationsResult(emptyList())
        var lastQuery: FuelStationsQuery? = null
        /** One-shot failure — the transient-outage case. */
        var failNext = false
        override fun covers(countryCode: String) = true
        override suspend fun stationsNear(query: FuelStationsQuery): Result<FuelStationsResult> {
            askCount++
            lastQuery = query
            if (failNext) {
                failNext = false
                return Result.failure(IllegalStateException("Overpass answered 504"))
            }
            return Result.success(result)
        }
    }

    // ── Public parkings [PARKINGS-ON-THE-MAP-001] ────────────────────────────

    private fun spyParking(id: String = "osm/node/1") = PublicParking(
        id = id,
        name = "Parking Benavente",
        operator = null,
        kind = PublicParkingKind.UNDERGROUND,
        latitude = 40.4157,
        longitude = -3.7028,
    )

    @Test
    fun `should_loadParkings_andPersistTheChoice_when_theLayerTurnsOn`() = runTest(testDispatcher) {
        parkingsSource.result = Result.success(listOf(spyParking()))
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)
        advanceUntilIdle()

        vm.handleIntent(HomeIntent.TogglePublicParkingsLayer)
        advanceUntilIdle()

        assertTrue(vm.state.value.showPublicParkingsLayer)
        assertTrue(prefs.showPublicParkingsLayer, "the choice must survive the process")
        assertEquals(1, parkingsSource.askCount)
        // ⛔ Into the LAYER store, not the section's: they answer different questions ("what is
        // where I look" vs "where do I park near me") and folding them into one is what painted
        // Madrid over Paris in the fuel lane. [PARKINGS-ON-THE-MAP-001]
        assertEquals(1, vm.state.value.parkingsLayer.parkings.size)
        assertTrue(vm.state.value.parkings.parkings.isEmpty(), "the section asked nothing yet")
    }

    @Test
    fun `should_askItsOwnQuestion_when_theSectionOpens_afterTheLayerAlreadyLoaded`() = runTest(testDispatcher) {
        // ONE fetch feeds both surfaces: the layer's answer is the section's answer. Unlike fuel
        // there is no per-car question to re-ask — a car park is a car park whatever the car burns.
        parkingsSource.result = Result.success(listOf(spyParking()))
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)
        advanceUntilIdle()
        vm.handleIntent(HomeIntent.TogglePublicParkingsLayer)
        advanceUntilIdle()

        vm.handleIntent(HomeIntent.TogglePublicParkings)
        advanceUntilIdle()

        // ⛔ TWO asks, and that is correct now: the layer answered for the CAMERA and the section
        // asks for the USER. Reusing one answer for both is what made the fuel layer lie about
        // where its stations were. [FUEL-LAYER-FOLLOWS-THE-CAMERA-001]
        assertEquals(2, parkingsSource.askCount)
        assertTrue(vm.state.value.parkings.isExpanded)
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

        vm.handleIntent(HomeIntent.TogglePublicParkings)
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

        vm.handleIntent(HomeIntent.TogglePublicParkings)
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
        vm.handleIntent(HomeIntent.TogglePublicParkingsLayer)
        advanceUntilIdle()
        vm.handleIntent(HomeIntent.SelectItem(HomeSelection.PublicParking("osm/node/1")))
        advanceUntilIdle()
        assertTrue(vm.state.value.selectedPublicParking != null)

        vm.handleIntent(HomeIntent.TogglePublicParkingsLayer)
        advanceUntilIdle()

        assertTrue(!vm.state.value.showPublicParkingsLayer)
        assertEquals(null, vm.state.value.selection, "no peek may keep talking about an invisible pin")
    }

    @Test
    fun `should_notReaskOnEveryFix_when_theCameraHasNotMoved`() = runTest(testDispatcher) {
        // ⛔ THE regression, measured on device on 09-09-2026: the layer re-checked itself on every
        // GPS fix, so a failed ask over an empty store asked AGAIN on the next fix — one Overpass
        // request every ~5 s, indefinitely, which is what rate-limited that machine out of the API
        // mid-verification. The camera-anchored design closes it by construction: the anchor is set
        // on the ATTEMPT, and a phone standing still produces no camera movement to justify another.
        // [PARKINGS-ON-THE-MAP-001] [FUEL-LAYER-FOLLOWS-THE-CAMERA-001]
        parkingsSource.result = Result.success(listOf(spyParking()))
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)
        advanceUntilIdle()
        vm.handleIntent(HomeIntent.TogglePublicParkingsLayer)
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
    fun `should_putTheAnchorBack_when_theViewportAskFails`() = runTest(testDispatcher) {
        // ⛔ The other half of the same lesson, inherited from the fuel lane: an anchor advanced on
        // a transient failure means "already asked here", and the area stays blank until the user
        // pans kilometres away. A failure must leave the layer ready to try the SAME place again.
        parkingsSource.result = Result.failure(IllegalStateException("Overpass answered 504"))
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)
        advanceUntilIdle()
        vm.handleIntent(HomeIntent.TogglePublicParkingsLayer)
        advanceUntilIdle()
        assertEquals(1, parkingsSource.askCount)
        assertEquals(null, vm.state.value.parkingsLayer.anchorLat, "a failed ask keeps no anchor")

        // The network comes back and the same centre is offered again: it MUST ask.
        parkingsSource.result = Result.success(listOf(spyParking()))
        locationDataSource.emitHighAccuracy(location)
        advanceUntilIdle()

        assertEquals(2, parkingsSource.askCount)
        assertEquals(1, vm.state.value.parkingsLayer.parkings.size)
    }

    // ── La sección responde sobre DONDE MIRAS [A-SECTION-ANSWERS-ABOUT-WHERE-YOU-LOOK-001] ──

    @Test
    fun `should_askAboutTheCamera_not_theUser_when_theSectionOpens`() = runTest(testDispatcher) {
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
        vm.handleIntent(HomeIntent.TogglePublicParkings)
        advanceUntilIdle()

        assertEquals(LEJOS_LAT, parkingsSource.seen?.latitude)
        assertEquals(LEJOS_LON, parkingsSource.seen?.longitude)
    }

    @Test
    fun `should_refetchTheOpenSection_when_theCameraSettlesFarEnough`() = runTest(testDispatcher) {
        parkingsSource.result = Result.success(listOf(spyParking()))
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)
        advanceUntilIdle()
        vm.handleIntent(HomeIntent.TogglePublicParkings)
        advanceUntilIdle()
        val tras = parkingsSource.askCount

        vm.handleIntent(HomeIntent.CameraPositionChanged(LEJOS_LAT, LEJOS_LON))
        advanceUntilIdle()

        assertEquals(tras + 1, parkingsSource.askCount, "la sección abierta viaja con la cámara")
        assertEquals(LEJOS_LAT, parkingsSource.seen?.latitude)
    }

    @Test
    fun `should_notRefetch_when_theSectionIsFolded`() = runTest(testDispatcher) {
        // ⛔ Sigue valiendo la regla que sostiene todo esto: plegada no descarga NADA. Un usuario que
        // solo mueve el mapa no paga peticiones por una lista que nunca abrió.
        parkingsSource.result = Result.success(listOf(spyParking()))
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)
        advanceUntilIdle()
        assertEquals(0, parkingsSource.askCount)

        vm.handleIntent(HomeIntent.CameraPositionChanged(LEJOS_LAT, LEJOS_LON))
        advanceUntilIdle()

        assertEquals(0, parkingsSource.askCount, "plegada no pregunta ni aunque la cámara viaje")
    }

    @Test
    fun `should_notRefetch_when_theCameraBarelyMoved`() = runTest(testDispatcher) {
        // Medio radio, como las capas: un temblor del dedo no es una pregunta nueva.
        parkingsSource.result = Result.success(listOf(spyParking()))
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitHighAccuracy(location)
        advanceUntilIdle()
        vm.handleIntent(HomeIntent.TogglePublicParkings)
        advanceUntilIdle()
        val tras = parkingsSource.askCount

        // ~200 m: muy por debajo de la mitad de los 1.500 m del radio.
        vm.handleIntent(HomeIntent.CameraPositionChanged(location.latitude + 0.0018, location.longitude))
        advanceUntilIdle()

        assertEquals(tras, parkingsSource.askCount)
    }

    /** Otra ciudad: lo bastante lejos para exigir una pregunta nueva, dentro del límite de 50 km. */
    private val LEJOS_LAT = location.latitude + 0.15
    private val LEJOS_LON = location.longitude + 0.15

    /** Counts the questions actually asked, like its fuel twin. */
    private class SpyPublicParkingsSource : PublicParkingsSource {
        var askCount = 0
        /** DONDE se preguntó por última vez — lo que distingue "contestó" de "contestó del sitio
         *  correcto". [A-SECTION-ANSWERS-ABOUT-WHERE-YOU-LOOK-001] */
        var seen: PublicParkingsQuery? = null
        var result: Result<List<PublicParking>> = Result.success(emptyList())
        override fun covers(countryCode: String) = true
        override suspend fun parkingsNear(query: PublicParkingsQuery): Result<List<PublicParking>> {
            askCount++
            seen = query
            return result
        }
    }
}
