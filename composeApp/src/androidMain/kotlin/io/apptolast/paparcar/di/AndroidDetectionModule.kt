package io.apptolast.paparcar.di

import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.LocationServices
import io.apptolast.paparcar.detection.ActivityRecognitionManagerImpl
import io.apptolast.paparcar.detection.DepartureEventBusImpl
import io.apptolast.paparcar.detection.GeofenceEventBusImpl
import io.apptolast.paparcar.detection.GeofenceManagerImpl
import io.apptolast.paparcar.detection.SignificantMotionMonitor
import io.apptolast.paparcar.detection.WorkManagerParkingEnrichmentScheduler
import io.apptolast.paparcar.detection.WorkManagerParkingSyncScheduler
import io.apptolast.paparcar.detection.WorkManagerReportSpotScheduler
import io.apptolast.paparcar.detection.sensor.AndroidStepDetectorSource
import io.apptolast.paparcar.domain.ActivityRecognitionManager
import io.apptolast.paparcar.domain.sensor.StepDetectorSource
import io.apptolast.paparcar.domain.service.DepartureEventBus
import io.apptolast.paparcar.domain.service.GeofenceEventBus
import io.apptolast.paparcar.domain.service.GeofenceManager
import io.apptolast.paparcar.domain.service.ParkingEnrichmentScheduler
import io.apptolast.paparcar.domain.service.ParkingSyncScheduler
import io.apptolast.paparcar.domain.service.ReportSpotScheduler
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val androidDetectionModule = module {

    // --- Activity Recognition ---
    single<ActivityRecognitionManager> { ActivityRecognitionManagerImpl(androidContext(), get()) }
    single { ActivityRecognition.getClient(androidContext()) }

    // --- Step Detector (Sensor.TYPE_STEP_DETECTOR) [BUG-GARAGE-COLA-001] ---
    single<StepDetectorSource> { AndroidStepDetectorSource(androidContext()) }

    // --- Geofence ---
    single { LocationServices.getGeofencingClient(androidContext()) }
    single<GeofenceEventBus> { GeofenceEventBusImpl() }
    single<GeofenceManager> { GeofenceManagerImpl(androidContext(), get(), get()) }

    // --- Departure Detection ---
    single<DepartureEventBus> { DepartureEventBusImpl(androidContext()) }

    // --- Parked-session safety net: hardware wake-up trigger [DET-SIGMOTION-001] ---
    single { SignificantMotionMonitor(androidContext(), get()) }

    // --- Manual detection start ("I'm driving" cold-start affordance) [DET-G-01b] ---
    single<io.apptolast.paparcar.domain.detection.ManualParkingDetection> {
        io.apptolast.paparcar.detection.ManualParkingDetectionImpl(androidContext())
    }

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
        io.apptolast.paparcar.bluetooth.BluetoothParkingDetector(
            observeLocation = get(),
            confirmParking = get(),
            notificationPort = get(),
            config = get(),
        )
    }

}
