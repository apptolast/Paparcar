package io.apptolast.paparcar.di

import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.android.gms.location.LocationServices
import io.apptolast.paparcar.data.datasource.local.room.AppDatabase
import io.apptolast.paparcar.data.datasource.platform.PlatformLocationDataSource
import io.apptolast.paparcar.domain.geocoder.GeocoderDataSource
import io.apptolast.paparcar.domain.notification.AppNotificationManager
import io.apptolast.paparcar.domain.places.PlacesDataSource
import io.apptolast.paparcar.domain.permissions.PermissionManager
import io.apptolast.paparcar.domain.preferences.AppPreferences
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

val androidPlatformModule = module {
    // Database
    single<AppDatabase> {
        Room.databaseBuilder(
            androidContext(),
            AppDatabase::class.java,
            "paparcar.db"
        ).addMigrations(MIGRATION_3_4)
            .fallbackToDestructiveMigration(true)
            .build()
    }

    // Location
    single { LocationServices.getFusedLocationProviderClient(androidContext()) }
    single<PlatformLocationDataSource> { AndroidLocationDataSourceImpl(get()) }
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
}
