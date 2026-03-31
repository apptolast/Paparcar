package io.apptolast.paparcar.di

import io.apptolast.paparcar.domain.ActivityRecognitionManager
import io.apptolast.paparcar.domain.service.DepartureEventBus
import io.apptolast.paparcar.domain.service.GeofenceEventBus
import io.apptolast.paparcar.domain.service.GeofenceManager
import io.apptolast.paparcar.domain.service.ParkingEnrichmentScheduler
import io.apptolast.paparcar.ios.stub.StubActivityRecognitionManager
import io.apptolast.paparcar.ios.stub.StubDepartureEventBus
import io.apptolast.paparcar.ios.stub.StubGeofenceEventBus
import io.apptolast.paparcar.ios.stub.StubGeofenceManager
import io.apptolast.paparcar.ios.stub.StubParkingEnrichmentScheduler
import io.apptolast.paparcar.ios.stub.StubReportSpotScheduler
import io.apptolast.paparcar.domain.service.ReportSpotScheduler
import org.koin.dsl.module

val iosDetectionModule = module {
    single<ActivityRecognitionManager> { StubActivityRecognitionManager() }
    single<GeofenceEventBus> { StubGeofenceEventBus() }
    single<GeofenceManager> { StubGeofenceManager() }
    single<DepartureEventBus> { StubDepartureEventBus() }
    single<ParkingEnrichmentScheduler> { StubParkingEnrichmentScheduler() }
    single<ReportSpotScheduler> { StubReportSpotScheduler() }
}
