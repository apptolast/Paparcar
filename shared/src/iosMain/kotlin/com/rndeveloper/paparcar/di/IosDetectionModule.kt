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
import com.rndeveloper.paparcar.detection.IosGeofenceEventBusImpl
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
    single<GeofenceEventBus> { IosGeofenceEventBusImpl() }
    single<GeofenceManager> { IosGeofenceManagerImpl(get()) }
    single<DepartureEventBus> { IosDepartureEventBusImpl() }
    single<ParkingEnrichmentScheduler> { IosParkingEnrichmentScheduler(get(), get()) }
    single<ParkingSyncScheduler> { IosParkingSyncScheduler(get(), get()) }
    single<ReportSpotScheduler> { IosReportSpotScheduler(get()) }
    single<com.rndeveloper.paparcar.domain.detection.ports.ManualParkingDetection> {
        com.rndeveloper.paparcar.detection.IosManualParkingDetectionImpl()
    }
    // [DET-HANDOFF-NOT-MANUAL-001] No detection service on iOS yet — the handoff port is a no-op.
    single<com.rndeveloper.paparcar.domain.detection.ports.ArrivalHandoffDetection> {
        com.rndeveloper.paparcar.detection.IosArrivalHandoffDetectionImpl()
    }
    // No resident departure watcher on iOS yet — the resumer is a no-op. [DET-WATCH-REACTIVATE-001]
    single<com.rndeveloper.paparcar.domain.detection.ports.DepartureWatchResumer> {
        com.rndeveloper.paparcar.detection.IosDepartureWatchResumerImpl()
    }
}
