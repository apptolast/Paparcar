@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.rndeveloper.paparcar.di

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.apptolast.baselogin.domain.AuthRepository
import com.rndeveloper.paparcar.data.datasource.FakeLocationDataSource
import com.rndeveloper.paparcar.data.datasource.local.room.AppDatabase
import com.rndeveloper.paparcar.data.datasource.remote.FirebaseDataSource
import com.rndeveloper.paparcar.data.session.RoomLocalSessionCache
import com.rndeveloper.paparcar.domain.ActivityRecognitionManager
import com.rndeveloper.paparcar.domain.bluetooth.BluetoothScanner
import com.rndeveloper.paparcar.domain.connectivity.ConnectivityObserver
import com.rndeveloper.paparcar.domain.detection.ports.ArrivalHandoffDetection
import com.rndeveloper.paparcar.domain.detection.ports.ManualParkingDetection
import com.rndeveloper.paparcar.domain.geocoder.GeocoderDataSource
import com.rndeveloper.paparcar.domain.location.LocationDataSource
import com.rndeveloper.paparcar.domain.notification.AppNotificationManager
import com.rndeveloper.paparcar.domain.permissions.OemBackgroundReliabilityManager
import com.rndeveloper.paparcar.domain.permissions.PermissionManager
import com.rndeveloper.paparcar.domain.places.PlacesDataSource
import com.rndeveloper.paparcar.domain.preferences.AppPreferences
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
import com.rndeveloper.paparcar.fakes.data.datasource.remote.FakeFirebaseDataSource
import com.rndeveloper.paparcar.fakes.data.repository.FakeActivityRecognitionManager
import com.rndeveloper.paparcar.fakes.data.repository.FakeAppPreferences
import com.rndeveloper.paparcar.fakes.data.repository.FakeAuthRepository
import com.rndeveloper.paparcar.fakes.data.repository.FakeBluetoothScanner
import com.rndeveloper.paparcar.fakes.data.repository.FakeConnectivityObserver
import com.rndeveloper.paparcar.fakes.data.repository.FakeDepartureEventBus
import com.rndeveloper.paparcar.fakes.data.repository.FakeGeocoderDataSource
import com.rndeveloper.paparcar.fakes.data.repository.FakeGeofenceEventBus
import com.rndeveloper.paparcar.fakes.data.repository.FakeGeofenceManager
import com.rndeveloper.paparcar.fakes.data.repository.FakeArrivalHandoffDetection
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

val iosMockModule = module {
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
    single<ManualParkingDetection> { FakeManualParkingDetection() }
    // [DET-HANDOFF-NOT-MANUAL-001] Separate port, separate fake.
    single<ArrivalHandoffDetection> { FakeArrivalHandoffDetection() }

    // Session
    single<LocalSessionCache> { RoomLocalSessionCache(get()) }

    // Repositories
    single<SpotRepository> { FakeSpotRepository() }
    single<AuthRepository> { FakeAuthRepository() }
    single<VehicleRepository> { FakeVehicleRepository() }
    single<UserParkingRepository> { FakeUserParkingRepository() }
    single<UserProfileRepository> { FakeUserProfileRepository() }
    single<ZoneRepository> { FakeZoneRepository() }

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
}
