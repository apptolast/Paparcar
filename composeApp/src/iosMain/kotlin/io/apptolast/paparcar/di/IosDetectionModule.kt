package io.apptolast.paparcar.di

import io.apptolast.paparcar.domain.ActivityRecognitionManager
import io.apptolast.paparcar.domain.service.DepartureEventBus
import io.apptolast.paparcar.domain.service.GeofenceEventBus
import io.apptolast.paparcar.domain.service.GeofenceService
import io.apptolast.paparcar.domain.service.ParkingEnrichmentScheduler
import io.apptolast.paparcar.ios.stub.StubActivityRecognitionManager
import io.apptolast.paparcar.ios.stub.StubDepartureEventBus
import io.apptolast.paparcar.ios.stub.StubGeofenceEventBus
import io.apptolast.paparcar.ios.stub.StubGeofenceService
import io.apptolast.paparcar.ios.stub.StubParkingEnrichmentScheduler
import org.koin.dsl.module

val iosDetectionModule = module {
    single<ActivityRecognitionManager> { StubActivityRecognitionManager() }
    single<GeofenceEventBus> { StubGeofenceEventBus() }
    single<GeofenceService> { StubGeofenceService() }
    single<DepartureEventBus> { StubDepartureEventBus() }
    single<ParkingEnrichmentScheduler> { StubParkingEnrichmentScheduler() }
}
