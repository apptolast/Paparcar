package com.rndeveloper.paparcar.di

import android.app.NotificationManager
import android.content.Context
import com.google.android.gms.location.LocationServices
import com.rndeveloper.paparcar.data.datasource.local.room.AppDatabase
import com.rndeveloper.paparcar.data.datasource.local.room.buildAppDatabase
import com.rndeveloper.paparcar.domain.location.LocationDataSource
import com.rndeveloper.paparcar.domain.model.DeviceCapabilities
import com.rndeveloper.paparcar.domain.geocoder.GeocoderDataSource
import com.rndeveloper.paparcar.domain.places.PlacesDataSource
import com.rndeveloper.paparcar.domain.places.RoadNetworkDataSource
import com.rndeveloper.paparcar.domain.permissions.OemBackgroundReliabilityManager
import com.rndeveloper.paparcar.domain.permissions.PermissionManager
import com.rndeveloper.paparcar.domain.preferences.AppPreferences
import com.rndeveloper.paparcar.bluetooth.AndroidBluetoothScanner
import com.rndeveloper.paparcar.connectivity.AndroidConnectivityObserver
import com.rndeveloper.paparcar.diagnostics.AndroidDeviceInfoProvider
import com.rndeveloper.paparcar.domain.bluetooth.BluetoothScanner
import com.rndeveloper.paparcar.domain.connectivity.ConnectivityObserver
import com.rndeveloper.paparcar.domain.diagnostics.DeviceInfoProvider
import com.rndeveloper.paparcar.domain.diagnostics.LocalDiagnosticsLog
import com.rndeveloper.paparcar.logging.AndroidLocalDiagnosticsLog
import com.rndeveloper.paparcar.location.AndroidGeocoderDataSourceImpl
import com.rndeveloper.paparcar.location.AndroidLocationDataSourceImpl
import com.rndeveloper.paparcar.location.OverpassPlacesDataSourceImpl
import com.rndeveloper.paparcar.location.OverpassRoadNetworkDataSourceImpl
import com.rndeveloper.paparcar.permissions.OemBackgroundReliabilityManagerImpl
import com.rndeveloper.paparcar.permissions.PermissionManagerImpl
import com.rndeveloper.paparcar.preferences.AndroidDataStoreAppPreferences
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val androidPlatformModule = module {
    // [DATA-ROOM-STARTS-AT-VERSION-ONE-001] The database starts at v1 and the migration chain is
    // empty, because no shipped install exists to migrate from. The destructive fallback is what
    // carries our own dev phones across: they hold a v2 file [DATA-ROOM-RETURNS-TO-VERSION-ONE-001]
    // and get a clean wipe on first launch instead of a crash. The first public release freezes
    // this — after it, every schema change needs its Migration.
    single<AppDatabase> { buildAppDatabase(androidContext()) }

    // Location
    single { LocationServices.getFusedLocationProviderClient(androidContext()) }
    single<LocationDataSource> { AndroidLocationDataSourceImpl(get()) }
    single<GeocoderDataSource> { AndroidGeocoderDataSourceImpl(androidContext()) }
    single<PlacesDataSource> { OverpassPlacesDataSourceImpl() }
    single<RoadNetworkDataSource> { OverpassRoadNetworkDataSourceImpl() }

    // Notification plumbing — the manager impl itself is bound in appModule (:app owns it)
    single { androidContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    // Permissions
    single<PermissionManager> { PermissionManagerImpl(androidContext()) }
    single<OemBackgroundReliabilityManager> { OemBackgroundReliabilityManagerImpl(androidContext()) }

    // Platform capabilities — Android offers both reliability remedies: the manifest ACL
    // receiver (BT strategy) and ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS. [IOS-F0-03]
    single { DeviceCapabilities(supportsBtStrategy = true, supportsBatteryExemption = true) }

    // Preferences
    single<AppPreferences> { AndroidDataStoreAppPreferences(androidContext()) }

    // Bluetooth
    single<BluetoothScanner> { AndroidBluetoothScanner(androidContext()) }

    // Connectivity
    single<ConnectivityObserver> { AndroidConnectivityObserver(androidContext()) }

    // Diagnostics — device identity stamped into detection traces [DIAG-READABLE-001]
    single<DeviceInfoProvider> { AndroidDeviceInfoProvider(androidContext(), get(), get()) }

    // "Report a problem" ships the local parkdiag history. [SUPPORT-REPORT-SHIPS-THE-LOCAL-LOG-001]
    single<LocalDiagnosticsLog> { AndroidLocalDiagnosticsLog(androidContext()) }
}
