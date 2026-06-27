package io.apptolast.paparcar.di

import io.apptolast.paparcar.domain.ActivityRecognitionManager
import io.apptolast.paparcar.domain.sensor.StepDetectorSource
import io.apptolast.paparcar.domain.service.DepartureEventBus
import io.apptolast.paparcar.domain.service.GeofenceEventBus
import io.apptolast.paparcar.domain.service.GeofenceManager
import io.apptolast.paparcar.domain.service.ParkingEnrichmentScheduler
import io.apptolast.paparcar.domain.service.ParkingSyncScheduler
import io.apptolast.paparcar.detection.IosActivityRecognitionManagerImpl
import io.apptolast.paparcar.detection.IosDepartureEventBusImpl
import io.apptolast.paparcar.detection.IosGeofenceEventBusImpl
import io.apptolast.paparcar.detection.IosGeofenceManagerImpl
import io.apptolast.paparcar.detection.IosParkingEnrichmentScheduler
import io.apptolast.paparcar.detection.IosParkingSyncScheduler
import io.apptolast.paparcar.detection.IosReportSpotScheduler
import io.apptolast.paparcar.detection.IosStepDetectorSource
import io.apptolast.paparcar.domain.service.ReportSpotScheduler
import org.koin.dsl.module

val iosDetectionModule = module {
    single<ActivityRecognitionManager> { IosActivityRecognitionManagerImpl(get(), get()) }
    single<StepDetectorSource> { IosStepDetectorSource() }
    single<GeofenceEventBus> { IosGeofenceEventBusImpl() }
    single<GeofenceManager> { IosGeofenceManagerImpl(get()) }
    single<DepartureEventBus> { IosDepartureEventBusImpl() }
    single<ParkingEnrichmentScheduler> { IosParkingEnrichmentScheduler(get(), get()) }
    single<ParkingSyncScheduler> { IosParkingSyncScheduler(get(), get()) }
    single<ReportSpotScheduler> { IosReportSpotScheduler(get()) }
}
