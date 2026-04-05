package io.apptolast.paparcar.di

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import io.apptolast.paparcar.data.datasource.local.room.AppDatabase
import io.apptolast.paparcar.data.datasource.remote.FirebaseDataSource
import io.apptolast.paparcar.data.datasource.remote.FirebaseDataSourceImpl
import io.apptolast.paparcar.data.datasource.remote.UserProfileDataSource
import io.apptolast.paparcar.data.datasource.remote.UserProfileDataSourceImpl
import io.apptolast.paparcar.data.repository.SpotRepositoryImpl
import io.apptolast.paparcar.data.repository.UserParkingRepositoryImpl
import io.apptolast.paparcar.data.repository.UserProfileRepositoryImpl
import io.apptolast.paparcar.data.repository.VehicleRepositoryImpl
import io.apptolast.paparcar.domain.repository.SpotRepository
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import io.apptolast.paparcar.domain.repository.UserProfileRepository
import io.apptolast.paparcar.domain.repository.VehicleRepository
import org.koin.dsl.module

val dataModule = module {

    // Firebase
    single { Firebase.firestore }

    // DataSources
    single<FirebaseDataSource> { FirebaseDataSourceImpl(get()) }
    single<UserProfileDataSource> { UserProfileDataSourceImpl(get()) }

    // Repositories
    single<SpotRepository> { SpotRepositoryImpl(get()) }
    single<UserParkingRepository> { UserParkingRepositoryImpl(get(), get(), get()) }
    single<UserProfileRepository> { UserProfileRepositoryImpl(get(), get()) }
    single<VehicleRepository> { VehicleRepositoryImpl(get(), get(), get()) }

    // DAOs (from AppDatabase)
    single { get<AppDatabase>().parkingSessionDao() }
    single { get<AppDatabase>().userProfileDao() }
    single { get<AppDatabase>().vehicleDao() }

    // NOTA: LocationDataSource se provee en los módulos de plataforma.
}
