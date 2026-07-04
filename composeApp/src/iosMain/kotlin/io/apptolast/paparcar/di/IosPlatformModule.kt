@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.apptolast.paparcar.di

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.apptolast.paparcar.bluetooth.IosBluetoothScanner
import io.apptolast.paparcar.data.datasource.local.room.AppDatabase
import io.apptolast.paparcar.data.datasource.local.room.MIGRATION_8_9
import io.apptolast.paparcar.data.datasource.local.room.MIGRATION_9_10
import io.apptolast.paparcar.data.datasource.local.room.MIGRATION_10_11
import io.apptolast.paparcar.domain.bluetooth.BluetoothScanner
import io.apptolast.paparcar.domain.connectivity.ConnectivityObserver
import io.apptolast.paparcar.domain.location.LocationDataSource
import io.apptolast.paparcar.domain.geocoder.GeocoderDataSource
import io.apptolast.paparcar.domain.notification.AppNotificationManager
import io.apptolast.paparcar.domain.permissions.OemBackgroundReliabilityManager
import io.apptolast.paparcar.domain.permissions.PermissionManager
import io.apptolast.paparcar.domain.places.PlacesDataSource
import io.apptolast.paparcar.domain.preferences.AppPreferences
import io.apptolast.paparcar.connectivity.IosConnectivityObserver
import io.apptolast.paparcar.ios.preferences.IosAppPreferences
import io.apptolast.paparcar.location.IosGeocoderDataSourceImpl
import io.apptolast.paparcar.location.IosLocationDataSourceImpl
import io.apptolast.paparcar.location.IosOverpassPlacesDataSourceImpl
import io.apptolast.paparcar.notification.IosAppNotificationManagerImpl
import io.apptolast.paparcar.permissions.IosOemBackgroundReliabilityManagerImpl
import io.apptolast.paparcar.permissions.IosPermissionManagerImpl
import org.koin.dsl.module
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

val iosPlatformModule = module {
    // Database — destructive migration on any version mismatch (internal pre-beta, no real users).
    single<AppDatabase> {
        val dbFilePath = documentDirectory() + "/paparcar.db"
        Room.databaseBuilder<AppDatabase>(name = dbFilePath)
            .setDriver(BundledSQLiteDriver())
            .addMigrations(MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
            .fallbackToDestructiveMigration(dropAllTables = true)
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
    // OEM autostart whitelist — no iOS equivalent, stub always reports "not required"
    single<OemBackgroundReliabilityManager> { IosOemBackgroundReliabilityManagerImpl() }

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
