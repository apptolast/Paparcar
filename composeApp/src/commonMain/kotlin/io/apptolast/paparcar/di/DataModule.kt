package io.apptolast.paparcar.di

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import io.apptolast.paparcar.data.datasource.local.room.AppDatabase
import io.apptolast.paparcar.data.datasource.local.room.SpotDao
import io.apptolast.paparcar.data.geocoder.RoomLocalAddressAndPlaceDataSource
import io.apptolast.paparcar.domain.geocoder.LocalAddressAndPlaceDataSource
import io.apptolast.paparcar.data.datasource.remote.FirebaseDataSource
import io.apptolast.paparcar.data.datasource.remote.FirebaseDataSourceImpl
import io.apptolast.paparcar.data.datasource.remote.FirestoreDetectionEventLogger
import io.apptolast.paparcar.data.datasource.remote.FirestoreUiLocationLogger
import io.apptolast.paparcar.data.datasource.remote.RemoteUserProfileDataSource
import io.apptolast.paparcar.data.datasource.remote.RemoteUserProfileDataSourceImpl
import io.apptolast.paparcar.data.repository.AddressAndPlaceRepositoryImpl
import io.apptolast.paparcar.data.repository.SpotRepositoryImpl
import io.apptolast.paparcar.data.repository.UserParkingRepositoryImpl
import io.apptolast.paparcar.data.repository.UserProfileRepositoryImpl
import io.apptolast.paparcar.data.repository.VehicleRepositoryImpl
import io.apptolast.paparcar.data.repository.ZoneRepositoryImpl
import io.apptolast.paparcar.data.session.RoomLocalSessionCache
import io.apptolast.paparcar.domain.repository.AddressAndPlaceRepository
import io.apptolast.paparcar.domain.repository.SpotRepository
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import io.apptolast.paparcar.domain.repository.UserProfileRepository
import io.apptolast.paparcar.domain.repository.VehicleRepository
import io.apptolast.paparcar.domain.repository.ZoneRepository
import io.apptolast.paparcar.domain.diagnostics.DetectionEventLogger
import io.apptolast.paparcar.domain.diagnostics.UiLocationLogger
import io.apptolast.paparcar.domain.session.LocalSessionCache
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
