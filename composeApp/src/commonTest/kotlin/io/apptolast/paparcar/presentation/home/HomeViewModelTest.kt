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
import io.apptolast.paparcar.domain.usecase.location.GetLocationInfoUseCase
import io.apptolast.paparcar.domain.usecase.location.SearchAddressUseCase
import io.apptolast.paparcar.domain.usecase.parking.ConfirmParkingUseCase
import io.apptolast.paparcar.domain.usecase.parking.ObserveParkedVehiclesUseCase
import io.apptolast.paparcar.domain.usecase.parking.ReleaseActiveParkingSessionUseCase
import io.apptolast.paparcar.domain.usecase.parking.UpdateParkingLocationUseCase
import io.apptolast.paparcar.domain.usecase.spot.ObserveNearbySpotsUseCase
import io.apptolast.paparcar.domain.usecase.spot.ReportSpotReleasedUseCase
import io.apptolast.paparcar.domain.usecase.spot.SendSpotSignalUseCase
import io.apptolast.paparcar.domain.usecase.zone.DeleteZoneUseCase
import io.apptolast.paparcar.domain.usecase.zone.ObserveZonesUseCase
import io.apptolast.paparcar.domain.usecase.zone.SaveZoneUseCase
import io.apptolast.paparcar.domain.usecase.zone.UpdateZoneUseCase
import io.apptolast.paparcar.fakes.FakeActivityRecognitionManager
import io.apptolast.paparcar.fakes.FakeAppNotificationManager
import io.apptolast.paparcar.fakes.FakeAppPreferences
import io.apptolast.paparcar.fakes.FakeAuthRepository
import io.apptolast.paparcar.fakes.FakeConnectivityObserver
import io.apptolast.paparcar.fakes.FakeGeocoderDataSource
import io.apptolast.paparcar.fakes.FakeGeofenceManager
import io.apptolast.paparcar.fakes.FakeLocationDataSource
import io.apptolast.paparcar.fakes.FakeParkingEnrichmentScheduler
import io.apptolast.paparcar.fakes.FakeParkingSyncScheduler
import io.apptolast.paparcar.fakes.FakePermissionManager
import io.apptolast.paparcar.fakes.FakePlacesDataSource
import io.apptolast.paparcar.fakes.FakeReportSpotScheduler
import io.apptolast.paparcar.fakes.FakeSpotRepository
import io.apptolast.paparcar.fakes.FakeUserParkingRepository
import io.apptolast.paparcar.fakes.FakeVehicleRepository
import io.apptolast.paparcar.fakes.FakeZoneRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
    private lateinit var vm: HomeViewModel

    private fun buildVm(initialMapType: String = "NORMAL"): HomeViewModel {
        prefs = FakeAppPreferences(initialDefaultMapType = initialMapType)
        val geocoder = FakeGeocoderDataSource()
        val places = FakePlacesDataSource()
        val getLocationInfo = GetLocationInfoUseCase(geocoder, places)
        val searchAddress = SearchAddressUseCase(geocoder)
        val observeNearbySpots = ObserveNearbySpotsUseCase(spotRepo)
        val sendSpotSignal = SendSpotSignalUseCase(spotRepo)
        val reportSpotReleased = ReportSpotReleasedUseCase(reportScheduler, getLocationInfo)
        val releaseSession = ReleaseActiveParkingSessionUseCase(reportSpotReleased, parkingRepo)
        val confirmParking = ConfirmParkingUseCase(
            userParkingRepository = parkingRepo,
            vehicleRepository = vehicleRepo,
            geofenceService = FakeGeofenceManager(),
            notificationPort = FakeAppNotificationManager(),
            enrichmentScheduler = FakeParkingEnrichmentScheduler(),
            parkingSyncScheduler = FakeParkingSyncScheduler(),
            authRepository = authRepo,
            config = ParkingDetectionConfig(),
        )
        val updateParkingLocation = UpdateParkingLocationUseCase(
            userParkingRepository = parkingRepo,
            geofenceService = FakeGeofenceManager(),
            enrichmentScheduler = FakeParkingEnrichmentScheduler(),
            config = ParkingDetectionConfig(),
        )
        val observeParkedVehicles = ObserveParkedVehiclesUseCase(parkingRepo, vehicleRepo)
        val zoneRepo = FakeZoneRepository()
        return HomeViewModel(
            permissionManager = permissions,
            locationDataSource = locationDataSource,
            observeNearbySpots = observeNearbySpots,
            reportSpotReleased = reportSpotReleased,
            activityRecognitionManager = activityRecognition,
            userParkingRepository = parkingRepo,
            releaseSession = releaseSession,
            getLocationInfo = getLocationInfo,
            confirmParking = confirmParking,
            observeParkedVehicles = observeParkedVehicles,
            updateParkingLocation = updateParkingLocation,
            searchAddress = searchAddress,
            appPreferences = prefs,
            sendSpotSignal = sendSpotSignal,
            connectivityObserver = connectivity,
            observeZones = ObserveZonesUseCase(zoneRepo),
            saveZone = SaveZoneUseCase(zoneRepo, authRepo),
            updateZone = UpdateZoneUseCase(zoneRepo),
            deleteZone = DeleteZoneUseCase(zoneRepo),
            vehicleRepository = vehicleRepo,
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
        vm.handleIntent(HomeIntent.SetMapType(MapType.NORMAL))
        // First SetMapType with same value as init — prefs should remain untouched
        assertEquals("NORMAL", prefs.defaultMapType)
        // Verify by switching and switching back
        vm.handleIntent(HomeIntent.SetMapType(MapType.SATELLITE))
        val countAfterFirst = prefs.defaultMapType
        vm.handleIntent(HomeIntent.SetMapType(MapType.SATELLITE))
        // calling with same type should NOT change the prefs value (no second write)
        assertEquals(countAfterFirst, prefs.defaultMapType)
    }

    // ── OpenHistory ───────────────────────────────────────────────────────────

    @Test
    fun `should_emit_NavigateToHistory_on_OpenHistory`() = runTest {
        vm.effect.test {
            vm.handleIntent(HomeIntent.OpenHistory)
            assertIs<HomeEffect.NavigateToHistory>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
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
        vm.handleIntent(HomeIntent.SetSizeFilter(io.apptolast.paparcar.domain.model.VehicleSize.SMALL))
        assertEquals(io.apptolast.paparcar.domain.model.VehicleSize.SMALL, vm.state.value.sizeFilter)
    }

    @Test
    fun `should_clear_sizeFilter_on_SetSizeFilter_null`() = runTest {
        vm.handleIntent(HomeIntent.SetSizeFilter(io.apptolast.paparcar.domain.model.VehicleSize.SMALL))
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
                sizeCategory = io.apptolast.paparcar.domain.model.VehicleSize.MEDIUM,
            ),
            extraVehicles = listOf(
                Vehicle(
                    id = "veh-B",
                    userId = "user-1",
                    brand = "Ford",
                    model = "Transit",
                    sizeCategory = io.apptolast.paparcar.domain.model.VehicleSize.LARGE,
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
}
