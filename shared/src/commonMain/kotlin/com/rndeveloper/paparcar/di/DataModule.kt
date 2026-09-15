package com.rndeveloper.paparcar.di

import com.rndeveloper.paparcar.domain.repository.ZoneAlertSubscriptionRepository
import com.rndeveloper.paparcar.data.repository.FirestoreZoneAlertSubscriptionRepository
import com.rndeveloper.paparcar.domain.repository.ZoneWatchRepository
import com.rndeveloper.paparcar.data.repository.RoomZoneWatchRepository
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.app
import dev.gitlive.firebase.firestore.firestore
import com.rndeveloper.paparcar.data.datasource.remote.FirestoreDatabase
import com.rndeveloper.paparcar.germanyFuelFeedKey
import com.rndeveloper.paparcar.isDebugBuild
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
import com.rndeveloper.paparcar.data.datasource.remote.RemoteSharedCarDataSource
import com.rndeveloper.paparcar.data.datasource.remote.RemoteSharedCarDataSourceImpl
import com.rndeveloper.paparcar.data.datasource.remote.RemoteUserProfileDataSource
import com.rndeveloper.paparcar.data.datasource.remote.RemoteUserProfileDataSourceImpl
import com.rndeveloper.paparcar.data.repository.SharedCarRepositoryImpl
import com.rndeveloper.paparcar.data.sharedcar.SharedCarMirrorWriter
import com.rndeveloper.paparcar.domain.repository.SharedCarRepository
import com.rndeveloper.paparcar.domain.sharedcar.SharedCarMirror
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
import com.rndeveloper.paparcar.data.fuel.DiskBackedFuelStationsSource
import com.rndeveloper.paparcar.data.fuel.RoomFuelStationsCache
import com.rndeveloper.paparcar.data.fuel.RoomFuelWatchRepository
import com.rndeveloper.paparcar.domain.repository.FuelWatchRepository
import com.rndeveloper.paparcar.data.fuel.AverageFuelPricesRepositoryImpl
import com.rndeveloper.paparcar.data.fuel.FranceFuelStationsSource
import com.rndeveloper.paparcar.data.fuel.GermanyFuelStationsSource
import com.rndeveloper.paparcar.data.fuel.FuelStationsRegistry
import com.rndeveloper.paparcar.data.fuel.fuelHttpClient
import com.rndeveloper.paparcar.data.fuel.MineturStationsSource
import com.rndeveloper.paparcar.data.fuel.OpenVanAverageFuelPricesSource
import com.rndeveloper.paparcar.data.fuel.OsmFuelStationsSource
import com.rndeveloper.paparcar.data.parkings.CachingPublicParkingsSource
import com.rndeveloper.paparcar.data.parkings.DiskBackedPublicParkingsSource
import com.rndeveloper.paparcar.data.parkings.OsmPublicParkingsSource
import com.rndeveloper.paparcar.data.parkings.RoomPublicParkingsCache
import com.rndeveloper.paparcar.data.places.OverpassPlacesDataSource
import com.rndeveloper.paparcar.data.places.OverpassRoadNetworkDataSource
import com.rndeveloper.paparcar.domain.fuel.FuelStationsSource
import com.rndeveloper.paparcar.domain.parkings.PublicParkingsSource
import com.rndeveloper.paparcar.domain.places.PlacesDataSource
import com.rndeveloper.paparcar.domain.places.RoadNetworkDataSource

