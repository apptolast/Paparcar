@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.apptolast.paparcar.di

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.apptolast.paparcar.data.datasource.local.room.AppDatabase
import io.apptolast.paparcar.data.datasource.platform.PlatformLocationDataSource
import io.apptolast.paparcar.domain.geocoder.GeocoderPort
import io.apptolast.paparcar.domain.notification.NotificationPort
import io.apptolast.paparcar.domain.permissions.PermissionManager
import io.apptolast.paparcar.domain.places.PlacesPort
import io.apptolast.paparcar.ios.stub.StubGeocoderPort
import io.apptolast.paparcar.ios.stub.StubLocationDataSource
import io.apptolast.paparcar.ios.stub.StubNotificationPort
import io.apptolast.paparcar.ios.stub.StubPermissionManager
import io.apptolast.paparcar.ios.stub.StubPlacesPort
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
    single<PlatformLocationDataSource> { StubLocationDataSource() }
    single<GeocoderPort> { StubGeocoderPort() }
    single<PlacesPort> { StubPlacesPort() }

    // Notifications (stub)
    single<NotificationPort> { StubNotificationPort() }

    // Permissions (stub — returns all granted to allow app flow)
    single<PermissionManager> { StubPermissionManager() }
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
