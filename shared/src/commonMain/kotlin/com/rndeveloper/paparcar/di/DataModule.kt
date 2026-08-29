package com.rndeveloper.paparcar.di

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import com.rndeveloper.paparcar.data.datasource.local.room.AppDatabase
import com.rndeveloper.paparcar.data.datasource.local.room.SpotDao
import com.rndeveloper.paparcar.data.geocoder.RoomLocalAddressAndPlaceDataSource
import com.rndeveloper.paparcar.domain.geocoder.LocalAddressAndPlaceDataSource
import com.rndeveloper.paparcar.data.datasource.remote.FirebaseDataSource
import com.rndeveloper.paparcar.data.datasource.remote.FirebaseDataSourceImpl
import com.rndeveloper.paparcar.data.datasource.remote.FirestoreDetectionEventLogger
import com.rndeveloper.paparcar.data.datasource.remote.FirestoreDiagnosticsReportUploader
import com.rndeveloper.paparcar.data.datasource.remote.FirestoreUiLocationLogger
import com.rndeveloper.paparcar.data.datasource.remote.RemoteUserProfileDataSource
import com.rndeveloper.paparcar.data.datasource.remote.RemoteUserProfileDataSourceImpl
import com.rndeveloper.paparcar.data.repository.AddressAndPlaceRepositoryImpl
import com.rndeveloper.paparcar.data.repository.DiagnosticsRepositoryImpl
import com.rndeveloper.paparcar.data.repository.SpotRepositoryImpl
import com.rndeveloper.paparcar.data.repository.UserParkingRepositoryImpl
import com.rndeveloper.paparcar.data.repository.UserProfileRepositoryImpl
import com.rndeveloper.paparcar.data.repository.VehicleRepositoryImpl
import com.rndeveloper.paparcar.data.repository.ZoneRepositoryImpl
import com.rndeveloper.paparcar.data.session.RoomLocalSessionCache
import com.rndeveloper.paparcar.domain.repository.AddressAndPlaceRepository
import com.rndeveloper.paparcar.domain.repository.DiagnosticsRepository
import com.rndeveloper.paparcar.domain.repository.SpotRepository
import com.rndeveloper.paparcar.domain.repository.UserParkingRepository
import com.rndeveloper.paparcar.domain.repository.UserProfileRepository
import com.rndeveloper.paparcar.domain.repository.VehicleRepository
import com.rndeveloper.paparcar.domain.repository.ZoneRepository
import com.rndeveloper.paparcar.domain.diagnostics.DetectionEventLogger
import com.rndeveloper.paparcar.domain.diagnostics.DiagnosticsReportUploader
import com.rndeveloper.paparcar.domain.diagnostics.UiLocationLogger
import com.rndeveloper.paparcar.domain.session.LocalSessionCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module

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
        )
    }

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
        VehicleRepositoryImpl(get(), get(), get(), get(), CoroutineScope(SupervisorJob() + Dispatchers.Default))
    }
    single<ZoneRepository> {
        ZoneRepositoryImpl(get(), get(), get(), CoroutineScope(SupervisorJob() + Dispatchers.Default))
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

    // NOTA: LocationDataSource se provee en los módulos de plataforma.
}
