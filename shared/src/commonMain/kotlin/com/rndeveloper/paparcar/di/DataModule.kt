package com.rndeveloper.paparcar.di

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import com.rndeveloper.paparcar.data.datasource.local.room.AppDatabase
import com.rndeveloper.paparcar.data.datasource.local.room.SpotDao
import com.rndeveloper.paparcar.data.geocoder.RoomLocalAddressAndPlaceDataSource
import com.rndeveloper.paparcar.domain.geocoder.LocalAddressAndPlaceDataSource
import com.rndeveloper.paparcar.data.datasource.remote.FirebaseDataSource
import com.rndeveloper.paparcar.data.datasource.remote.FirebaseDataSourceImpl
import com.rndeveloper.paparcar.data.analytics.FirebaseAnalyticsTracker
import com.rndeveloper.paparcar.data.datasource.remote.FirestoreDetectionEventLogger
import com.rndeveloper.paparcar.data.datasource.remote.FirestoreDiagnosticsReportUploader
import com.rndeveloper.paparcar.data.datasource.remote.FirestoreUiLocationLogger
import com.rndeveloper.paparcar.data.datasource.remote.RemoteUserProfileDataSource
import com.rndeveloper.paparcar.data.datasource.remote.RemoteUserProfileDataSourceImpl
import com.rndeveloper.paparcar.data.repository.AddressAndPlaceRepositoryImpl
import com.rndeveloper.paparcar.data.repository.DiagnosticsRepositoryImpl
import com.rndeveloper.paparcar.data.repository.OpenSourceLicenseRepositoryImpl
import com.rndeveloper.paparcar.data.datasource.remote.RevenueCatDataSource
import com.rndeveloper.paparcar.data.datasource.remote.RevenueCatDataSourceImpl
import com.rndeveloper.paparcar.data.repository.RevenueCatEntitlementRepositoryImpl
import com.rndeveloper.paparcar.data.repository.SpotRepositoryImpl
import com.rndeveloper.paparcar.data.repository.UserParkingRepositoryImpl
import com.rndeveloper.paparcar.data.repository.UserProfileRepositoryImpl
import com.rndeveloper.paparcar.data.repository.VehicleRepositoryImpl
import com.rndeveloper.paparcar.data.repository.ZoneRepositoryImpl
import com.rndeveloper.paparcar.data.session.RoomLocalSessionCache
import com.rndeveloper.paparcar.domain.repository.AddressAndPlaceRepository
import com.rndeveloper.paparcar.domain.repository.AverageFuelPricesRepository
import com.rndeveloper.paparcar.domain.repository.DiagnosticsRepository
import com.rndeveloper.paparcar.domain.repository.EntitlementRepository
import com.rndeveloper.paparcar.domain.repository.OpenSourceLicenseRepository
import com.rndeveloper.paparcar.domain.repository.SpotRepository
import com.rndeveloper.paparcar.domain.repository.UserParkingRepository
import com.rndeveloper.paparcar.domain.repository.UserProfileRepository
import com.rndeveloper.paparcar.domain.repository.VehicleRepository
import com.rndeveloper.paparcar.domain.repository.ZoneRepository
import com.rndeveloper.paparcar.domain.analytics.AnalyticsTracker
import com.rndeveloper.paparcar.domain.diagnostics.DetectionEventLogger
import com.rndeveloper.paparcar.domain.diagnostics.DiagnosticsReportUploader
import com.rndeveloper.paparcar.domain.diagnostics.UiLocationLogger
import com.rndeveloper.paparcar.domain.session.LocalSessionCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.dsl.module
import com.rndeveloper.paparcar.data.fuel.CachingFuelStationsSource
import com.rndeveloper.paparcar.data.fuel.AverageFuelPricesRepositoryImpl
import com.rndeveloper.paparcar.data.fuel.FranceFuelStationsSource
import com.rndeveloper.paparcar.data.fuel.FuelStationsRegistry
import com.rndeveloper.paparcar.data.fuel.fuelHttpClient
import com.rndeveloper.paparcar.data.fuel.MineturStationsSource
import com.rndeveloper.paparcar.data.fuel.OpenVanAverageFuelPricesSource
import com.rndeveloper.paparcar.data.fuel.OsmFuelStationsSource
import com.rndeveloper.paparcar.domain.fuel.FuelStationsSource

