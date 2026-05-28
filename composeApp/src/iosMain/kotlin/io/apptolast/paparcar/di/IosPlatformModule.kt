@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.apptolast.paparcar.di

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.apptolast.paparcar.bluetooth.IosBluetoothScanner
import io.apptolast.paparcar.data.datasource.local.room.AppDatabase
import io.apptolast.paparcar.data.datasource.local.room.MIGRATION_3_4
import io.apptolast.paparcar.domain.bluetooth.BluetoothScanner
import io.apptolast.paparcar.domain.connectivity.ConnectivityObserver
import io.apptolast.paparcar.domain.location.LocationDataSource
import io.apptolast.paparcar.domain.geocoder.GeocoderDataSource
import io.apptolast.paparcar.domain.notification.AppNotificationManager
import io.apptolast.paparcar.domain.permissions.PermissionManager
import io.apptolast.paparcar.domain.places.PlacesDataSource
import io.apptolast.paparcar.domain.preferences.AppPreferences
import io.apptolast.paparcar.connectivity.IosConnectivityObserver
import io.apptolast.paparcar.ios.preferences.IosAppPreferences
import io.apptolast.paparcar.location.IosGeocoderDataSourceImpl
import io.apptolast.paparcar.location.IosLocationDataSourceImpl
import io.apptolast.paparcar.location.IosOverpassPlacesDataSourceImpl
import io.apptolast.paparcar.notification.IosAppNotificationManagerImpl
import io.apptolast.paparcar.permissions.IosPermissionManagerImpl
import org.koin.dsl.module
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

val iosPlatformModule = module {
    // Database
    // Destructive migration is gated to legacy versions (1, 2) only — pre-beta installs
    // with no real user data. From v3 onward, schema bumps MUST ship an explicit
    // Migration; otherwise Room will throw at startup. See AppDatabase doc. [DB-001]
    single<AppDatabase> {
        val dbFilePath = documentDirectory() + "/paparcar.db"
        Room.databaseBuilder<AppDatabase>(name = dbFilePath)
            .setDriver(BundledSQLiteDriver())
            .fallbackToDestructiveMigrationFrom(true, 1, 2)
            .addMigrations(MIGRATION_3_4)
            .build()
    }

    // Location — real iOS implementation (CLLocationManager)
    single<LocationDataSource> { IosLocationDataSourceImpl() }
    // Geocoder — real iOS implementation (CLGeocoder)
    single<GeocoderDataSource> { IosGeocoderDataSourceImpl() }
    // Places — real iOS implementation (Overpass API via NSURLSession) [IOS-PLACES-001]
    single<PlacesDataSource> { IosOverpassPlacesDataSourceImpl() }

    // Notifications — real iOS implementation (UNUserNotificationCenter)
    single<AppNotificationManager> { IosAppNotificationManagerImpl() }

    // Permissions — real iOS implementation (CLLocationManager + CoreMotion + UserNotifications)
    single<PermissionManager> { IosPermissionManagerImpl() }

    // Preferences — real iOS implementation (NSUserDefaults)
    single<AppPreferences> { IosAppPreferences() }

    // Connectivity — real iOS implementation (nw_path_monitor)
    single<ConnectivityObserver> { IosConnectivityObserver() }

    // Bluetooth — real iOS implementation (CBCentralManager for state; getBondedDevices is empty by design)
    single<BluetoothScanner> { IosBluetoothScanner() }
}

private fun documentDirectory(): String {
    val documentDirectory = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )
    return requireNotNull(documentDirectory?.path)
}
