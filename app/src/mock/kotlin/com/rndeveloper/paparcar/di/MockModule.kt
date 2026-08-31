package com.rndeveloper.paparcar.di

import androidx.room.Room
import com.apptolast.baselogin.domain.AuthRepository
import com.rndeveloper.paparcar.data.datasource.FakeLocationDataSource
import com.rndeveloper.paparcar.data.datasource.local.room.AppDatabase
import com.rndeveloper.paparcar.fakes.data.datasource.remote.FakeFirebaseDataSource
import com.rndeveloper.paparcar.data.datasource.remote.FirebaseDataSource
import com.rndeveloper.paparcar.domain.ActivityRecognitionManager
import com.rndeveloper.paparcar.domain.bluetooth.BluetoothScanner
import com.rndeveloper.paparcar.domain.connectivity.ConnectivityObserver
import com.rndeveloper.paparcar.data.geocoder.RoomLocalAddressAndPlaceDataSource
import com.rndeveloper.paparcar.data.repository.AddressAndPlaceRepositoryImpl
import com.rndeveloper.paparcar.domain.geocoder.GeocoderDataSource
import com.rndeveloper.paparcar.domain.geocoder.LocalAddressAndPlaceDataSource
import com.rndeveloper.paparcar.domain.location.LocationDataSource
import com.rndeveloper.paparcar.domain.notification.AppNotificationManager
import com.rndeveloper.paparcar.domain.diagnostics.DeviceInfoProvider
import com.rndeveloper.paparcar.domain.diagnostics.UnknownDeviceInfoProvider
import com.rndeveloper.paparcar.domain.permissions.OemBackgroundReliabilityManager
import com.rndeveloper.paparcar.domain.permissions.PermissionManager
import com.rndeveloper.paparcar.domain.places.PlacesDataSource
import com.rndeveloper.paparcar.domain.places.RoadNetworkDataSource
import com.rndeveloper.paparcar.location.OverpassRoadNetworkDataSourceImpl
import com.rndeveloper.paparcar.domain.preferences.AppPreferences
import com.rndeveloper.paparcar.domain.repository.*
import com.rndeveloper.paparcar.domain.sensor.StepDetectorSource
import com.rndeveloper.paparcar.domain.service.*
import com.rndeveloper.paparcar.notification.FakeAppNotificationManager
import com.rndeveloper.paparcar.data.session.RoomLocalSessionCache
import com.rndeveloper.paparcar.domain.session.LocalSessionCache
import com.rndeveloper.paparcar.fakes.data.repository.FakeActivityRecognitionManager
import com.rndeveloper.paparcar.fakes.data.repository.FakeAppPreferences
import com.rndeveloper.paparcar.fakes.data.repository.FakeAuthRepository
import com.rndeveloper.paparcar.fakes.data.repository.FakeBluetoothScanner
import com.rndeveloper.paparcar.fakes.data.repository.FakeConnectivityObserver
import com.rndeveloper.paparcar.fakes.data.repository.FakeDepartureEventBus
import com.rndeveloper.paparcar.fakes.data.repository.FakeDiagnosticsRepository
import com.rndeveloper.paparcar.fakes.data.repository.FakeGeocoderDataSource
import com.rndeveloper.paparcar.fakes.data.repository.FakeGeofenceEventBus
import com.rndeveloper.paparcar.fakes.data.repository.FakeGeofenceManager
import com.rndeveloper.paparcar.fakes.data.repository.FakeOemBackgroundReliabilityManager
import com.rndeveloper.paparcar.fakes.data.repository.FakeParkingEnrichmentScheduler
import com.rndeveloper.paparcar.fakes.data.repository.FakeParkingSyncScheduler
import com.rndeveloper.paparcar.fakes.data.repository.FakePermissionManager
import com.rndeveloper.paparcar.fakes.data.repository.FakePlacesDataSource
import com.rndeveloper.paparcar.fakes.data.repository.FakeReportSpotScheduler
import com.rndeveloper.paparcar.fakes.data.repository.FakeSpotRepository
import com.rndeveloper.paparcar.fakes.data.repository.FakeStepDetectorSource
import com.rndeveloper.paparcar.fakes.data.repository.FakeUserParkingRepository
import com.rndeveloper.paparcar.fakes.data.repository.FakeUserProfileRepository
import com.rndeveloper.paparcar.fakes.data.repository.FakeVehicleRepository
import com.rndeveloper.paparcar.fakes.data.repository.FakeZoneRepository
import com.rndeveloper.paparcar.fakes.MockScenario
import com.apptolast.baselogin.presentation.screens.login.LoginViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val mockModule = module {
    // Dev scenario shared by the scenario-aware fakes below and by the Dev Catalog UI.
    single { MockScenario() }

    // Library login screen's ViewModel (needs only AuthRepository, faked below). The library's
    // own presentationModule is internal, so we register this one explicitly — without it the
    // login screen crashes (NoDefinitionFound) when the Dev Catalog shows the LoggedOut flow.
    viewModelOf(::LoginViewModel)

    // DataSources
    // Pass the shared detection runtime so the mock location can "drive" while a trip is running. [DRIVE-SIM-001]
    single<LocationDataSource> { FakeLocationDataSource(get<com.rndeveloper.paparcar.domain.detection.DetectionRuntimeState>()) }
    single<FirebaseDataSource> { FakeFirebaseDataSource() }
    single<AppNotificationManager> { FakeAppNotificationManager() }
    // Ajustes resuelve DeviceInfoProvider (reporte de soporte). Solo lo ata androidPlatformModule,
    // que el build mock NO carga, asi que el tab CRASHEABA al abrirlo: Koin
    // NoDefinitionFoundException. El fallback de dominio ya existe y basta aqui.
    // [MOCK-SETTINGS-TAB-CRASHES-WITHOUT-DEVICE-INFO-001]
    single<DeviceInfoProvider> { UnknownDeviceInfoProvider }
    single<GeocoderDataSource> { FakeGeocoderDataSource() }
    single<PlacesDataSource> { FakePlacesDataSource() }
    // Real Overpass road source so the mock sim exercises live OSM map-matching on-device. [ROUTE-SNAP-001]
    single<RoadNetworkDataSource> { OverpassRoadNetworkDataSourceImpl() }
    single<PermissionManager> { FakePermissionManager(get()) }
    single<OemBackgroundReliabilityManager> { FakeOemBackgroundReliabilityManager(get()) }
    single<AppPreferences> { FakeAppPreferences(get()) }
    // Scenario-aware since [UI-MAP-PUCK-BELONGS-TO-THE-DRIVE-NOT-TO-ONE-LANE-001]: the connection is
    // what the BT trip surface reads, so the Dev Catalog lever has to drive it.
    single<BluetoothScanner> { FakeBluetoothScanner(get()) }
    single<ConnectivityObserver> { FakeConnectivityObserver(get()) }
    single<ActivityRecognitionManager> { FakeActivityRecognitionManager() }
    single<StepDetectorSource> { FakeStepDetectorSource() }
    single<GeofenceManager> { FakeGeofenceManager() }
    single<DepartureEventBus> { FakeDepartureEventBus() }
    single<GeofenceEventBus> { FakeGeofenceEventBus() }
    single<ParkingEnrichmentScheduler> { FakeParkingEnrichmentScheduler() }
    single<ParkingSyncScheduler> { FakeParkingSyncScheduler() }
    // Domain factories resolve the telemetry port with get(); the mock stack has no dataModule,
    // so the port must exist here or resolving ConfirmParkingUseCase crashes Home. [MOCKQA-001]
    single<com.rndeveloper.paparcar.domain.diagnostics.DetectionEventLogger> { com.rndeveloper.paparcar.domain.diagnostics.NoOpDetectionEventLogger() }
    single<com.rndeveloper.paparcar.domain.diagnostics.UiLocationLogger> { com.rndeveloper.paparcar.domain.diagnostics.NoOpUiLocationLogger() }
    single<ReportSpotScheduler> { FakeReportSpotScheduler() }
    single<com.rndeveloper.paparcar.domain.detection.ports.ManualParkingDetection> {
        // Same shared runtime, so the Home "I'm driving" CTA starts the sim too. [DRIVE-SIM-001]
        com.rndeveloper.paparcar.fakes.data.repository.FakeManualParkingDetection(get<com.rndeveloper.paparcar.domain.detection.MutableDetectionRuntimeState>())
    }
    single<com.rndeveloper.paparcar.domain.detection.ports.ArrivalHandoffDetection> {
        // [DET-HANDOFF-NOT-MANUAL-001] Own fake: "the safety net started it" must never be able to
        // satisfy an assertion about the user starting it.
        com.rndeveloper.paparcar.fakes.data.repository.FakeArrivalHandoffDetection(get<com.rndeveloper.paparcar.domain.detection.MutableDetectionRuntimeState>())
    }
    single<com.rndeveloper.paparcar.domain.detection.ports.DepartureWatchResumer> {
        // Same shared runtime: tapping "Reactivate" on the interrupted-watch row flips presence to
        // Sentry, so the row heals in the mock build like it does on device. [DET-WATCH-REACTIVATE-001]
        com.rndeveloper.paparcar.fakes.data.repository.FakeDepartureWatchResumer(get<com.rndeveloper.paparcar.domain.detection.MutableDetectionRuntimeState>())
    }

    // Session
    single<LocalSessionCache> { RoomLocalSessionCache(get()) }

    // Repositories
    single<SpotRepository> { FakeSpotRepository() }
    single<AuthRepository> { FakeAuthRepository(get()) }
    single<VehicleRepository> { FakeVehicleRepository(get()) }
    // Runtime-aware: while the driving sim runs, report no active session so readiness reaches Monitoring. [DRIVE-SIM-001]
    // Scenario-aware: the "own parked session" lever seeds an active session for the ACTIVE
    // vehicle so Home reaches the watching/parked state. [UX-PARKED-STATE-001]
    single<UserParkingRepository> {
        FakeUserParkingRepository(get<com.rndeveloper.paparcar.domain.detection.DetectionRuntimeState>(), get<MockScenario>())
    }
    single<UserProfileRepository> { FakeUserProfileRepository() }
    single<ZoneRepository> { FakeZoneRepository() }
    // DeleteAccountUseCase resolves this in its swept-repos list; the mock stack has no dataModule,
    // so the port must exist here or resolving it crashes. [ACCOUNT-DELETE-SWEEPS-DIAGNOSTICS-001]
    single<DiagnosticsRepository> { FakeDiagnosticsRepository() }
    // SendDiagnosticsReportUseCase resolves the uploader; same no-dataModule rule as above.
    // [SUPPORT-REPORT-SHIPS-THE-LOCAL-LOG-001]
    single<com.rndeveloper.paparcar.domain.diagnostics.DiagnosticsReportUploader> {
        com.rndeveloper.paparcar.fakes.data.repository.FakeDiagnosticsReportUploader()
    }
    single<AddressAndPlaceRepository> { AddressAndPlaceRepositoryImpl(get(), get(), get()) }

    // Database (In-memory Room)
    single<AppDatabase> {
        Room.inMemoryDatabaseBuilder(androidContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    // DAOs
    single { get<AppDatabase>().parkingSessionDao() }
    single { get<AppDatabase>().userProfileDao() }
    single { get<AppDatabase>().vehicleDao() }
    single { get<AppDatabase>().spotDao() }
    single { get<AppDatabase>().zoneDao() }
    single { get<AppDatabase>().geocoderCacheDao() }

    // Local datasources
    single<LocalAddressAndPlaceDataSource> { RoomLocalAddressAndPlaceDataSource(get()) }
}
