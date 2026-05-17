package io.apptolast.paparcar.di

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import io.apptolast.paparcar.data.datasource.local.room.AppDatabase
import io.apptolast.paparcar.data.datasource.local.room.SpotDao
import io.apptolast.paparcar.data.datasource.remote.FirebaseDataSource
import io.apptolast.paparcar.data.datasource.remote.FirebaseDataSourceImpl
import io.apptolast.paparcar.data.datasource.remote.RemoteUserProfileDataSource
import io.apptolast.paparcar.data.datasource.remote.RemoteUserProfileDataSourceImpl
import io.apptolast.paparcar.data.repository.SpotRepositoryImpl
import io.apptolast.paparcar.data.repository.UserParkingRepositoryImpl
import io.apptolast.paparcar.data.repository.UserProfileRepositoryImpl
import io.apptolast.paparcar.data.repository.VehicleRepositoryImpl
import io.apptolast.paparcar.data.repository.ZoneRepositoryImpl
import io.apptolast.paparcar.data.session.RoomLocalSessionCache
import io.apptolast.paparcar.domain.repository.SpotRepository
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import io.apptolast.paparcar.domain.repository.UserProfileRepository
import io.apptolast.paparcar.domain.repository.VehicleRepository
import io.apptolast.paparcar.domain.repository.ZoneRepository
import io.apptolast.paparcar.domain.session.LocalSessionCache
import org.koin.dsl.module

val dataModule = module {

    // Firebase
    single { Firebase.firestore }

    // DataSources
    single<FirebaseDataSource> { FirebaseDataSourceImpl(get()) }
    single<RemoteUserProfileDataSource> { RemoteUserProfileDataSourceImpl(get()) }

    // Repositories
    single<SpotRepository> { SpotRepositoryImpl(get(), get()) }
    single<UserParkingRepository> { UserParkingRepositoryImpl(get(), get(), get(), get()) }
    single<UserProfileRepository> { UserProfileRepositoryImpl(get(), get()) }
    single<VehicleRepository> { VehicleRepositoryImpl(get(), get(), get(), get()) }
    single<ZoneRepository> { ZoneRepositoryImpl(get(), get(), get()) }

    // Session
    single<LocalSessionCache> { RoomLocalSessionCache(get()) }

    // DAOs (from AppDatabase)
    single { get<AppDatabase>().parkingSessionDao() }
    single { get<AppDatabase>().userProfileDao() }
    single { get<AppDatabase>().vehicleDao() }
    single<SpotDao> { get<AppDatabase>().spotDao() }
    single { get<AppDatabase>().zoneDao() }

    // NOTA: LocationDataSource se provee en los módulos de plataforma.
}
