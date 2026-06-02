package io.apptolast.paparcar.di

import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import com.google.android.gms.location.LocationServices
import io.apptolast.paparcar.data.datasource.local.room.AppDatabase
import io.apptolast.paparcar.data.datasource.local.room.MIGRATION_3_4
import io.apptolast.paparcar.data.datasource.local.room.MIGRATION_4_5
import io.apptolast.paparcar.data.datasource.local.room.MIGRATION_5_6
import io.apptolast.paparcar.data.datasource.local.room.MIGRATION_6_7
import io.apptolast.paparcar.data.datasource.local.room.MIGRATION_7_8
import io.apptolast.paparcar.data.datasource.local.room.MIGRATION_8_9
import io.apptolast.paparcar.domain.location.LocationDataSource
import io.apptolast.paparcar.domain.geocoder.GeocoderDataSource
import io.apptolast.paparcar.domain.notification.AppNotificationManager
import io.apptolast.paparcar.domain.places.PlacesDataSource
import io.apptolast.paparcar.domain.permissions.OemBackgroundReliabilityManager
import io.apptolast.paparcar.domain.permissions.PermissionManager
import io.apptolast.paparcar.domain.preferences.AppPreferences
import io.apptolast.paparcar.bluetooth.AndroidBluetoothScanner
import io.apptolast.paparcar.connectivity.AndroidConnectivityObserver
import io.apptolast.paparcar.domain.bluetooth.BluetoothScanner
import io.apptolast.paparcar.domain.connectivity.ConnectivityObserver
import io.apptolast.paparcar.location.AndroidGeocoderDataSourceImpl
import io.apptolast.paparcar.location.AndroidLocationDataSourceImpl
import io.apptolast.paparcar.location.OverpassPlacesDataSourceImpl
import io.apptolast.paparcar.notification.AppNotificationManagerImpl
import io.apptolast.paparcar.notification.ForegroundNotificationProvider
import io.apptolast.paparcar.permissions.OemBackgroundReliabilityManagerImpl
import io.apptolast.paparcar.permissions.PermissionManagerImpl
import io.apptolast.paparcar.preferences.AndroidDataStoreAppPreferences
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val androidPlatformModule = module {
    // Database
    // Destructive migration is gated to legacy versions (1, 2) only — pre-beta installs
    // with no real user data. From v3 onward, schema bumps MUST ship an explicit
    // Migration; otherwise Room will throw at startup. See AppDatabase doc. [DB-001]
    single<AppDatabase> {
        Room.databaseBuilder(
            androidContext(),
            AppDatabase::class.java,
            "paparcar.db"
        ).fallbackToDestructiveMigrationFrom(true, 1, 2)
            .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
            .build()
    }

    // Location
    single { LocationServices.getFusedLocationProviderClient(androidContext()) }
    single<LocationDataSource> { AndroidLocationDataSourceImpl(get()) }
    single<GeocoderDataSource> { AndroidGeocoderDataSourceImpl(androidContext()) }
    single<PlacesDataSource> { OverpassPlacesDataSourceImpl() }

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
}
