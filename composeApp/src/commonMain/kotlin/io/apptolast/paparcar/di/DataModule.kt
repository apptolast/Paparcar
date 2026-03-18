package io.apptolast.paparcar.di

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import io.apptolast.paparcar.data.datasource.local.room.AppDatabase
import io.apptolast.paparcar.data.datasource.remote.FirebaseDataSource
import io.apptolast.paparcar.data.datasource.remote.FirebaseDataSourceImpl
import io.apptolast.paparcar.data.repository.SpotRepositoryImpl
import io.apptolast.paparcar.data.repository.UserParkingRepositoryImpl
import io.apptolast.paparcar.domain.repository.SpotRepository
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import org.koin.dsl.module

val dataModule = module {

    // Firebase
    single { Firebase.firestore }

    // Repositories
    single<SpotRepository> { SpotRepositoryImpl(get()) }
    single<UserParkingRepository> { UserParkingRepositoryImpl(get()) }

    // DataSources
    single<FirebaseDataSource> { FirebaseDataSourceImpl(get()) }

    // DAOs (from AppDatabase)
    single { get<AppDatabase>().parkingSessionDao() }

    // NOTA: LocationDataSource se provee en los módulos de plataforma.
}