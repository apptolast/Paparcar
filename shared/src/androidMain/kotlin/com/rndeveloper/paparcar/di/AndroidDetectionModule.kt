package com.rndeveloper.paparcar.di

import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.LocationServices
import com.rndeveloper.paparcar.detection.ActivityRecognitionManagerImpl
import com.rndeveloper.paparcar.detection.DepartureEventBusImpl
import com.rndeveloper.paparcar.detection.SharedFlowGeofenceEventBus
import com.rndeveloper.paparcar.detection.GeofenceManagerImpl
import com.rndeveloper.paparcar.detection.SignificantMotionMonitor
import com.rndeveloper.paparcar.detection.WorkManagerParkingEnrichmentScheduler
import com.rndeveloper.paparcar.detection.WorkManagerParkingSyncScheduler
import com.rndeveloper.paparcar.detection.WorkManagerReportSpotScheduler
import com.rndeveloper.paparcar.detection.sensor.AndroidDetectionStepAnchors
import com.rndeveloper.paparcar.detection.sensor.AndroidStepCounterSource
import com.rndeveloper.paparcar.detection.sensor.AndroidStepDetectorSource
import com.rndeveloper.paparcar.domain.ActivityRecognitionManager
import com.rndeveloper.paparcar.domain.sensor.DetectionStepAnchors
import com.rndeveloper.paparcar.domain.sensor.StepCounterSource
import com.rndeveloper.paparcar.domain.sensor.StepDetectorSource
import com.rndeveloper.paparcar.domain.service.DepartureEventBus
import com.rndeveloper.paparcar.domain.service.GeofenceEventBus
import com.rndeveloper.paparcar.domain.service.GeofenceManager
import com.rndeveloper.paparcar.domain.service.ParkingEnrichmentScheduler
import com.rndeveloper.paparcar.domain.service.ParkingSyncScheduler
import com.rndeveloper.paparcar.domain.service.ReportSpotScheduler
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val androidDetectionModule = module {

    // --- Activity Recognition ---
    single<ActivityRecognitionManager> { ActivityRecognitionManagerImpl(androidContext(), get()) }
    single { ActivityRecognition.getClient(androidContext()) }

    // --- Step Detector (Sensor.TYPE_STEP_DETECTOR) [BUG-GARAGE-COLA-001] ---
    single<StepDetectorSource> { AndroidStepDetectorSource(androidContext()) }

    // --- Cumulative step counter (Sensor.TYPE_STEP_COUNTER): step budget for the parked-state
    // reconcile; keeps counting in the sensor hub across process death [DET-RECONCILE-001] ---
    single<StepCounterSource> { AndroidStepCounterSource(androidContext()) }

    // --- Confirm-time step-anchor sealer: baseline for the honest-close step budget, available
    // from the moment of parking (not the safety net's first tick) [DET-HONEST-CLOSE-001] ---
    single<DetectionStepAnchors> { AndroidDetectionStepAnchors(get(), androidContext()) }

    // --- Side-record ports: thin wrappers over the existing parking_safety_net prefs (same
    // keys, same formats) — the common contracts iOS reconstruction reads. [IOS-F0-06] ---
    single<com.rndeveloper.paparcar.domain.detection.PendingArmRecords> {
        com.rndeveloper.paparcar.detection.AndroidPendingArmRecords(androidContext())
    }
    single<com.rndeveloper.paparcar.domain.detection.ExitDeliveryRecords> {
        com.rndeveloper.paparcar.detection.AndroidExitDeliveryRecords(androidContext())
    }
    single<com.rndeveloper.paparcar.domain.detection.ArrivalResolutionRecord> {
        com.rndeveloper.paparcar.detection.AndroidArrivalResolutionRecord(androidContext())
    }

    // --- Geofence ---
    single { LocationServices.getGeofencingClient(androidContext()) }
    single<GeofenceEventBus> { SharedFlowGeofenceEventBus() }
    single<GeofenceManager> { GeofenceManagerImpl(androidContext(), get(), get()) }

    // --- Departure Detection ---
    single<DepartureEventBus> { DepartureEventBusImpl(androidContext()) }

    // --- Parked-session safety net: hardware wake-up trigger [DET-SIGMOTION-001] ---
    // Third arg: DetectionRuntimeState — SENTRY-resident triggers wake the live service directly. [DET-RESIDENT-FGS-001]
    single { SignificantMotionMonitor(androidContext(), get(), get()) }

    // --- Trip trail: every one-shot fix becomes a persisted breadcrumb [DET-BREADCRUMBS-001] ---
    single<com.rndeveloper.paparcar.domain.detection.ports.TripTrail> {
        com.rndeveloper.paparcar.detection.TripTrailImpl(androidContext())
    }

    // --- Driving route: the dense tracked route persisted so the drawn line survives background /
    // cold-start mid-trip; fed by the service, restored by the trip controller [DET-ROUTE-TRACK-001] ---
    single<com.rndeveloper.paparcar.domain.detection.ports.DrivingRouteStore> {
        com.rndeveloper.paparcar.detection.DrivingRouteStoreImpl(androidContext())
    }

    // --- Departure-watch resurrection: rebuild the resident SENTRY watcher from a foreground moment
    // (visible Activity / "Reactivate" tap). Same gate as the service's idle epilogue. [DET-WATCH-REACTIVATE-001] ---
    single<com.rndeveloper.paparcar.domain.detection.ports.DepartureWatchResumer> {
        com.rndeveloper.paparcar.detection.DepartureWatchResumerImpl(
            context = androidContext(),
            observeDepartureWatchGap = get(),
        )
    }

    // --- Manual detection start ("I'm driving" cold-start affordance) [DET-G-01b] ---
    single<com.rndeveloper.paparcar.domain.detection.ports.ManualParkingDetection> {
        com.rndeveloper.paparcar.detection.ManualParkingDetectionImpl(androidContext())
    }

    // --- Arrival handoff start (safety net follows the rest of a dispatched trip).
    // Its OWN port + service action so a worker-born session is never read as the user's
    // "I'm driving". [DET-HANDOFF-NOT-MANUAL-001][DET-ARRIVAL-HANDOFF-001] ---
    single<com.rndeveloper.paparcar.domain.detection.ports.ArrivalHandoffDetection> {
        com.rndeveloper.paparcar.detection.ArrivalHandoffDetectionImpl(androidContext())
    }

    // --- Previous-process death attribution: parkdiag gaps stop being anonymous
    // [DET-MEMORY-LIMITER-IS-AN-ATTRIBUTABLE-KILL-001] ---
    single { com.rndeveloper.paparcar.diagnostics.ProcessDeathAttributor(androidContext(), get()) }

    // --- Parking Enrichment ---
    single<ParkingEnrichmentScheduler> { WorkManagerParkingEnrichmentScheduler(androidContext()) }

    // --- Parking Sync (Firestore propagation, off the confirm-parking critical path) ---
    // userId is resolved inside each worker's doWork() via AuthRepository — no coroutine scope needed here.
    single<ParkingSyncScheduler> { WorkManagerParkingSyncScheduler(androidContext()) }

    // --- Spot Report ---
    single<ReportSpotScheduler> { WorkManagerReportSpotScheduler(androidContext()) }

    // BluetoothParkingDetector is stateless — inject as factory so each Service instance
    // gets its own, keeping the scope ownership clean. [BT-REFACTOR-FGS-001]
    // [BT-NOTIF-LEGACY-CLEANUP] uses the legacy showParkingSaved notification (no REVERT
    // card); MAC-address binding makes BT detection reliable enough that the revert
    // affordance was overkill. Only takes notificationPort, not vehicleRepository.
    factory {
        com.rndeveloper.paparcar.bluetooth.BluetoothParkingDetector(
            observeLocation = get(),
            confirmParking = get(),
            notificationPort = get(),
            config = get(),
            evaluateBtPark = get(),
            detectionEventLogger = get(),
        )
    }

}
