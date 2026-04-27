package io.apptolast.paparcar.di

import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.android.gms.location.LocationServices
import io.apptolast.paparcar.data.datasource.local.room.AppDatabase
import io.apptolast.paparcar.domain.location.LocationDataSource
import io.apptolast.paparcar.domain.geocoder.GeocoderDataSource
import io.apptolast.paparcar.domain.notification.AppNotificationManager
import io.apptolast.paparcar.domain.places.PlacesDataSource
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
import io.apptolast.paparcar.permissions.PermissionManagerImpl
import io.apptolast.paparcar.preferences.AndroidAppPreferences
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS locations")
    }
}

private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `user_profile` (
                `userId` TEXT NOT NULL,
                `email` TEXT,
                `displayName` TEXT,
                `photoUrl` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`userId`)
            )
            """.trimIndent()
        )
    }
}

private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE parking_sessions ADD COLUMN userId TEXT NOT NULL DEFAULT ''")
    }
}

private val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `cached_spots` (
                `id` TEXT NOT NULL,
                `latitude` REAL NOT NULL,
                `longitude` REAL NOT NULL,
                `accuracy` REAL NOT NULL,
                `reportedAt` INTEGER NOT NULL,
                `reportedBy` TEXT NOT NULL,
                `speed` REAL NOT NULL DEFAULT 0,
                `addressStreet` TEXT,
                `addressCity` TEXT,
                `addressRegion` TEXT,
                `addressCountry` TEXT,
                `placeInfoName` TEXT,
                `placeInfoCategory` TEXT,
                `type` TEXT NOT NULL DEFAULT 'AUTO_DETECTED',
                `confidence` REAL NOT NULL DEFAULT 1,
                `sizeCategory` TEXT,
                `enRouteCount` INTEGER NOT NULL DEFAULT 0,
                `expiresAt` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
    }
}

private val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `cached_spots` ADD COLUMN `acceptCount` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `cached_spots` ADD COLUMN `rejectCount` INTEGER NOT NULL DEFAULT 0")
    }
}

val androidPlatformModule = module {
    // Database
    single<AppDatabase> {
        Room.databaseBuilder(
            androidContext(),
            AppDatabase::class.java,
            "paparcar.db"
        ).addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_7_8, MIGRATION_8_9)
            .fallbackToDestructiveMigration(true)
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

    // Preferences
    single<AppPreferences> { AndroidAppPreferences(androidContext()) }

    // Bluetooth
    single<BluetoothScanner> { AndroidBluetoothScanner(androidContext()) }

    // Connectivity
    single<ConnectivityObserver> { AndroidConnectivityObserver(androidContext()) }
}