val dataModule = module {

    // Firebase
    single { Firebase.firestore }

    // DataSources
    single<FirebaseDataSource> { FirebaseDataSourceImpl(get()) }
    single<RemoteUserProfileDataSource> { RemoteUserProfileDataSourceImpl(get()) }

    // Detection diagnostics — Firestore-backed remote event log, gated by a Firestore flag
    // (diagnostics_config/{userId}.enabled). [DET-LOG-02]
    // Dispatchers.Default (not IO) keeps this constructible from commonMain; GitLive suspend calls
    // are callback-based and don't block the dispatcher thread.
    single<DetectionEventLogger> {
        FirestoreDetectionEventLogger(
            firestore = get(),
            authRepository = get(),
            deviceInfo = get(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            // [DET-THE-EVIDENCE-MUST-REACH-THE-TRACE-001] So the rollup's "credible driving fix"
            // uses the detector's own bars instead of a private copy that drifts.
            detectionConfig = get(),
        )
    }

    // Product analytics — anonymous population-wide counters, cell-only location. A different
    // system from the opt-in diagnostics above, on purpose. [ANALYTICS-EVENTS-001]
    single<AnalyticsTracker> { FirebaseAnalyticsTracker() }

    // Consumer map-location diagnostics — local logcat always + gated Firestore mirror at
    // diagnostics/{userId}/uiLocation, same opt-in flag as detection. Verifies UI-LOC-FOREGROUND-001.
    single<UiLocationLogger> {
        FirestoreUiLocationLogger(
            firestore = get(),
            authRepository = get(),
            deviceInfo = get(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    }

    // Repositories
    single<SpotRepository> { SpotRepositoryImpl(get(), get()) }
    single<UserParkingRepository> { UserParkingRepositoryImpl(get(), get(), get()) }
    single<UserProfileRepository> { UserProfileRepositoryImpl(get(), get()) }
    single<VehicleRepository> {
        VehicleRepositoryImpl(get(), get(), get(), get(), get(), CoroutineScope(SupervisorJob() + Dispatchers.Default), get())
    }
    // Atribución OSS — `single` a propósito: el fichero se genera en build y no cambia en runtime,
    // así que la lista y el detalle comparten una sola lectura. [SET-LICENSES-ARE-SHOWN-IN-THE-APP-001]
    single<OpenSourceLicenseRepository> { OpenSourceLicenseRepositoryImpl() }

    single<ZoneRepository> {
        ZoneRepositoryImpl(get(), get(), get(), CoroutineScope(SupervisorJob() + Dispatchers.Default))
    }
    // Suscripción Paparcar Plus — RevenueCat es la ÚNICA fuente de verdad del entitlement;
    // nada se replica en Firestore ni en Room. Depende de Purchases.configure() en la
    // Application, ANTES de que Koin construya este grafo. [MONEY-001]
    // El SDK vive detrás de un datasource inyectado — mismo patrón que FirebaseDataSource
    // para Firestore — así el repositorio no nombra a RevenueCat directamente.
    single<RevenueCatDataSource> { RevenueCatDataSourceImpl() }
    single<EntitlementRepository> {
        RevenueCatEntitlementRepositoryImpl(
            dataSource = get(),
            refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    }
    single<AddressAndPlaceRepository> { AddressAndPlaceRepositoryImpl(get(), get(), get()) }
    // Erasure port of the diagnostics telemetry — account deletion sweeps diagnostics/{uid} +
    // diagnostics_config/{uid} with the rest of the user's data. [ACCOUNT-DELETE-SWEEPS-DIAGNOSTICS-001]
    single<DiagnosticsRepository> { DiagnosticsRepositoryImpl(get()) }

    // "Report a problem" upload — chunked into Firestore (no Storage bucket on pap-26).
    // [SUPPORT-REPORT-SHIPS-THE-LOCAL-LOG-001]
    single<DiagnosticsReportUploader> { FirestoreDiagnosticsReportUploader(get()) }

    // Session
    single<LocalSessionCache> { RoomLocalSessionCache(get()) }

    // DAOs (from AppDatabase)
    single { get<AppDatabase>().parkingSessionDao() }
    single { get<AppDatabase>().userProfileDao() }
    single { get<AppDatabase>().vehicleDao() }
    single<SpotDao> { get<AppDatabase>().spotDao() }
    single { get<AppDatabase>().zoneDao() }
    single { get<AppDatabase>().geocoderCacheDao() }

    // Local datasources
    single<LocalAddressAndPlaceDataSource> { RoomLocalAddressAndPlaceDataSource(get()) }

    // ── Gasolineras cercanas [FUEL-STATIONS-LIST-001] ─────────────────────────
    // Un solo HttpClient en commonMain: Ktor ya viene con el fetcher de red de Coil y los dos
    // motores están declarados por plataforma, así que cada país es UN fichero en vez de una clase
    // de transporte por plataforma (con ocho países, dieciséis).
    // ⛔ El cliente NO se registra como definición propia: `HttpClient()` es una FÁBRICA que resuelve
    // su motor del classpath, pero el verificador de Koin mira el CONSTRUCTOR y exige entonces un
    // `HttpClientEngine` que nadie provee — lo cazó `KoinModuleVerifyTest`, no el runtime. Creado
    // aquí dentro lo comparten igualmente todos los adaptadores de país, que es lo que se quería.
    //
    // Una fuente por país; OSM es el suelo del resto del mundo, y el registro decide quién contesta
    // y cuándo se cae al suelo. La caché envuelve al registro, no al revés: así vale para todos los
    // países y nunca guarda un fallo.
    single<FuelStationsSource> {
        // ⛔ NO `HttpClient()` a pelo: Overpass responde 406 al User-Agent por defecto de OkHttp
        // (`okhttp/x.y.z` cae en su filtro de bots) — medido bisecando cabeceras el 09-09 contra el
        // endpoint real, y era el fallo de red inexplicado de FUEL-STATIONS-LIST-001. España nunca
        // lo notó porque el Ministerio contesta primero y no filtra.
        // [FUEL-LAYER-FOLLOWS-THE-CAMERA-001]
        val http = fuelHttpClient()
        CachingFuelStationsSource(
            FuelStationsRegistry(
                // Un país = una entrada. El orden no decide nada: cada fuente declara a quién
                // cubre y sólo una responde por país. [FUEL-FRANCE-A-PRICE-CARRIES-ITS-OWN-HOUR-001]
                national = listOf(
                    MineturStationsSource(http),
                    FranceFuelStationsSource(http),
                ),
                fallback = OsmFuelStationsSource(http),
            ),
        )
    }

    // ── Media nacional de precios [FUEL-NATIONAL-AVERAGE-FROM-OPENVAN-001] ────
    // El escalón 2 del precio: lo que vale un litro de media en el país donde aparcó el coche.
    // Alimenta el GASTO ESTIMADO de los viajes, no las filas de gasolineras — son dos escalones
    // distintos de la misma escalera y no se tocan entre sí.
    //
    // ⛔ `single`, no `factory`: la tabla refrescada vive AQUÍ. Con `factory` cada pantalla tendría
    // su propio caché, refrescaría por su cuenta y dos pantallas podrían citar medias distintas
    // para el mismo viaje.
    //
    // ⛔ La red NUNCA está en el camino del cálculo: `current()` es síncrono y siempre contesta
    // (el suelo es el snapshot generado que va en el binario), y `refresh()` solo lo sustituye
    // más tarde. Es lo que mantiene cierto el invariante de F5a.
    //
    // El refresco se lanza AQUÍ y no en el constructor del repositorio, que se queda puro: un
    // `single` de Koin es perezoso, así que esto arranca justo cuando alguien pide precios por
    // primera vez, y el repositorio sigue siendo construible en un test sin que salga a la red.
    // ⚠️ La primera resolución de una pantalla puede usar todavía el snapshot del binario: el
    // refresco no bloquea a nadie, y la cifra fresca entra en la siguiente emisión. Es el precio
    // asumido de no tener la red en el camino del cálculo, y por eso el snapshot se regenera en
    // cada release en vez de envejecer.
    // ⛔ `fuelHttpClient()`, no `HttpClient()` a pelo — y aquí el motivo que manda no es el
    // User-Agent sino el DEADLINE. [FUEL-LAYER-FOLLOWS-THE-CAMERA-001] Un cliente sin timeout no
    // puede fallar nunca, y `refresh()` sostiene su Mutex mientras espera: una petición colgada
    // dejaría el candado tomado para siempre y ningún refresco posterior volvería a entrar.
    single<AverageFuelPricesRepository> {
        AverageFuelPricesRepositoryImpl(OpenVanAverageFuelPricesSource(fuelHttpClient())).also { repo ->
            CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { repo.refresh() }
        }
    }

    // NOTA: LocationDataSource se provee en los módulos de plataforma.
}
