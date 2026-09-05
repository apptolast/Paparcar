@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.rndeveloper.paparcar.di

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.apptolast.baselogin.domain.AuthRepository
import com.rndeveloper.paparcar.data.datasource.FakeLocationDataSource
import com.rndeveloper.paparcar.data.datasource.local.room.AppDatabase
import com.rndeveloper.paparcar.data.datasource.remote.FirebaseDataSource
import com.rndeveloper.paparcar.data.geocoder.RoomLocalAddressAndPlaceDataSource
import com.rndeveloper.paparcar.data.repository.AddressAndPlaceRepositoryImpl
import com.rndeveloper.paparcar.data.session.RoomLocalSessionCache
import com.rndeveloper.paparcar.domain.ActivityRecognitionManager
import com.rndeveloper.paparcar.domain.bluetooth.BluetoothScanner
import com.rndeveloper.paparcar.domain.connectivity.ConnectivityObserver
import com.rndeveloper.paparcar.domain.detection.DetectionRuntimeState
import com.rndeveloper.paparcar.domain.detection.MutableDetectionRuntimeState
import com.rndeveloper.paparcar.domain.detection.ports.ArrivalHandoffDetection
import com.rndeveloper.paparcar.domain.detection.ports.DepartureWatchResumer
import com.rndeveloper.paparcar.domain.detection.ports.ManualParkingDetection
import com.rndeveloper.paparcar.domain.diagnostics.DetectionEventLogger
import com.rndeveloper.paparcar.domain.diagnostics.DeviceInfoProvider
import com.rndeveloper.paparcar.domain.diagnostics.DiagnosticsReportUploader
import com.rndeveloper.paparcar.domain.diagnostics.NoOpDetectionEventLogger
import com.rndeveloper.paparcar.domain.diagnostics.NoOpUiLocationLogger
import com.rndeveloper.paparcar.domain.diagnostics.UiLocationLogger
import com.rndeveloper.paparcar.domain.diagnostics.UnknownDeviceInfoProvider
import com.rndeveloper.paparcar.domain.geocoder.GeocoderDataSource
import com.rndeveloper.paparcar.domain.geocoder.LocalAddressAndPlaceDataSource
import com.rndeveloper.paparcar.domain.location.LocationDataSource
import com.rndeveloper.paparcar.domain.model.DeviceCapabilities
import com.rndeveloper.paparcar.domain.notification.AppNotificationManager
import com.rndeveloper.paparcar.domain.permissions.OemBackgroundReliabilityManager
import com.rndeveloper.paparcar.domain.permissions.PermissionManager
import com.rndeveloper.paparcar.domain.places.PlacesDataSource
import com.rndeveloper.paparcar.domain.preferences.AppPreferences
import com.rndeveloper.paparcar.domain.repository.AddressAndPlaceRepository
import com.rndeveloper.paparcar.domain.repository.DiagnosticsRepository
import com.rndeveloper.paparcar.domain.repository.SpotRepository
import com.rndeveloper.paparcar.domain.repository.UserParkingRepository
import com.rndeveloper.paparcar.domain.repository.UserProfileRepository
import com.rndeveloper.paparcar.domain.repository.VehicleRepository
import com.rndeveloper.paparcar.domain.repository.ZoneRepository
import com.rndeveloper.paparcar.domain.sensor.StepDetectorSource
import com.rndeveloper.paparcar.domain.service.DepartureEventBus
import com.rndeveloper.paparcar.domain.service.GeofenceEventBus
import com.rndeveloper.paparcar.domain.service.GeofenceManager
import com.rndeveloper.paparcar.domain.service.ParkingEnrichmentScheduler
import com.rndeveloper.paparcar.domain.service.ParkingSyncScheduler
import com.rndeveloper.paparcar.domain.service.ReportSpotScheduler
import com.rndeveloper.paparcar.domain.session.LocalSessionCache
import com.rndeveloper.paparcar.fakes.MockScenario
import com.rndeveloper.paparcar.fakes.data.datasource.remote.FakeFirebaseDataSource
import com.rndeveloper.paparcar.fakes.data.repository.FakeActivityRecognitionManager
import com.rndeveloper.paparcar.fakes.data.repository.FakeAppPreferences
import com.rndeveloper.paparcar.fakes.data.repository.FakeArrivalHandoffDetection
import com.rndeveloper.paparcar.fakes.data.repository.FakeAuthRepository
import com.rndeveloper.paparcar.fakes.data.repository.FakeBluetoothScanner
import com.rndeveloper.paparcar.fakes.data.repository.FakeConnectivityObserver
import com.rndeveloper.paparcar.fakes.data.repository.FakeDepartureEventBus
import com.rndeveloper.paparcar.fakes.data.repository.FakeDepartureWatchResumer
import com.rndeveloper.paparcar.fakes.data.repository.FakeDiagnosticsReportUploader
import com.rndeveloper.paparcar.fakes.data.repository.FakeDiagnosticsRepository
import com.rndeveloper.paparcar.fakes.data.repository.FakeGeocoderDataSource
import com.rndeveloper.paparcar.fakes.data.repository.FakeGeofenceEventBus
import com.rndeveloper.paparcar.fakes.data.repository.FakeGeofenceManager
import com.rndeveloper.paparcar.fakes.data.repository.FakeManualParkingDetection
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
import com.rndeveloper.paparcar.notification.FakeAppNotificationManager
import org.koin.dsl.module

