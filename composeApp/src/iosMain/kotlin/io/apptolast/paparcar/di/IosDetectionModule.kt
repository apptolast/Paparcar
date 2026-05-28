package io.apptolast.paparcar.di

import io.apptolast.paparcar.domain.ActivityRecognitionManager
import io.apptolast.paparcar.domain.sensor.StepDetectorSource
import io.apptolast.paparcar.domain.service.DepartureEventBus
import io.apptolast.paparcar.domain.service.GeofenceEventBus
import io.apptolast.paparcar.domain.service.GeofenceManager
import io.apptolast.paparcar.domain.service.ParkingEnrichmentScheduler
import io.apptolast.paparcar.domain.service.ParkingSyncScheduler
import io.apptolast.paparcar.detection.IosActivityRecognitionManagerImpl
import io.apptolast.paparcar.detection.IosGeofenceEventBusImpl
import io.apptolast.paparcar.detection.IosGeofenceManagerImpl
import io.apptolast.paparcar.detection.IosParkingEnrichmentScheduler
import io.apptolast.paparcar.detection.IosParkingSyncScheduler
import io.apptolast.paparcar.detection.IosReportSpotScheduler
import io.apptolast.paparcar.detection.IosStepDetectorSource
import io.apptolast.paparcar.ios.stub.StubDepartureEventBus
import io.apptolast.paparcar.domain.service.ReportSpotScheduler
import org.koin.dsl.module

val iosDetectionModule = module {
    single<ActivityRecognitionManager> { IosActivityRecognitionManagerImpl(get(), get()) }
    // Step detector — empty-flow stub for iOS. CMPedometer-backed impl tracked in
    // docs/backlog/detection-improvements-2026-05-27.md.
    single<StepDetectorSource> { IosStepDetectorSource() }
    single<GeofenceEventBus> { IosGeofenceEventBusImpl() }
    single<GeofenceManager> { IosGeofenceManagerImpl(get()) }
    single<DepartureEventBus> { StubDepartureEventBus() }
    single<ParkingEnrichmentScheduler> { IosParkingEnrichmentScheduler(get(), get()) }
    // Real iOS implementation — coroutine-scope + retry. No process-death persistence yet. [IOS-SYNC-001]
    single<ParkingSyncScheduler> { IosParkingSyncScheduler(get(), get()) }
    single<ReportSpotScheduler> { IosReportSpotScheduler(get(), get()) }
}
