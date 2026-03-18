@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.apptolast.paparcar.di

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.apptolast.paparcar.data.datasource.local.room.AppDatabase
import io.apptolast.paparcar.domain.location.LocationDataSource
import io.apptolast.paparcar.domain.geocoder.GeocoderDataSource
import io.apptolast.paparcar.domain.notification.AppNotificationManager
import io.apptolast.paparcar.domain.permissions.PermissionManager
import io.apptolast.paparcar.domain.places.PlacesDataSource
import io.apptolast.paparcar.domain.preferences.AppPreferences
import io.apptolast.paparcar.ios.preferences.IosAppPreferences
import io.apptolast.paparcar.ios.stub.StubAppNotificationManager
import io.apptolast.paparcar.ios.stub.StubGeocoderDataSource
import io.apptolast.paparcar.ios.stub.StubLocationDataSource
import io.apptolast.paparcar.ios.stub.StubPlacesDataSource
import io.apptolast.paparcar.permissions.IosPermissionManagerImpl
import org.koin.dsl.module
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

val iosPlatformModule = module {
    // Database
    single<AppDatabase> {
        val dbFilePath = documentDirectory() + "/paparcar.db"
        Room.databaseBuilder<AppDatabase>(name = dbFilePath)
            .setDriver(BundledSQLiteDriver())
            .fallbackToDestructiveMigration(true)
            .build()
    }

    // Location (stub)
    single<LocationDataSource> { StubLocationDataSource() }
    single<GeocoderDataSource> { StubGeocoderDataSource() }
    single<PlacesDataSource> { StubPlacesDataSource() }

    // Notifications (stub)
    single<AppNotificationManager> { StubAppNotificationManager() }

    // Permissions — real iOS implementation (CLLocationManager + CoreMotion + UserNotifications)
    single<PermissionManager> { IosPermissionManagerImpl() }

    // Preferences — real iOS implementation (NSUserDefaults)
    single<AppPreferences> { IosAppPreferences() }
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
