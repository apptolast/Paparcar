@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.presentation.home

import app.cash.turbine.test
import com.swmansion.kmpmaps.core.MapType
import io.apptolast.paparcar.domain.connectivity.ConnectivityStatus
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.usecase.location.GetAddressAndPlaceUseCase
import io.apptolast.paparcar.domain.usecase.location.SearchAddressUseCase
import io.apptolast.paparcar.domain.usecase.parking.ConfirmParkingUseCase
import io.apptolast.paparcar.domain.usecase.parking.ObserveParkedVehiclesUseCase
import io.apptolast.paparcar.domain.usecase.parking.ReleaseActiveParkingSessionUseCase
import io.apptolast.paparcar.domain.usecase.parking.UpdateParkingLocationUseCase
import io.apptolast.paparcar.domain.usecase.spot.ObserveNearbySpotsUseCase
import io.apptolast.paparcar.domain.usecase.spot.ReportSpotReleasedUseCase
import io.apptolast.paparcar.domain.usecase.spot.SendSpotSignalUseCase
import io.apptolast.paparcar.domain.event.MapFocusEventBus
import io.apptolast.paparcar.domain.usecase.zone.SaveZoneUseCase
import io.apptolast.paparcar.fakes.FakeActivityRecognitionManager
import io.apptolast.paparcar.fakes.FakeDepartureEventBus
import io.apptolast.paparcar.fakes.FakeAppNotificationManager
import io.apptolast.paparcar.fakes.FakeAppPreferences
import io.apptolast.paparcar.fakes.FakeAuthRepository
import io.apptolast.paparcar.fakes.FakeConnectivityObserver
import io.apptolast.paparcar.fakes.FakeGeocoderDataSource
import io.apptolast.paparcar.fakes.FakeGeofenceManager
import io.apptolast.paparcar.fakes.FakeAddressAndPlaceRepository
import io.apptolast.paparcar.fakes.FakeLocationDataSource
import io.apptolast.paparcar.fakes.FakeParkingEnrichmentScheduler
import io.apptolast.paparcar.fakes.FakePermissionManager
import io.apptolast.paparcar.fakes.FakeReportSpotScheduler
import io.apptolast.paparcar.fakes.FakeSpotRepository
import io.apptolast.paparcar.fakes.FakeUserParkingRepository
import io.apptolast.paparcar.fakes.FakeVehicleRepository
import io.apptolast.paparcar.fakes.FakeZoneRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val location = GpsPoint(40.416775, -3.703790, 10f, 0L, 0f)

    private lateinit var permissions: FakePermissionManager
    private lateinit var locationDataSource: FakeLocationDataSource
    private lateinit var spotRepo: FakeSpotRepository
    private lateinit var parkingRepo: FakeUserParkingRepository
    private lateinit var vehicleRepo: FakeVehicleRepository
    private lateinit var authRepo: FakeAuthRepository
    private lateinit var connectivity: FakeConnectivityObserver
    private lateinit var prefs: FakeAppPreferences
    private lateinit var activityRecognition: FakeActivityRecognitionManager
    private lateinit var reportScheduler: FakeReportSpotScheduler
    private lateinit var zoneRepo: FakeZoneRepository
    private lateinit var vm: HomeViewModel

    private fun buildVm(initialMapType: String = "TERRAIN"): HomeViewModel {
        prefs = FakeAppPreferences(initialDefaultMapType = initialMapType)
        val addressAndPlaceRepo = FakeAddressAndPlaceRepository()
        val getAddressAndPlace = GetAddressAndPlaceUseCase(repository = addressAndPlaceRepo)
        val searchAddress = SearchAddressUseCase(FakeGeocoderDataSource())
        val observeNearbySpots = ObserveNearbySpotsUseCase(spotRepo)
        val sendSpotSignal = SendSpotSignalUseCase(spotRepo)
        val reportSpotReleased = ReportSpotReleasedUseCase(reportScheduler, getAddressAndPlace, FakeAuthRepository(initialSession = null))
        val releaseSession = ReleaseActiveParkingSessionUseCase(reportSpotReleased, parkingRepo)
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
        return HomeViewModel(
            permissionManager = permissions,
            locationDataSource = locationDataSource,
            observeNearbySpots = observeNearbySpots,
            reportSpotReleased = reportSpotReleased,
            activityRecognitionManager = activityRecognition,
            userParkingRepository = parkingRepo,
            releaseSession = releaseSession,
            getAddressAndPlace = getAddressAndPlace,
            confirmParking = confirmParking,
            observeParkedVehicles = observeParkedVehicles,
            updateParkingLocation = updateParkingLocation,
            searchAddress = searchAddress,
            appPreferences = prefs,
            sendSpotSignal = sendSpotSignal,
            connectivityObserver = connectivity,
            zoneRepository = zoneRepo,
            saveZone = SaveZoneUseCase(zoneRepo, authRepo),
            vehicleRepository = vehicleRepo,
            mapFocusEventBus = MapFocusEventBus(),
            notificationPort = FakeAppNotificationManager(),
        )
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
        connectivity = FakeConnectivityObserver(ConnectivityStatus.Online)
        activityRecognition = FakeActivityRecognitionManager()
        reportScheduler = FakeReportSpotScheduler()
        zoneRepo = FakeZoneRepository()
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
    fun `should_apply_satellite_mapType_from_preferences`() = runTest {
        val vmSat = buildVm(initialMapType = "SATELLITE")
        assertEquals(MapType.SATELLITE, vmSat.state.value.mapType)
    }

    // ── Init — permissions + GPS chain ────────────────────────────────────────

    @Test
    fun `should_start_with_allPermissionsGranted_false`() = runTest {
        assertEquals(false, vm.state.value.allPermissionsGranted)
    }

    @Test
    fun `should_update_allPermissionsGranted_true_when_permissions_emitted`() = runTest {
        permissions.emit(FakePermissionManager.allGranted())
        assertEquals(true, vm.state.value.allPermissionsGranted)
    }

    @Test
    fun `should_update_userGpsPoint_when_location_emitted_with_permissions_granted`() = runTest {
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitBalanced(location)
        assertEquals(location, vm.state.value.userGpsPoint)
    }

    @Test
    fun `should_not_update_userGpsPoint_when_permissions_denied`() = runTest {
        locationDataSource.emitBalanced(location)
        assertNull(vm.state.value.userGpsPoint)
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
        locationDataSource.emitBalanced(location)

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
        locationDataSource.emitBalanced(location)
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
    fun `should_emit_RequestLocationPermission_on_LoadNearbySpots_when_denied`() = runTest {
        vm.effect.test {
            vm.handleIntent(HomeIntent.LoadNearbySpots)
            assertIs<HomeEffect.RequestLocationPermission>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
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

    // ── ShowParkingConfirmation / Dismiss ────────────────────────────────────

    @Test
    fun `should_set_pendingParkingGps_on_ShowParkingConfirmation`() = runTest {
        vm.handleIntent(HomeIntent.ShowParkingConfirmation(location))
        assertEquals(location, vm.state.value.pendingParkingGps)
    }

    @Test
    fun `should_clear_pendingParkingGps_on_DismissConfirmation`() = runTest {
        vm.handleIntent(HomeIntent.ShowParkingConfirmation(location))
        vm.handleIntent(HomeIntent.DismissConfirmation)
        assertNull(vm.state.value.pendingParkingGps)
    }

    // ── ConfirmDetectedParking ────────────────────────────────────────────────

    @Test
    fun `should_clear_pendingParkingGps_on_ConfirmDetectedParking`() = runTest {
        vm.handleIntent(HomeIntent.ShowParkingConfirmation(location))
        vm.handleIntent(HomeIntent.ConfirmDetectedParking)
        assertNull(vm.state.value.pendingParkingGps)
    }

    @Test
    fun `should_do_nothing_on_ConfirmDetectedParking_when_no_pending_gps`() = runTest {
        vm.handleIntent(HomeIntent.ConfirmDetectedParking)
        assertNull(vm.state.value.pendingParkingGps)
    }

    // ── AddingParking ─────────────────────────────────────────────────────────
    // `ManualPark` was retired — the empty-state CTA now opens AddingParking
    // (`EnterAddParkingMode` → user positions the pin → `ConfirmAddParking`).
    // The validation rules that used to live in the snap-to-GPS `manualPark()`
    // method now live in `confirmAddParking()` and are exercised on Confirm.

    @Test
    fun `should_emit_ShowError_on_ConfirmAddParking_when_no_GPS`() = runTest {
        vm.handleIntent(HomeIntent.EnterAddParkingMode(initialGps = null))
        vm.effect.test {
            vm.handleIntent(HomeIntent.ConfirmAddParking)
            assertIs<HomeEffect.ShowError>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should_emit_OfflineActionBlocked_on_ConfirmAddParking_when_offline`() = runTest {
        connectivity.emit(ConnectivityStatus.Offline)
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitBalanced(location)

        vm.handleIntent(HomeIntent.EnterAddParkingMode(initialGps = location))
        vm.effect.test {
            vm.handleIntent(HomeIntent.ConfirmAddParking)
            assertIs<HomeEffect.OfflineActionBlocked>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── ReleaseParking ────────────────────────────────────────────────────────

    @Test
    fun `should_emit_SpotReported_on_ReleaseParking_success`() = runTest {
        vm.effect.test {
            vm.handleIntent(HomeIntent.ReleaseParking(40.0, -3.7))
            assertIs<HomeEffect.SpotReported>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should_clear_selectedItemId_on_ReleaseParking_success`() = runTest {
        vm.handleIntent(HomeIntent.SelectItem("some-spot"))
        vm.handleIntent(HomeIntent.ReleaseParking(40.0, -3.7))
        assertNull(vm.state.value.selectedItemId)
    }

    // ── SelectItem ────────────────────────────────────────────────────────────

    @Test
    fun `should_set_selectedItemId_on_SelectItem`() = runTest {
        vm.handleIntent(HomeIntent.SelectItem("spot-42"))
        assertEquals("spot-42", vm.state.value.selectedItemId)
    }

    @Test
    fun `should_clear_selectedItemId_on_SelectItem_null`() = runTest {
        vm.handleIntent(HomeIntent.SelectItem("spot-42"))
        vm.handleIntent(HomeIntent.SelectItem(null))
        assertNull(vm.state.value.selectedItemId)
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

        vm.handleIntent(HomeIntent.SelectItem("spot-42"))

        val s = vm.state.value
        assertEquals(HomeMode.Browse, s.mode)
        assertEquals("spot-42", s.selectedItemId)
        assertNull(s.pinCameraLat)
        assertNull(s.pinCameraLon)
        assertNull(s.editingParkingId)
        assertNull(s.addingParkingVehicleId)
    }

    @Test
    fun `should_exit_Reporting_and_clear_reportingSize_on_SelectItem`() = runTest {
        vm.handleIntent(HomeIntent.EnterReportMode(lat = 40.0, lon = -3.7))
        vm.handleIntent(HomeIntent.SetReportingSize(io.apptolast.paparcar.domain.model.VehicleSize.MEDIUM_SUV))
        assertEquals(HomeMode.Reporting, vm.state.value.mode)

        vm.handleIntent(HomeIntent.SelectItem("spot-42"))

        val s = vm.state.value
        assertEquals(HomeMode.Browse, s.mode)
        assertEquals("spot-42", s.selectedItemId)
        assertNull(s.reportingSize)
        assertNull(s.pinCameraLat)
    }

    @Test
    fun `should_exit_AddingZone_and_clear_zone_form_on_SelectItem`() = runTest {
        vm.handleIntent(HomeIntent.EnterAddZoneMode(lat = 40.0, lon = -3.7))
        vm.handleIntent(HomeIntent.UpdateAddingZoneName("Casa"))
        vm.handleIntent(HomeIntent.SetZoneIsPrivate(true))
        assertEquals(HomeMode.AddingZone, vm.state.value.mode)

        vm.handleIntent(HomeIntent.SelectItem("spot-42"))

        val s = vm.state.value
        assertEquals(HomeMode.Browse, s.mode)
        assertEquals("spot-42", s.selectedItemId)
        assertEquals("", s.addingZoneName)
        assertEquals(false, s.addingZoneIsPrivate)
        assertNull(s.editingZoneId)
    }

    // ── SetMapType ────────────────────────────────────────────────────────────

    @Test
    fun `should_set_mapType_on_SetMapType`() = runTest {
        vm.handleIntent(HomeIntent.SetMapType(MapType.SATELLITE))
        assertEquals(MapType.SATELLITE, vm.state.value.mapType)
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
        vm.handleIntent(HomeIntent.SetMapType(MapType.SATELLITE))
        val countAfterFirst = prefs.defaultMapType
        vm.handleIntent(HomeIntent.SetMapType(MapType.SATELLITE))
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
    }

    // ── SetSizeFilter ─────────────────────────────────────────────────────────

    @Test
    fun `should_set_sizeFilter_on_SetSizeFilter`() = runTest {
        vm.handleIntent(HomeIntent.SetSizeFilter(io.apptolast.paparcar.domain.model.VehicleSize.MICRO_SMALL))
        assertEquals(io.apptolast.paparcar.domain.model.VehicleSize.MICRO_SMALL, vm.state.value.sizeFilter)
    }

    @Test
    fun `should_clear_sizeFilter_on_SetSizeFilter_null`() = runTest {
        vm.handleIntent(HomeIntent.SetSizeFilter(io.apptolast.paparcar.domain.model.VehicleSize.MICRO_SMALL))
        vm.handleIntent(HomeIntent.SetSizeFilter(null))
        assertNull(vm.state.value.sizeFilter)
    }

    // ── SendSpotSignal ────────────────────────────────────────────────────────

    @Test
    fun `should_emit_SpotSignalSent_on_SendSpotSignal_success`() = runTest {
        vm.effect.test {
            vm.handleIntent(HomeIntent.SendSpotSignal("spot-1", accepted = true))
            assertIs<HomeEffect.SpotSignalSent>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should_emit_ShowError_on_SendSpotSignal_failure`() = runTest {
        spotRepo.signalResult = Result.failure(RuntimeException("network error"))
        vm.effect.test {
            vm.handleIntent(HomeIntent.SendSpotSignal("spot-1", accepted = false))
            assertIs<HomeEffect.ShowError>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Connectivity reconnect tick ───────────────────────────────────────────

    @Test
    fun `should_trigger_spot_resubscription_on_offline_to_online_reconnect`() = runTest {
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitBalanced(location)

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

    // ── selectedItemId deselected when spot disappears ────────────────────────

    @Test
    fun `should_clear_selectedItemId_when_selected_spot_disappears_from_nearbySpots`() = runTest {
        val spot = Spot(id = "s1", location = location, reportedBy = "u1", address = null, placeInfo = null)
        spotRepo.spots = listOf(spot)
        permissions.emit(FakePermissionManager.allGranted())
        locationDataSource.emitBalanced(location)

        vm.handleIntent(HomeIntent.SelectItem("s1"))
        assertEquals("s1", vm.state.value.selectedItemId)

        spotRepo.spots = emptyList()
        assertNull(vm.state.value.selectedItemId)
    }

    @Test
    fun `should_keep_session_selectedItemId_when_spots_change`() = runTest {
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
        locationDataSource.emitBalanced(location)

        vm.handleIntent(HomeIntent.SelectItem(session.id))
        spotRepo.spots = emptyList()

        assertEquals(session.id, vm.state.value.selectedItemId)
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
                sizeCategory = io.apptolast.paparcar.domain.model.VehicleSize.MEDIUM_SUV,
            ),
            extraVehicles = listOf(
                Vehicle(
                    id = "veh-B",
                    userId = "user-1",
                    brand = "Ford",
                    model = "Transit",
                    sizeCategory = io.apptolast.paparcar.domain.model.VehicleSize.LARGE_SEDAN,
                ),
            ),
        )
        vm = buildVm()

        val cards = vm.state.value.vehicleCards
        assertEquals(2, cards.size)
        val parkedCard = cards.first { it.vehicle.id == "veh-A" }
        val emptyCard = cards.first { it.vehicle.id == "veh-B" }
        assertEquals("session-parked", parkedCard.session?.id)
        assertNull(emptyCard.session)
    }

    @Test
    fun `should_emit_empty_vehicleCards_when_no_vehicles_registered`() = runTest {
        // Default FakeVehicleRepository in setUp has no vehicles
        assertEquals(emptyList(), vm.state.value.vehicleCards)
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

        // User taps the second vehicle's row → selectedItemId == sessionB.id
        vm.handleIntent(HomeIntent.SelectItem(sessionB.id))
        vm.handleIntent(HomeIntent.ReleaseParking(location.latitude, location.longitude, publishSpot = false))

        // sessionA must remain active; sessionB cleared.
        val activeAfter = vm.state.value.activeSessions.map { it.id }.toSet()
        assertEquals(setOf("session-A"), activeAfter)
    }

    @Test
    fun `should_fall_back_to_first_active_session_on_release_when_nothing_selected`() = runTest {
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

        assertNull(vm.state.value.selectedItemId)
        vm.handleIntent(HomeIntent.ReleaseParking(location.latitude, location.longitude, publishSpot = false))

        val activeAfter = vm.state.value.activeSessions.map { it.id }.toSet()
        // First-emitted session (sessionA) is the legacy fallback target.
        assertEquals(setOf("session-B"), activeAfter)
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
        assertEquals(io.apptolast.paparcar.domain.model.Zone.DEFAULT_RADIUS_METERS, s.addingZoneRadius)
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

    @Test
    fun `should_prefill_radius_and_isPrivate_on_EnterEditZoneMode`() = runTest {
        val zone = io.apptolast.paparcar.domain.model.Zone(
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
            sizeCategory = io.apptolast.paparcar.domain.model.VehicleSize.LARGE_SEDAN,
            carbodyType = io.apptolast.paparcar.domain.model.CarbodyType.SEDAN,
            isActive = true,
        )
        vehicleRepo = FakeVehicleRepository(defaultVehicle = activeVehicle)
        vm = buildVm()

        vm.handleIntent(HomeIntent.EnterReportMode(lat = 40.0, lon = -3.7))
        vm.handleIntent(HomeIntent.ConfirmReportSpot)
        advanceUntilIdle()

        assertEquals(io.apptolast.paparcar.domain.model.CarbodyType.SEDAN, reportScheduler.lastCarbodyType)
        assertNull(reportScheduler.lastSizeCategory)
    }

    @Test
    fun `should_prefer_user_selected_size_over_active_vehicle_size_on_reportSpot`() = runTest {
        val activeVehicle = Vehicle(
            id = "v1",
            userId = "u1",
            sizeCategory = io.apptolast.paparcar.domain.model.VehicleSize.LARGE_SEDAN,
            carbodyType = io.apptolast.paparcar.domain.model.CarbodyType.SEDAN,
            isActive = true,
        )
        vehicleRepo = FakeVehicleRepository(defaultVehicle = activeVehicle)
        vm = buildVm()

        vm.handleIntent(HomeIntent.EnterReportMode(lat = 40.0, lon = -3.7))
        vm.handleIntent(HomeIntent.SetReportingSize(io.apptolast.paparcar.domain.model.VehicleSize.MICRO_SMALL))
        vm.handleIntent(HomeIntent.ConfirmReportSpot)
        advanceUntilIdle()

        // User-picked size wins; carbody still comes from the active vehicle.
        assertEquals(io.apptolast.paparcar.domain.model.VehicleSize.MICRO_SMALL, reportScheduler.lastSizeCategory)
        assertEquals(io.apptolast.paparcar.domain.model.CarbodyType.SEDAN, reportScheduler.lastCarbodyType)
    }

    @Test
    fun `should_clear_both_selection_fields_when_entering_AddingZone_with_zone_preselected`() = runTest {
        val zone = io.apptolast.paparcar.domain.model.Zone(
            id = "z1",
            userId = "u1",
            name = "Casa",
            lat = 40.0,
            lon = -3.7,
            iconKey = "home",
            createdAt = 0L,
        )
        zoneRepo.zones = listOf(zone)
        vm = buildVm()

        // Pre-condition: a zone is selected.
        vm.handleIntent(HomeIntent.SelectZone("z1"))
        assertEquals("z1", vm.state.value.selectedZoneId)

        // Now enter AddZone — selection invariants demand both selection fields null.
        vm.handleIntent(HomeIntent.EnterAddZoneMode(lat = 40.0, lon = -3.7))

        val s = vm.state.value
        assertEquals(HomeMode.AddingZone, s.mode)
        assertNull(s.selectedZoneId)
        assertNull(s.selectedItemId)
    }
}
