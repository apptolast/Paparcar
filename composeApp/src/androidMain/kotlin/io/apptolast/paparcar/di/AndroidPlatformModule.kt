package io.apptolast.paparcar.di

import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import com.google.android.gms.location.LocationServices
import io.apptolast.paparcar.data.datasource.local.room.AppDatabase
import io.apptolast.paparcar.data.datasource.platform.PlatformLocationDataSource
import io.apptolast.paparcar.data.notification.AppNotificationManager
import io.apptolast.paparcar.domain.permissions.PermissionManager
import io.apptolast.paparcar.location.AndroidLocationDataSourceImpl
import io.apptolast.paparcar.notification.AppNotificationManagerImpl
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

    // Notification
    single { androidContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
    single<AppNotificationManager> { AppNotificationManagerImpl(androidContext(), get()) }

    // Permissions
    single<PermissionManager> { PermissionManagerImpl(androidContext()) }
}
