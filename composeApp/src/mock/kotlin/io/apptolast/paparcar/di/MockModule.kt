package io.apptolast.paparcar.di

import androidx.room.Room
import com.apptolast.customlogin.domain.AuthRepository
import io.apptolast.paparcar.data.datasource.FakeLocationDataSource
import io.apptolast.paparcar.data.datasource.local.room.AppDatabase
import io.apptolast.paparcar.fakes.data.datasource.remote.FakeFirebaseDataSource
import io.apptolast.paparcar.data.datasource.remote.FirebaseDataSource
import io.apptolast.paparcar.domain.ActivityRecognitionManager
import io.apptolast.paparcar.domain.bluetooth.BluetoothScanner
import io.apptolast.paparcar.domain.connectivity.ConnectivityObserver
import io.apptolast.paparcar.data.geocoder.RoomLocalAddressAndPlaceDataSource
import io.apptolast.paparcar.data.repository.AddressAndPlaceRepositoryImpl
import io.apptolast.paparcar.domain.geocoder.GeocoderDataSource
import io.apptolast.paparcar.domain.geocoder.LocalAddressAndPlaceDataSource
import io.apptolast.paparcar.domain.location.LocationDataSource
import io.apptolast.paparcar.domain.notification.AppNotificationManager
import io.apptolast.paparcar.domain.permissions.OemBackgroundReliabilityManager
import io.apptolast.paparcar.domain.permissions.PermissionManager
import io.apptolast.paparcar.domain.places.PlacesDataSource
import io.apptolast.paparcar.domain.preferences.AppPreferences
import io.apptolast.paparcar.domain.repository.*
import io.apptolast.paparcar.domain.sensor.StepDetectorSource
import io.apptolast.paparcar.domain.service.*
import io.apptolast.paparcar.notification.FakeAppNotificationManager
import io.apptolast.paparcar.data.session.RoomLocalSessionCache
import io.apptolast.paparcar.domain.session.LocalSessionCache
import io.apptolast.paparcar.fakes.data.repository.FakeActivityRecognitionManager
import io.apptolast.paparcar.fakes.data.repository.FakeAppPreferences
import io.apptolast.paparcar.fakes.data.repository.FakeAuthRepository
import io.apptolast.paparcar.fakes.data.repository.FakeBluetoothScanner
import io.apptolast.paparcar.fakes.data.repository.FakeConnectivityObserver
import io.apptolast.paparcar.fakes.data.repository.FakeDepartureEventBus
import io.apptolast.paparcar.fakes.data.repository.FakeGeocoderDataSource
import io.apptolast.paparcar.fakes.data.repository.FakeGeofenceEventBus
import io.apptolast.paparcar.fakes.data.repository.FakeGeofenceManager
import io.apptolast.paparcar.fakes.data.repository.FakeOemBackgroundReliabilityManager
import io.apptolast.paparcar.fakes.data.repository.FakeParkingEnrichmentScheduler
import io.apptolast.paparcar.fakes.data.repository.FakeParkingSyncScheduler
import io.apptolast.paparcar.fakes.data.repository.FakePermissionManager
import io.apptolast.paparcar.fakes.data.repository.FakePlacesDataSource
import io.apptolast.paparcar.fakes.data.repository.FakeReportSpotScheduler
import io.apptolast.paparcar.fakes.data.repository.FakeSpotRepository
import io.apptolast.paparcar.fakes.data.repository.FakeStepDetectorSource
import io.apptolast.paparcar.fakes.data.repository.FakeUserParkingRepository
import io.apptolast.paparcar.fakes.data.repository.FakeUserProfileRepository
import io.apptolast.paparcar.fakes.data.repository.FakeVehicleRepository
import io.apptolast.paparcar.fakes.data.repository.FakeZoneRepository
import io.apptolast.paparcar.fakes.MockScenario
import com.apptolast.customlogin.presentation.screens.login.LoginViewModel
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
    single<LocationDataSource> { FakeLocationDataSource(get<io.apptolast.paparcar.domain.detection.DetectionRuntimeState>()) }
    single<FirebaseDataSource> { FakeFirebaseDataSource() }
    single<AppNotificationManager> { FakeAppNotificationManager() }
    single<GeocoderDataSource> { FakeGeocoderDataSource() }
    single<PlacesDataSource> { FakePlacesDataSource() }
    single<PermissionManager> { FakePermissionManager(get()) }
    single<OemBackgroundReliabilityManager> { FakeOemBackgroundReliabilityManager() }
    single<AppPreferences> { FakeAppPreferences(get()) }
    single<BluetoothScanner> { FakeBluetoothScanner() }
    single<ConnectivityObserver> { FakeConnectivityObserver(get()) }
    single<ActivityRecognitionManager> { FakeActivityRecognitionManager() }
    single<StepDetectorSource> { FakeStepDetectorSource() }
    single<GeofenceManager> { FakeGeofenceManager() }
    single<DepartureEventBus> { FakeDepartureEventBus() }
    single<GeofenceEventBus> { FakeGeofenceEventBus() }
    single<ParkingEnrichmentScheduler> { FakeParkingEnrichmentScheduler() }
    single<ParkingSyncScheduler> { FakeParkingSyncScheduler() }
    single<ReportSpotScheduler> { FakeReportSpotScheduler() }
    single<io.apptolast.paparcar.domain.detection.ManualParkingDetection> {
        // Same shared runtime, so the Home "I'm driving" CTA starts the sim too. [DRIVE-SIM-001]
        io.apptolast.paparcar.fakes.data.repository.FakeManualParkingDetection(get<io.apptolast.paparcar.domain.detection.MutableDetectionRuntimeState>())
    }

    // Session
    single<LocalSessionCache> { RoomLocalSessionCache(get()) }

    // Repositories
    single<SpotRepository> { FakeSpotRepository() }
    single<AuthRepository> { FakeAuthRepository(get()) }
    single<VehicleRepository> { FakeVehicleRepository(get()) }
    // Runtime-aware: while the driving sim runs, report no active session so readiness reaches Monitoring. [DRIVE-SIM-001]
    single<UserParkingRepository> { FakeUserParkingRepository(get<io.apptolast.paparcar.domain.detection.DetectionRuntimeState>()) }
    single<UserProfileRepository> { FakeUserProfileRepository() }
    single<ZoneRepository> { FakeZoneRepository() }
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
