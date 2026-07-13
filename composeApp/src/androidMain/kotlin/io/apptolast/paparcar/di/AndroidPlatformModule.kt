package io.apptolast.paparcar.di

import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import com.google.android.gms.location.LocationServices
import io.apptolast.paparcar.data.datasource.local.room.AppDatabase
import io.apptolast.paparcar.data.datasource.local.room.MIGRATION_2_3
import io.apptolast.paparcar.data.datasource.local.room.MIGRATION_3_4
import io.apptolast.paparcar.data.datasource.local.room.MIGRATION_5_6
import io.apptolast.paparcar.data.datasource.local.room.MIGRATION_6_7
import io.apptolast.paparcar.data.datasource.local.room.MIGRATION_7_8
import io.apptolast.paparcar.data.datasource.local.room.MIGRATION_8_9
import io.apptolast.paparcar.data.datasource.local.room.MIGRATION_9_10
import io.apptolast.paparcar.data.datasource.local.room.MIGRATION_10_11
import io.apptolast.paparcar.data.datasource.local.room.MIGRATION_11_12
import io.apptolast.paparcar.domain.location.LocationDataSource
import io.apptolast.paparcar.domain.geocoder.GeocoderDataSource
import io.apptolast.paparcar.domain.notification.AppNotificationManager
import io.apptolast.paparcar.domain.places.PlacesDataSource
import io.apptolast.paparcar.domain.places.RoadNetworkDataSource
import io.apptolast.paparcar.domain.permissions.OemBackgroundReliabilityManager
import io.apptolast.paparcar.domain.permissions.PermissionManager
import io.apptolast.paparcar.domain.preferences.AppPreferences
import io.apptolast.paparcar.bluetooth.AndroidBluetoothScanner
import io.apptolast.paparcar.connectivity.AndroidConnectivityObserver
import io.apptolast.paparcar.diagnostics.AndroidDeviceInfoProvider
import io.apptolast.paparcar.domain.bluetooth.BluetoothScanner
import io.apptolast.paparcar.domain.connectivity.ConnectivityObserver
import io.apptolast.paparcar.domain.diagnostics.DeviceInfoProvider
import io.apptolast.paparcar.location.AndroidGeocoderDataSourceImpl
import io.apptolast.paparcar.location.AndroidLocationDataSourceImpl
import io.apptolast.paparcar.location.OverpassPlacesDataSourceImpl
import io.apptolast.paparcar.location.OverpassRoadNetworkDataSourceImpl
import io.apptolast.paparcar.notification.AppNotificationManagerImpl
import io.apptolast.paparcar.notification.ForegroundNotificationProvider
import io.apptolast.paparcar.permissions.OemBackgroundReliabilityManagerImpl
import io.apptolast.paparcar.permissions.PermissionManagerImpl
import io.apptolast.paparcar.preferences.AndroidDataStoreAppPreferences
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val androidPlatformModule = module {
    // [AUDIT-DATA-001 M7] Database migrations. The chain is now CONTIGUOUS from the first exported
    // schema (v5) to the current version — every v5+ upgrade migrates cleanly and preserves local
    // data (incl. un-synced offline edits). The destructive fallback below is now a last-resort
    // safety net that can ONLY fire for a pre-v5 database — a schema that was never exported and
    // never shipped in any release (the public beta baseline is the current version). No real
    // user can reach it; removing it entirely would trade a harmless wipe of a non-existent DB for
    // a hard crash, so it stays as the belt to the migration braces.
    single<AppDatabase> {
        Room.databaseBuilder(
            androidContext(),
            AppDatabase::class.java,
            "paparcar.db"
        ).addMigrations(
            MIGRATION_2_3, MIGRATION_3_4, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
            MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12,
        )
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    // Location
    single { LocationServices.getFusedLocationProviderClient(androidContext()) }
    single<LocationDataSource> { AndroidLocationDataSourceImpl(get()) }
    single<GeocoderDataSource> { AndroidGeocoderDataSourceImpl(androidContext()) }
    single<PlacesDataSource> { OverpassPlacesDataSourceImpl() }
    single<RoadNetworkDataSource> { OverpassRoadNetworkDataSourceImpl() }

    // Notification — single instance implements both contracts
    single { androidContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
    single { AppNotificationManagerImpl(androidContext(), get()) }
    single<AppNotificationManager> { get<AppNotificationManagerImpl>() }
    single<ForegroundNotificationProvider> { get<AppNotificationManagerImpl>() }

    // Permissions
    single<PermissionManager> { PermissionManagerImpl(androidContext()) }
    single<OemBackgroundReliabilityManager> { OemBackgroundReliabilityManagerImpl(androidContext()) }

    // Preferences
    single<AppPreferences> { AndroidDataStoreAppPreferences(androidContext()) }

    // Bluetooth
    single<BluetoothScanner> { AndroidBluetoothScanner(androidContext()) }

    // Connectivity
    single<ConnectivityObserver> { AndroidConnectivityObserver(androidContext()) }

    // Diagnostics — device identity stamped into detection traces [DIAG-READABLE-001]
    single<DeviceInfoProvider> { AndroidDeviceInfoProvider(androidContext()) }
}
