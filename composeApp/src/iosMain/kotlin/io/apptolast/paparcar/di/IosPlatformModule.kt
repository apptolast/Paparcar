@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.apptolast.paparcar.di

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.apptolast.paparcar.bluetooth.IosBluetoothScanner
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
    // [AUDIT-DATA-001 M7] Full contiguous migration chain (v5+), matching Android — iOS previously
    // registered only 8_9/9_10/10_11 and would have wiped any v5-v8 database. Destructive fallback
    // stays as the last-resort net for the never-shipped pre-v5 case. See AndroidPlatformModule.
    single<AppDatabase> {
        val dbFilePath = documentDirectory() + "/paparcar.db"
        Room.databaseBuilder<AppDatabase>(name = dbFilePath)
            .setDriver(BundledSQLiteDriver())
            .addMigrations(
                MIGRATION_2_3, MIGRATION_3_4, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
                MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12,
            )
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