val dataModule = module {

    // Firebase
    // Debug builds → `debug` database, release → `(default)`. The ONLY place a FirebaseFirestore
    // is built: every remote source receives this instance. [FIRESTORE-DEBUG-BUILDS-USE-DEBUG-DATABASE-001]
    single { Firebase.firestore(Firebase.app, FirestoreDatabase.idFor(isDebugBuild)) }

    // DataSources
    single<FirebaseDataSource> { FirebaseDataSourceImpl(get()) }
    single<RemoteUserProfileDataSource> {
        // appPreferences = the local-first consent, read at the single push door.
        // [PRIV-A-TRACE-IS-NOT-A-STAT-001]
        // sharedCarMirror = the group's copy of a parking, written at the same door.
        // [VEH-SHARED-001]
        RemoteUserProfileDataSourceImpl(
            get(),
            appPreferences = getOrNull(),
            sharedCarMirror = getOrNull(),
        )
    }
    single<RemoteSharedCarDataSource> { RemoteSharedCarDataSourceImpl(get()) }

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

    // Coche compartido: el espejo del grupo. [VEH-SHARED-001]
    single<SharedCarMirror> {
        SharedCarMirrorWriter(
            remote = get(),
            vehicleDao = get(),
            parkingDao = get(),
            profileDao = get(),
        )
    }
    single<SharedCarRepository> {
        SharedCarRepositoryImpl(
            remote = get(),
            dao = get(),
            profileDao = get(),
            vehicleRepository = get(),
            authRepository = get(),
        )
    }

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
    single { get<AppDatabase>().zoneWatchDao() }

    // La memoria que la alerta de zona nunca tuvo: lo ya avisado y el primer vistazo de cada zona.
    // [ZONE-AN-ALERT-NOBODY-IS-AWAKE-TO-RECEIVE-001]
    single<ZoneWatchRepository> { RoomZoneWatchRepository(dao = get()) }

    // El índice que la Cloud Function consulta. ⛔ Solo se ESCRIBE desde el cliente; las reglas
    // permiten leer únicamente lo tuyo, y esa lectura acotada es lo que hace posible BORRAR las
    // suscripciones que sobran. [ZONE-A-SPOT-TELLS-THE-SERVER-AND-THE-SERVER-TELLS-YOU-001]
    single<ZoneAlertSubscriptionRepository> { FirestoreZoneAlertSubscriptionRepository(firestore = get()) }
    single { get<AppDatabase>().geocoderCacheDao() }
    single { get<AppDatabase>().publicParkingCacheDao() }
    single { get<AppDatabase>().osmCoverageDao() }
    single { get<AppDatabase>().fuelStationCacheDao() }
    single { get<AppDatabase>().fuelWatchDao() }
    single { get<AppDatabase>().tripRoutePointDao() }
    single { get<AppDatabase>().sharedCarDao() }

    // Lo que el usuario sigue y lo que esas bombas han cobrado. ⛔ Tabla aparte de la caché a
    // propósito: la caché se barre, y un seguimiento borrado por una limpieza es la elección del
    // usuario tirada por una tarea de mantenimiento. [FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001]
    single<FuelWatchRepository> { RoomFuelWatchRepository(dao = get()) }

    // Driving route of the live trip — ONE Room-backed impl for both platforms, replacing the
    // SharedPreferences/NSUserDefaults twins. [TRIP-A-LONG-DRIVE-KEEPS-ITS-ORIGIN-001]
    single<com.rndeveloper.paparcar.domain.detection.ports.DrivingRouteStore> {
        com.rndeveloper.paparcar.data.detection.RoomDrivingRouteStore(dao = get())
    }

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
        // Tres capas, cada una con SU pregunta [CACHE-A-MAP-MUST-NOT-EMPTY-ON-COLD-START-001]:
        //   Caching  → «¿vuelvo a preguntar?»                 (memoria, 15 min)
        //   Disk     → «¿qué enseño si NO puedo preguntar?»    (Room, 30 días de GEOGRAFÍA)
        //   Registry → la red, un país una fuente
        // ⛔ El disco envuelve al REGISTRO, no a una fuente: así hereda a quien haya contestado, y
        // esa fuente se nombra en el resultado — que es lo que impide que el id de una estación
        // española pise el de una de OSM en una tabla compartida.
        CachingFuelStationsSource(
            DiskBackedFuelStationsSource(
                delegate = FuelStationsRegistry(
                    // Un país = una entrada. El orden no decide nada: cada fuente declara a quién
                    // cubre y sólo una responde por país. [FUEL-FRANCE-A-PRICE-CARRIES-ITS-OWN-HOUR-001]
                    national = listOf(
                        MineturStationsSource(http),
                        FranceFuelStationsSource(http),
                        // La clave como PROVEEDOR y no como valor: se lee al preguntar, así que un
                        // build sin ella (repo público) no reclama Alemania y cae al suelo de OSM
                        // sin fingir un feed roto. [FUEL-GERMANY-THE-FEED-ANSWERS-WHOLE-001]
                        GermanyFuelStationsSource(http) { germanyFuelFeedKey },
                    ),
                    fallback = OsmFuelStationsSource(http),
                ),
                disk = RoomFuelStationsCache(dao = get(), coverageDao = get()),
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

    // Parkings públicos — OSM y solo OSM: aquí no hay registro por país porque la segunda fuente
    // (la ocupación de una ciudad) ENRIQUECERÁ esta base en vez de sustituirla, y esa junta la
    // decide F6b, no este módulo. [PARKINGS-ON-THE-MAP-001]
    //
    // ⛔ El MISMO `fuelHttpClient()` que el carril de gasolineras, y no es reutilización por pereza:
    // los dos hablan con Overpass, que responde **406 al User-Agent por defecto de OkHttp**, y los
    // dos necesitan el deadline que impide que un `isLoading` se quede clavado. Un cliente propio
    // aquí habría repetido el mismo fallo desde cero. [FUEL-LAYER-FOLLOWS-THE-CAMERA-001]
    //
    // Tres capas, y cada una contesta a UNA pregunta distinta — reutilizar el TTL de una para otra
    // es el error que esta pila evita [CACHE-A-MAP-MUST-NOT-EMPTY-ON-COLD-START-001]:
    //   CachingPublicParkingsSource  → «¿vuelvo a preguntar?»            (memoria, 30 min)
    //   DiskBackedPublicParkingsSource → «¿qué enseño si NO puedo preguntar?» (Room, 30 días)
    //   OsmPublicParkingsSource      → la red
    // El disco va DEBAJO de la red y solo se lee cuando esta falla: la respuesta de hoy gana siempre.
    single<PublicParkingsSource> {
        CachingPublicParkingsSource(
            DiskBackedPublicParkingsSource(
                delegate = OsmPublicParkingsSource(fuelHttpClient()),
                disk = RoomPublicParkingsCache(parkingDao = get(), coverageDao = get()),
            )
        )
    }

    // ── Las otras dos preguntas a Overpass [OVERPASS-A-SINGLE-MIRROR-HAS-NO-FAILOVER-001] ────
    // Estaban en los módulos de plataforma porque cada una traía su propio POST a mano
    // (HttpURLConnection en Android, NSURLSession en iOS) — y por eso las dos se quedaron fuera del
    // failover de espejos, usando `OverpassEndpoints.ALL.first()` mientras lo compartían.
    //
    // ⛔ El MISMO `fuelHttpClient()` que gasolineras y parkings, y aquí no es reutilización por
    // pereza: es lo que hace SEGURA la migración. Medido el 10-09-2026 contra el endpoint real, el
    // User-Agent por defecto de OkHttp devuelve **406**, así que un `HttpClient()` a pelo convertiría
    // dos vías que hoy funcionan (`HttpURLConnection` manda el agente de Dalvik, que pasa) en dos
    // vías rotas al 100 %. [FUEL-LAYER-FOLLOWS-THE-CAMERA-001]
    single<PlacesDataSource> { OverpassPlacesDataSource(fuelHttpClient()) }
    // Con la implementación en commonMain, iOS deja de ser la única plataforma sin map-matching: no
    // lo tenía porque el fichero vivía en androidMain, no porque se hubiera decidido que no.
    single<RoadNetworkDataSource> { OverpassRoadNetworkDataSource(fuelHttpClient()) }

    // NOTA: LocationDataSource se provee en los módulos de plataforma.
}
