package io.apptolast.paparcar.di

import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.LocationServices
import io.apptolast.paparcar.detection.ActivityRecognitionManagerImpl
import io.apptolast.paparcar.detection.DepartureEventBusImpl
import io.apptolast.paparcar.detection.GeofenceEventBusImpl
import io.apptolast.paparcar.detection.GeofenceManagerImpl
import io.apptolast.paparcar.detection.WorkManagerParkingEnrichmentScheduler
import io.apptolast.paparcar.detection.WorkManagerReportSpotScheduler
import io.apptolast.paparcar.domain.ActivityRecognitionManager
import io.apptolast.paparcar.domain.service.DepartureEventBus
import io.apptolast.paparcar.domain.service.GeofenceEventBus
import io.apptolast.paparcar.domain.service.GeofenceManager
import io.apptolast.paparcar.domain.service.ParkingEnrichmentScheduler
import io.apptolast.paparcar.domain.service.ReportSpotScheduler
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val androidDetectionModule = module {

    // --- Activity Recognition ---
    single<ActivityRecognitionManager> { ActivityRecognitionManagerImpl(androidContext()) }
    single { ActivityRecognition.getClient(androidContext()) }

    // --- Geofence ---
    single { LocationServices.getGeofencingClient(androidContext()) }
    single<GeofenceEventBus> { GeofenceEventBusImpl() }
    single<GeofenceManager> { GeofenceManagerImpl(androidContext(), get(), get()) }

    // --- Departure Detection ---
    single<DepartureEventBus> { DepartureEventBusImpl() }

    // --- Parking Enrichment ---
    single<ParkingEnrichmentScheduler> { WorkManagerParkingEnrichmentScheduler(androidContext()) }

    // --- Spot Report ---
    single<ReportSpotScheduler> { WorkManagerReportSpotScheduler(androidContext()) }

}
