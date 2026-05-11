package io.apptolast.paparcar.di

import io.apptolast.paparcar.domain.ActivityRecognitionManager
import io.apptolast.paparcar.domain.service.DepartureEventBus
import io.apptolast.paparcar.domain.service.GeofenceEventBus
import io.apptolast.paparcar.domain.service.GeofenceManager
import io.apptolast.paparcar.domain.service.ParkingEnrichmentScheduler
import io.apptolast.paparcar.domain.service.ParkingSyncScheduler
import io.apptolast.paparcar.detection.IosActivityRecognitionManagerImpl
import io.apptolast.paparcar.detection.IosGeofenceEventBusImpl
import io.apptolast.paparcar.detection.IosGeofenceManagerImpl
import io.apptolast.paparcar.detection.IosParkingEnrichmentScheduler
import io.apptolast.paparcar.detection.IosReportSpotScheduler
import io.apptolast.paparcar.ios.stub.StubDepartureEventBus
import io.apptolast.paparcar.ios.stub.StubParkingSyncScheduler
import io.apptolast.paparcar.domain.service.ReportSpotScheduler
import org.koin.dsl.module

val iosDetectionModule = module {
    single<ActivityRecognitionManager> { IosActivityRecognitionManagerImpl() }
    single<GeofenceEventBus> { IosGeofenceEventBusImpl() }
    single<GeofenceManager> { IosGeofenceManagerImpl(get()) }
    single<DepartureEventBus> { StubDepartureEventBus() }
    single<ParkingEnrichmentScheduler> { IosParkingEnrichmentScheduler(get(), get()) }
    single<ParkingSyncScheduler> { StubParkingSyncScheduler() }
    single<ReportSpotScheduler> { IosReportSpotScheduler(get(), get()) }
}
