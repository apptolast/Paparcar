package io.apptolast.paparcar.di

import io.apptolast.paparcar.data.datasource.local.LocalLocationDataSource
import io.apptolast.paparcar.data.datasource.local.LocalLocationDataSourceImpl
import io.apptolast.paparcar.data.datasource.local.room.AppDatabase
import io.apptolast.paparcar.data.datasource.remote.FirebaseDataSource
import io.apptolast.paparcar.data.datasource.remote.FirebaseDataSourceImpl
import io.apptolast.paparcar.data.repository.LocationRepositoryImpl
import io.apptolast.paparcar.data.repository.SpotRepositoryImpl
import io.apptolast.paparcar.domain.repository.LocationRepository
import io.apptolast.paparcar.domain.repository.SpotRepository
import org.koin.dsl.module

val dataModule = module {

    // Repositories
    single<LocationRepository> { LocationRepositoryImpl(get(), get()) }
    single<SpotRepository> { SpotRepositoryImpl(get()) }

    // DataSources
    single<LocalLocationDataSource> { LocalLocationDataSourceImpl(get()) }
    single<FirebaseDataSource> { FirebaseDataSourceImpl() }

    // DAOs (from AppDatabase)
    single { get<AppDatabase>().locationDao() }

    // NOTA: PlatformLocationDataSource se provee en los módulos de plataforma.
}
