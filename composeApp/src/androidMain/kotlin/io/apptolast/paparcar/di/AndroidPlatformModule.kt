package io.apptolast.paparcar.di

import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import com.google.android.gms.location.LocationServices
import io.apptolast.paparcar.data.datasource.local.room.AppDatabase
import io.apptolast.paparcar.data.datasource.platform.PlatformLocationDataSource
import io.apptolast.paparcar.domain.geocoder.GeocoderPort
import io.apptolast.paparcar.domain.notification.NotificationPort
import io.apptolast.paparcar.domain.permissions.PermissionManager
import io.apptolast.paparcar.location.AndroidGeocoderDataSource
import io.apptolast.paparcar.location.AndroidLocationDataSourceImpl
import io.apptolast.paparcar.notification.AppNotificationManagerImpl
import io.apptolast.paparcar.notification.ForegroundNotificationProvider
import io.apptolast.paparcar.permissions.PermissionManagerImpl
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val androidPlatformModule = module {
    // Database
    single<AppDatabase> {
        Room.databaseBuilder(
            androidContext(),
            AppDatabase::class.java,
            "paparcar.db"
        ).fallbackToDestructiveMigration(true)
            .build()
    }

    // Location
    single { LocationServices.getFusedLocationProviderClient(androidContext()) }
    single<PlatformLocationDataSource> { AndroidLocationDataSourceImpl(get()) }
    single<GeocoderPort> { AndroidGeocoderDataSource(androidContext()) }

    // Notification — single instance implements both contracts
    single { androidContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
    single { AppNotificationManagerImpl(androidContext(), get()) }
    single<NotificationPort> { get<AppNotificationManagerImpl>() }
    single<ForegroundNotificationProvider> { get<AppNotificationManagerImpl>() }

    // Permissions
    single<PermissionManager> { PermissionManagerImpl(androidContext()) }
}
