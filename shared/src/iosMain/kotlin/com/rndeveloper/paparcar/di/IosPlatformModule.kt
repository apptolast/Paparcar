@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.rndeveloper.paparcar.di

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.rndeveloper.paparcar.bluetooth.IosBluetoothScanner
import com.rndeveloper.paparcar.bluetooth.IosCarAudioRouteSource
import com.rndeveloper.paparcar.bluetooth.IosCarLinkDeclineStore
import com.rndeveloper.paparcar.domain.detection.ports.CarAudioRouteSource
import com.rndeveloper.paparcar.domain.detection.ports.CarLinkDeclineStore
import com.rndeveloper.paparcar.data.datasource.local.room.ALL_MIGRATIONS
import com.rndeveloper.paparcar.data.datasource.local.room.AppDatabase
import com.rndeveloper.paparcar.domain.bluetooth.BluetoothScanner
import com.rndeveloper.paparcar.diagnostics.IosDeviceInfoProvider
import com.rndeveloper.paparcar.domain.diagnostics.LocalDiagnosticsLog
import com.rndeveloper.paparcar.logging.IosLocalDiagnosticsLog
import com.rndeveloper.paparcar.domain.connectivity.ConnectivityObserver
import com.rndeveloper.paparcar.domain.diagnostics.DeviceInfoProvider
import com.rndeveloper.paparcar.domain.model.DeviceCapabilities
import com.rndeveloper.paparcar.domain.location.LocationDataSource
import com.rndeveloper.paparcar.domain.geocoder.GeocoderDataSource
import com.rndeveloper.paparcar.domain.notification.AppNotificationManager
import com.rndeveloper.paparcar.domain.permissions.OemBackgroundReliabilityManager
import com.rndeveloper.paparcar.domain.permissions.PermissionManager
import com.rndeveloper.paparcar.domain.preferences.AppPreferences
import com.rndeveloper.paparcar.domain.push.PushTokenProvider
import com.rndeveloper.paparcar.domain.push.SharedCarFenceRefresher
import com.rndeveloper.paparcar.push.IosPushRegistry
import com.rndeveloper.paparcar.push.IosSharedCarFenceRefresher
import com.rndeveloper.paparcar.push.PushMessageHandler
import com.rndeveloper.paparcar.push.SharedCarMemberSync
import com.rndeveloper.paparcar.push.ZoneAlertSubscriptionSync
import com.rndeveloper.paparcar.connectivity.IosConnectivityObserver
import com.rndeveloper.paparcar.ios.preferences.IosAppPreferences
import com.rndeveloper.paparcar.location.IosGeocoderDataSourceImpl
import com.rndeveloper.paparcar.location.IosLocationDataSourceImpl
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
    // ⛔ Places NO está aquí: era una copia literal de la de Android [IOS-PLACES-001], y esa copia es
    // exactamente el motivo por el que el failover de espejos se arregló en una y no en la otra.
    // Vive en commonMain y se registra en `dataModule`. [OVERPASS-A-SINGLE-MIRROR-HAS-NO-FAILOVER-001]
    // Road network, lo mismo: `OverpassRoadNetworkDataSource` (commonMain) sirve a las dos
    // plataformas desde `dataModule`, y con él el map-matching en vivo de iOS. [IOS-A-PARITY-TAIL-001]

    // Notifications — real iOS implementation (UNUserNotificationCenter)
    single<AppNotificationManager> { IosAppNotificationManagerImpl(appPreferences = get()) }

    // Permissions — real iOS implementation (CLLocationManager + CoreMotion + UserNotifications)
    single<PermissionManager> { IosPermissionManagerImpl() }
    // OEM autostart whitelist — no iOS equivalent, stub always reports "not required"
    single<OemBackgroundReliabilityManager> { IosOemBackgroundReliabilityManagerImpl() }

    // Platform capabilities — iOS offers NEITHER reliability remedy: BT Classic ACL events are
    // invisible to third-party apps and no battery-exemption concept exists. Tier ceiling =
    // ASSISTED; the evaluator reads both legs as N/A (no issues, no impossible CTAs). [IOS-F0-03]
    single { DeviceCapabilities(supportsBtStrategy = false, supportsBatteryExemption = false) }

    // Preferences — real iOS implementation (NSUserDefaults)
    single<AppPreferences> { IosAppPreferences() }

    // Connectivity — real iOS implementation (nw_path_monitor)
    single<ConnectivityObserver> { IosConnectivityObserver() }

    // Bluetooth — honest full stub: the BT lane cannot exist on iOS, and touching CoreBluetooth
    // just to answer adapter state would trigger a permission dialog that unlocks nothing.
    // [IOS-A-BT-LANE-NEEDS-A-REAL-SIGNAL-001]
    single<BluetoothScanner> { IosBluetoothScanner() }

    // [IOS-DET-AUDIO-ROUTE-PROVES-WHOSE-CAR-001] The car's BT as a SENSOR: reads AVAudioSession's
    // current route (A2DP only), passively — no session activation, no permission.
    single<CarAudioRouteSource> { IosCarAudioRouteSource() }
    single<CarLinkDeclineStore> { IosCarLinkDeclineStore() }

    // Diagnostics — device identity stamped into detection traces [DIAG-READABLE-001]
    single<DeviceInfoProvider> { IosDeviceInfoProvider() }

    // El `parkdiag` que escribe [IosFileAntilog], listo para viajar en un informe de usuario.
    // ⛔ Hasta [IOS-A-DETECTION-THAT-FAILS-MUST-LEAVE-A-TRACE-001] esto NO estaba ligado, y
    // `SendDiagnosticsReportUseCase` lo resuelve con `getOrNull()`: el informe de un iPhone subía
    // sólo cabeceras, en silencio y sin que nadie tuviera que equivocarse para que pasara.
    single<LocalDiagnosticsLog> { IosLocalDiagnosticsLog.default() }

    // ── Push [IOS-THE-GROUP-CANNOT-REACH-AN-IPHONE-001] ───────────────────────────────────────────
    // El reparto y las dos vigilancias son de `commonMain`; aquí solo se dice de dónde sale el token
    // (Swift, vía APNs) y cómo se le pide al controlador que reconcilie las vallas ya.
    single<PushTokenProvider> { IosPushRegistry }
    single<SharedCarFenceRefresher> { IosSharedCarFenceRefresher(controller = get()) }

    single {
        PushMessageHandler(
            notificationPort = get(),
            zoneWatch = get(),
            parkingRepository = get(),
            sharedCarFences = get(),
            authRepository = get(),
            zoneRepository = get(),
            sharedCarRepository = get(),
        )
    }

    // ⛔ `single`, las dos: `PaparcarIosBootstrap` les pide un resync cuando el token rota, y dos
    // instancias de cada una tendrían media verdad cada una.
    single {
        ZoneAlertSubscriptionSync(
            zoneRepository = get(),
            subscriptionRepository = get(),
            observeIsPlus = get(),
            appPreferences = get(),
            authRepository = get(),
            pushTokens = get(),
        )
    }

    single {
        SharedCarMemberSync(
            sharedCarRepository = get(),
            remote = get(),
            authRepository = get(),
            pushTokens = get(),
        )
    }
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
