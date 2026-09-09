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
import org.koin.dsl.module
import com.rndeveloper.paparcar.data.fuel.CachingFuelStationsSource
import com.rndeveloper.paparcar.data.fuel.FuelStationsRegistry
import com.rndeveloper.paparcar.data.fuel.MineturStationsSource
import com.rndeveloper.paparcar.data.fuel.OsmFuelStationsSource
import com.rndeveloper.paparcar.domain.fuel.FuelStationsSource
import io.ktor.client.HttpClient

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
        val http = HttpClient()
        CachingFuelStationsSource(
            FuelStationsRegistry(
                national = listOf(MineturStationsSource(http)),
                fallback = OsmFuelStationsSource(http),
            ),
        )
    }

    // NOTA: LocationDataSource se provee en los módulos de plataforma.
}