/**
 * Twin of `:app`'s `mockModule`, binding by binding — same contracts, same shared fakes, same
 * scenario/runtime wiring — minus what has no iOS side: `RoadNetworkDataSource` (its only impl is
 * androidMain Overpass; prod iOS does not bind it either, presentation degrades via `getOrNull`)
 * and `BuildConfig` (the login config takes a null web client id, like prod iOS —
 * IOS-SOCIAL-LOGIN-001). Drift between the two mock graphs is exactly what let
 * [IOS-DI-A-MOCK-GRAPH-ONLY-PROD-IS-VERIFIED-001] happen; the Android graph is verified by
 * `MockKoinGraphVerifyTest`, this one can only be verified once iOS tests run
 * (TEST-A-KMP-SUITE-THAT-ONLY-RUNS-ON-JVM-IS-HALF-A-SUITE-001) — until then, keep it mirrored
 * BY HAND in the same task that touches its twin.
 */
val iosMockModule = module {
    // Dev scenario shared by the scenario-aware fakes below and by the Dev Catalog UI.
    single { MockScenario() }

    // Mock skips BaseLogin's `initLoginKoin` (Firebase), which is what binds the config in prod.
    single { paparcarLoginConfig(googleWebClientId = null) }

    // DataSources
    // Pass the shared detection runtime so the mock location can "drive" while a trip is running. [DRIVE-SIM-001]
    single<LocationDataSource> { FakeLocationDataSource(get<DetectionRuntimeState>()) }
    single<FirebaseDataSource> { FakeFirebaseDataSource() }
    single<AppNotificationManager> { FakeAppNotificationManager() }
    // Ajustes resolves DeviceInfoProvider; only the platform modules bind it and mock loads none.
    // [MOCK-SETTINGS-TAB-CRASHES-WITHOUT-DEVICE-INFO-001]
    single<DeviceInfoProvider> { UnknownDeviceInfoProvider }
    single<GeocoderDataSource> { FakeGeocoderDataSource() }
    single<PlacesDataSource> { FakePlacesDataSource() }
    single<PermissionManager> { FakePermissionManager(get()) }
    // Prod binds this in each PLATFORM module, which the mock graph never loads. iOS literals:
    // no BT strategy, no battery exemption. [IOS-DI-A-MOCK-GRAPH-ONLY-PROD-IS-VERIFIED-001]
    single { DeviceCapabilities(supportsBtStrategy = false, supportsBatteryExemption = false) }
    single<OemBackgroundReliabilityManager> { FakeOemBackgroundReliabilityManager(get()) }
    single<AppPreferences> { FakeAppPreferences(get()) }
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
    single<DetectionEventLogger> { NoOpDetectionEventLogger() }
    single<UiLocationLogger> { NoOpUiLocationLogger() }
    single<ReportSpotScheduler> { FakeReportSpotScheduler() }
    single<ManualParkingDetection> {
        // Same shared runtime, so the Home "I'm driving" CTA starts the sim too. [DRIVE-SIM-001]
        FakeManualParkingDetection(get<MutableDetectionRuntimeState>())
    }
    single<ArrivalHandoffDetection> {
        // [DET-HANDOFF-NOT-MANUAL-001] Own fake: "the safety net started it" must never be able to
        // satisfy an assertion about the user starting it.
        FakeArrivalHandoffDetection(get<MutableDetectionRuntimeState>())
    }
    single<DepartureWatchResumer> {
        // Same shared runtime: tapping "Reactivate" on the interrupted-watch row flips presence to
        // Sentry, so the row heals in the mock build like it does on device. [DET-WATCH-REACTIVATE-001]
        FakeDepartureWatchResumer(get<MutableDetectionRuntimeState>())
    }

    // Session
    single<LocalSessionCache> { RoomLocalSessionCache(get()) }

    // Repositories
    single<SpotRepository> { FakeSpotRepository() }
    single<AuthRepository> { FakeAuthRepository(get()) }
    single<VehicleRepository> { FakeVehicleRepository(get()) }
    // Runtime-aware + scenario-aware, same as the Android twin. [DRIVE-SIM-001] [UX-PARKED-STATE-001]
    single<UserParkingRepository> {
        FakeUserParkingRepository(get<DetectionRuntimeState>(), get<MockScenario>())
    }
    single<UserProfileRepository> { FakeUserProfileRepository() }
    single<ZoneRepository> { FakeZoneRepository() }
    // DeleteAccountUseCase resolves this in its swept-repos list. [ACCOUNT-DELETE-SWEEPS-DIAGNOSTICS-001]
    single<DiagnosticsRepository> { FakeDiagnosticsRepository() }
    // SendDiagnosticsReportUseCase resolves the uploader. [SUPPORT-REPORT-SHIPS-THE-LOCAL-LOG-001]
    single<DiagnosticsReportUploader> { FakeDiagnosticsReportUploader() }
    single<AddressAndPlaceRepository> { AddressAndPlaceRepositoryImpl(get(), get(), get()) }
    // Real impl: static in-code data, no platform deps — the licenses screen shows as it ships.
    single<com.rndeveloper.paparcar.domain.repository.OpenSourceLicenseRepository> {
        com.rndeveloper.paparcar.data.repository.OpenSourceLicenseRepositoryImpl()
    }

    // Database (in-memory Room — no persistence, no migrations needed)
    single<AppDatabase> {
        Room.inMemoryDatabaseBuilder<AppDatabase>()
            .setDriver(BundledSQLiteDriver())
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
