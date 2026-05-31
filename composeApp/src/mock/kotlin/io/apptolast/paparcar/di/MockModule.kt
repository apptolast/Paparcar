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
import io.apptolast.paparcar.domain.geocoder.GeocoderDataSource
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
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val mockModule = module {
    // DataSources
    single<LocationDataSource> { FakeLocationDataSource() }
    single<FirebaseDataSource> { FakeFirebaseDataSource() }
    single<AppNotificationManager> { FakeAppNotificationManager() }
    single<GeocoderDataSource> { FakeGeocoderDataSource() }
    single<PlacesDataSource> { FakePlacesDataSource() }
    single<PermissionManager> { FakePermissionManager() }
    single<OemBackgroundReliabilityManager> { FakeOemBackgroundReliabilityManager() }
    single<AppPreferences> { FakeAppPreferences() }
    single<BluetoothScanner> { FakeBluetoothScanner() }
    single<ConnectivityObserver> { FakeConnectivityObserver() }
    single<ActivityRecognitionManager> { FakeActivityRecognitionManager() }
    single<StepDetectorSource> { FakeStepDetectorSource() }
    single<GeofenceManager> { FakeGeofenceManager() }
    single<DepartureEventBus> { FakeDepartureEventBus() }
    single<GeofenceEventBus> { FakeGeofenceEventBus() }
    single<ParkingEnrichmentScheduler> { FakeParkingEnrichmentScheduler() }
    single<ParkingSyncScheduler> { FakeParkingSyncScheduler() }
    single<ReportSpotScheduler> { FakeReportSpotScheduler() }

    // Session
    single<LocalSessionCache> { RoomLocalSessionCache(get()) }

    // Repositories
    single<SpotRepository> { FakeSpotRepository() }
    single<AuthRepository> { FakeAuthRepository() }
    single<VehicleRepository> { FakeVehicleRepository() }
    single<UserParkingRepository> { FakeUserParkingRepository() }
    single<UserProfileRepository> { FakeUserProfileRepository() }
    single<ZoneRepository> { FakeZoneRepository() }

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
}
