@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.rndeveloper.paparcar.di

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.rndeveloper.paparcar.bluetooth.IosBluetoothScanner
import com.rndeveloper.paparcar.data.datasource.local.room.ALL_MIGRATIONS
import com.rndeveloper.paparcar.data.datasource.local.room.AppDatabase
import com.rndeveloper.paparcar.domain.bluetooth.BluetoothScanner
import com.rndeveloper.paparcar.diagnostics.IosDeviceInfoProvider
import com.rndeveloper.paparcar.domain.connectivity.ConnectivityObserver
import com.rndeveloper.paparcar.domain.diagnostics.DeviceInfoProvider
import com.rndeveloper.paparcar.domain.location.LocationDataSource
import com.rndeveloper.paparcar.domain.geocoder.GeocoderDataSource
import com.rndeveloper.paparcar.domain.notification.AppNotificationManager
import com.rndeveloper.paparcar.domain.permissions.OemBackgroundReliabilityManager
import com.rndeveloper.paparcar.domain.permissions.PermissionManager
import com.rndeveloper.paparcar.domain.places.PlacesDataSource
import com.rndeveloper.paparcar.domain.preferences.AppPreferences
import com.rndeveloper.paparcar.connectivity.IosConnectivityObserver
import com.rndeveloper.paparcar.ios.preferences.IosAppPreferences
import com.rndeveloper.paparcar.location.IosGeocoderDataSourceImpl
import com.rndeveloper.paparcar.location.IosLocationDataSourceImpl
import com.rndeveloper.paparcar.location.IosOverpassPlacesDataSourceImpl
import com.rndeveloper.paparcar.notification.IosAppNotificationManagerImpl
import com.rndeveloper.paparcar.permissions.IosOemBackgroundReliabilityManagerImpl
import com.rndeveloper.paparcar.permissions.IosPermissionManagerImpl
import org.koin.dsl.module
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

val iosPlatformModule = module {
    // [DATA-ROOM-STARTS-AT-VERSION-ONE-001] v1 baseline — mirrors Android, INCLUDING the migration
    // chain [DB-A-NEW-COLUMN-NEEDS-ITS-MIGRATION-001]: with the destructive fallback below, a
    // version bump whose migration is missing here would not crash — it would silently wipe the
    // device. The shared ALL_MIGRATIONS list is what keeps the platforms from drifting, and it is
    // registered even while empty [DATA-ROOM-RETURNS-TO-VERSION-ONE-001] so that staying in sync
    // costs nothing on the day it stops being empty.
    single<AppDatabase> {
        val dbFilePath = documentDirectory() + "/paparcar.db"
        Room.databaseBuilder<AppDatabase>(name = dbFilePath)
            .setDriver(BundledSQLiteDriver())
            .addMigrations(*ALL_MIGRATIONS)
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

    // Diagnostics — device identity stamped into detection traces [DIAG-READABLE-001]
    single<DeviceInfoProvider> { IosDeviceInfoProvider() }
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
