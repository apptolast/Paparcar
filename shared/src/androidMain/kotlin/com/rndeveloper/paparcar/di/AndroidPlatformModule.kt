package com.rndeveloper.paparcar.di

import com.rndeveloper.paparcar.data.analytics.MetaAnalyticsTracker
import com.rndeveloper.paparcar.domain.analytics.AdAttributionTracker
import com.rndeveloper.paparcar.domain.push.PushTokenProvider
import com.rndeveloper.paparcar.domain.push.SharedCarFenceRefresher
import com.rndeveloper.paparcar.push.FirebaseMessagingTokenProvider
import com.rndeveloper.paparcar.push.JanitorSharedCarFenceRefresher
import com.rndeveloper.paparcar.push.PushMessageHandler
import com.rndeveloper.paparcar.push.SharedCarMemberSync
import com.rndeveloper.paparcar.push.ZoneAlertSubscriptionSync
import android.app.NotificationManager
import android.content.Context
import com.google.android.gms.location.LocationServices
import com.rndeveloper.paparcar.data.datasource.local.room.AppDatabase
import com.rndeveloper.paparcar.data.datasource.local.room.buildAppDatabase
import com.rndeveloper.paparcar.domain.location.LocationDataSource
import com.rndeveloper.paparcar.domain.model.DeviceCapabilities
import com.rndeveloper.paparcar.domain.geocoder.GeocoderDataSource
import com.rndeveloper.paparcar.domain.permissions.OemBackgroundReliabilityManager
import com.rndeveloper.paparcar.domain.permissions.PermissionManager
import com.rndeveloper.paparcar.domain.preferences.AppPreferences
import com.rndeveloper.paparcar.bluetooth.AndroidBluetoothScanner
import com.rndeveloper.paparcar.bluetooth.AndroidCarAudioRouteSource
import com.rndeveloper.paparcar.connectivity.AndroidConnectivityObserver
import com.rndeveloper.paparcar.diagnostics.AndroidDeviceInfoProvider
import com.rndeveloper.paparcar.domain.bluetooth.BluetoothScanner
import com.rndeveloper.paparcar.domain.connectivity.ConnectivityObserver
import com.rndeveloper.paparcar.domain.diagnostics.DeviceInfoProvider
import com.rndeveloper.paparcar.domain.diagnostics.LocalDiagnosticsLog
import com.rndeveloper.paparcar.logging.AndroidLocalDiagnosticsLog
import com.rndeveloper.paparcar.location.AndroidGeocoderDataSourceImpl
import com.rndeveloper.paparcar.location.AndroidLocationDataSourceImpl
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
    // Charging boost for the sampling tiers — while plugged in, density is free.
    // [DET-ADAPTIVE-SAMPLING-EARNS-ITS-FIXES-001]
    single<com.rndeveloper.paparcar.domain.location.ChargingStateSource> {
        com.rndeveloper.paparcar.location.AndroidChargingStateSource(androidContext())
    }
    // The measuring instrument bracketing GPS-holding jobs. [BATT-A-COST-IS-MEASURED-NOT-DECLARED-001]
    single<com.rndeveloper.paparcar.domain.detection.ports.BatteryMeter> {
        com.rndeveloper.paparcar.location.AndroidBatteryMeter(androidContext())
    }
    single<GeocoderDataSource> { AndroidGeocoderDataSourceImpl(androidContext()) }
    // ⛔ Places y carreteras NO están aquí: los dos hablan con Overpass, y una implementación por
    // plataforma es lo que dejó a tres vías usando un solo espejo sin failover. Viven en commonMain
    // y se registran en `dataModule`. [OVERPASS-A-SINGLE-MIRROR-HAS-NO-FAILOVER-001]

    // Notification plumbing — the manager impl itself is bound in appModule (:app owns it)
    single { androidContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    // Permissions
    single<PermissionManager> { PermissionManagerImpl(androidContext()) }
    single<OemBackgroundReliabilityManager> { OemBackgroundReliabilityManagerImpl(androidContext()) }

    // Platform capabilities — Android offers both reliability remedies: the manifest ACL
    // receiver (BT strategy) and ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS. [IOS-F0-03]
    single { DeviceCapabilities(supportsBtStrategy = true, supportsBatteryExemption = true) }

    single<com.rndeveloper.paparcar.domain.detection.ParkingAskBudget> {
        com.rndeveloper.paparcar.detection.AndroidParkingAskBudget(androidContext())
    }

    // Preferences
    single<AppPreferences> { AndroidDataStoreAppPreferences(androidContext()) }

    // Bluetooth
    single<BluetoothScanner> { AndroidBluetoothScanner(androidContext()) }
    // [IOS-DET-AUDIO-ROUTE-PROVES-WHOSE-CAR-001] Android links cars through the ACL lane, never the
    // audio route, so the sensor is empty here by construction — see
    // [AndroidCarAudioRouteSource]. ⛔ This used to bind `FakeCarAudioRouteSource`: a production
    // graph reaching into the mock graph, which stopped compiling the moment the fakes moved to
    // `:mockkit` and production could no longer see them.
    // [IOS-STORE-SHOTS-WITHOUT-SHIPPING-THE-DEMO-CODE-001]
    single<com.rndeveloper.paparcar.domain.detection.ports.CarAudioRouteSource> {
        AndroidCarAudioRouteSource()
    }

    // Connectivity
    single<ConnectivityObserver> { AndroidConnectivityObserver(androidContext()) }

    // Diagnostics — device identity stamped into detection traces [DIAG-READABLE-001]
    single<DeviceInfoProvider> { AndroidDeviceInfoProvider(androidContext(), get(), get()) }

    // "Report a problem" ships the local parkdiag history. [SUPPORT-REPORT-SHIPS-THE-LOCAL-LOG-001]
    single<LocalDiagnosticsLog> { AndroidLocalDiagnosticsLog(androidContext()) }

    // El carril de atribución publicitaria. Sólo existe en Android y sólo lo ata ESTE módulo: el
    // mock arranca con `MockPaparcarApp`, que no lo carga, así que el Dev Catalog no puede mandar
    // un evento a la cuenta de anuncios ni por descuido.
    // [MKT-META-APP-EVENTS-COST-THE-NO-ADS-PROMISE-001]
    single<AdAttributionTracker> { MetaAnalyticsTracker(androidContext(), get()) }

    // El Google Play Install Referrer sólo existe en Android. [MKT-INSTALL-ATTRIBUTION-CHANNEL-001]
    single<com.rndeveloper.paparcar.domain.attribution.InstallReferrerPort> {
        com.rndeveloper.paparcar.data.attribution.PlayInstallReferrerDataSource(androidContext())
    }

    // ── Push ─────────────────────────────────────────────────────────────────────────────────────
    // Las dos vigilancias y el reparto viven en `commonMain` desde
    // [IOS-THE-GROUP-CANNOT-REACH-AN-IPHONE-001]; lo que se registra aquí es lo que cambia por
    // plataforma. El comentario que este bloque llevaba —«Android only, because FCM is the Android
    // answer to waking an app that is not running»— era cierto el día que se escribió y dejó de
    // serlo el día que un iPhone entró en un grupo.
    single<PushTokenProvider> { FirebaseMessagingTokenProvider() }
    single<SharedCarFenceRefresher> { JanitorSharedCarFenceRefresher(androidContext()) }

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

    // ⛔ `single`, las dos: el servicio de mensajería inyecta la MISMA instancia para pedirles un
    // resync cuando el token rota, y dos de cada una tendrían media verdad cada una.
    // [ZONE-A-SPOT-TELLS-THE-SERVER-AND-THE-SERVER-TELLS-YOU-001]
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

    // El mismo token de este móvil, la otra vigilancia que lo necesita: el grupo del coche
    // compartido. [SHARED-A-PARKING-TELLS-THE-GROUP-001]
    single {
        SharedCarMemberSync(
            sharedCarRepository = get(),
            remote = get(),
            authRepository = get(),
            pushTokens = get(),
        )
    }
}
