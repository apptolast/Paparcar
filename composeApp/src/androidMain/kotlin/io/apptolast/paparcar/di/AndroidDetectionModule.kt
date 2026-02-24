package io.apptolast.paparcar.di

import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.LocationServices
import io.apptolast.paparcar.detection.ActivityRecognitionManagerImpl
import io.apptolast.paparcar.detection.GeofenceEventBusImpl
import io.apptolast.paparcar.detection.GeofenceManagerImpl
import io.apptolast.paparcar.domain.ActivityRecognitionManager
import io.apptolast.paparcar.domain.service.GeofenceEventBus
import io.apptolast.paparcar.domain.service.GeofenceService
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val androidDetectionModule = module {

    // --- Activity Recognition ---
    single<ActivityRecognitionManager> { ActivityRecognitionManagerImpl(androidContext()) }
    single { ActivityRecognition.getClient(androidContext()) }

    // --- Geofence ---
    single { LocationServices.getGeofencingClient(androidContext()) }
    single<GeofenceEventBus> { GeofenceEventBusImpl() }
    single<GeofenceService> { GeofenceManagerImpl(androidContext(), get(), get()) }

}
