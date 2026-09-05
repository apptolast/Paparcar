package com.rndeveloper.paparcar.di

import com.rndeveloper.paparcar.domain.ActivityRecognitionManager
import com.rndeveloper.paparcar.domain.sensor.StepDetectorSource
import com.rndeveloper.paparcar.domain.service.DepartureEventBus
import com.rndeveloper.paparcar.domain.service.GeofenceEventBus
import com.rndeveloper.paparcar.domain.service.GeofenceManager
import com.rndeveloper.paparcar.domain.service.ParkingEnrichmentScheduler
import com.rndeveloper.paparcar.domain.service.ParkingSyncScheduler
import com.rndeveloper.paparcar.detection.IosActivityRecognitionManagerImpl
import com.rndeveloper.paparcar.detection.IosDepartureEventBusImpl
import com.rndeveloper.paparcar.detection.SharedFlowGeofenceEventBus
import com.rndeveloper.paparcar.detection.IosGeofenceManagerImpl
import com.rndeveloper.paparcar.detection.IosParkingEnrichmentScheduler
import com.rndeveloper.paparcar.detection.IosParkingSyncScheduler
import com.rndeveloper.paparcar.detection.IosReportSpotScheduler
import com.rndeveloper.paparcar.detection.IosStepDetectorSource
import com.rndeveloper.paparcar.domain.service.ReportSpotScheduler
import org.koin.dsl.module

val iosDetectionModule = module {
    single<ActivityRecognitionManager> { IosActivityRecognitionManagerImpl(get(), get()) }
    single<StepDetectorSource> { IosStepDetectorSource() }
    single<GeofenceEventBus> { SharedFlowGeofenceEventBus() }
    single<GeofenceManager> { IosGeofenceManagerImpl(get()) }
    single<DepartureEventBus> { IosDepartureEventBusImpl() }
    single<ParkingEnrichmentScheduler> { IosParkingEnrichmentScheduler(get(), get()) }
    single<ParkingSyncScheduler> { IosParkingSyncScheduler(get(), get()) }
    single<ReportSpotScheduler> { IosReportSpotScheduler(get()) }
    // [IOS-F1-A-CONTROLLER-FOR-THE-HAPPY-PATH-001] The orchestrator — the functional mirror of
    // Android's CoordinatorDetectionService. Started from MainViewController after Koin is up so
    // its geofence-bus subscription exists before any region delegate can fire.
    single {
        com.rndeveloper.paparcar.detection.IosDetectionController(
            coordinator = get(),
            observeAdaptiveLocation = get(),
            getOneLocation = get(),
            evaluateGeofenceExit = get(),
            verifyDepartureEvidence = get(),
            strategyResolver = get(),
            userParkingRepository = get(),
            geofenceManager = get(),
            geofenceEventBus = get(),
            detectionRuntime = get(),
            pendingArmRecords = get(),
            arrivalResolutionRecord = get(),
            userStopStore = com.rndeveloper.paparcar.detection.IosUserStopStore(),
            detectionEventLogger = get(),
            config = get(),
            // F2 lanes [IOS-F2-A-WAKE-MUST-QUERY-THE-PAST-001]
            runDepartureCheck = get(),
            runHonestClose = get(),
            detectionStepAnchors = get(),
            vehicleRepository = get(),
            activityRecognitionManager = get(),
            departureEventBus = get(),
            reconstructionCoordinator = { clock, steps ->
                get(org.koin.core.qualifier.named(RECONSTRUCTION_COORDINATOR)) {
                    org.koin.core.parameter.parametersOf(clock, steps)
                }
            },
        )
    }
    single<com.rndeveloper.paparcar.domain.detection.ports.ManualParkingDetection> {
        com.rndeveloper.paparcar.detection.IosManualParkingDetectionImpl(get())
    }
    single<com.rndeveloper.paparcar.domain.detection.ports.ArrivalHandoffDetection> {
        com.rndeveloper.paparcar.detection.IosArrivalHandoffDetectionImpl(get())
    }
    single<com.rndeveloper.paparcar.domain.detection.ports.DepartureWatchResumer> {
        com.rndeveloper.paparcar.detection.IosDepartureWatchResumerImpl(get())
    }

    // [IOS-F0-06] Side-record ports (NSUserDefaults) + step-seal skeleton — the F1 orchestrator's
    // durable memory for wake-and-query reconstruction.
    single<com.rndeveloper.paparcar.domain.detection.PendingArmRecords> {
        com.rndeveloper.paparcar.detection.IosPendingArmRecords()
    }
    single<com.rndeveloper.paparcar.domain.detection.ExitDeliveryRecords> {
        com.rndeveloper.paparcar.detection.IosExitDeliveryRecords()
    }
    single<com.rndeveloper.paparcar.domain.detection.ArrivalResolutionRecord> {
        com.rndeveloper.paparcar.detection.IosArrivalResolutionRecord()
    }
    single<com.rndeveloper.paparcar.domain.sensor.DetectionStepAnchors> {
        com.rndeveloper.paparcar.detection.sensor.IosDetectionStepAnchors()
    }
}
