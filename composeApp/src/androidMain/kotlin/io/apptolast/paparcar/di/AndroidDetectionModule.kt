package io.apptolast.paparcar.di

import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.LocationServices
import io.apptolast.paparcar.bluetooth.BluetoothParkingDetector
import io.apptolast.paparcar.detection.ActivityRecognitionManagerImpl
import io.apptolast.paparcar.detection.DepartureEventBusImpl
import io.apptolast.paparcar.detection.GeofenceEventBusImpl
import io.apptolast.paparcar.detection.GeofenceManagerImpl
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

val androidDetectionModule = module {

    // --- Activity Recognition ---
    single<ActivityRecognitionManager> { ActivityRecognitionManagerImpl(androidContext()) }
    single { ActivityRecognition.getClient(androidContext()) }

    // --- Step Detector (Sensor.TYPE_STEP_DETECTOR) [BUG-GARAGE-COLA-001] ---
    single<StepDetectorSource> { AndroidStepDetectorSource(androidContext()) }

    // --- Geofence ---
    single { LocationServices.getGeofencingClient(androidContext()) }
    single<GeofenceEventBus> { GeofenceEventBusImpl() }
    single<GeofenceManager> { GeofenceManagerImpl(androidContext(), get(), get()) }

    // --- Departure Detection ---
    single<DepartureEventBus> { DepartureEventBusImpl() }

    // --- Parking Enrichment ---
    single<ParkingEnrichmentScheduler> { WorkManagerParkingEnrichmentScheduler(androidContext()) }

    // --- Parking Sync (Firestore propagation, off the confirm-parking critical path) ---
    single<ParkingSyncScheduler> { WorkManagerParkingSyncScheduler(androidContext(), get()) }

    // --- Spot Report ---
    single<ReportSpotScheduler> { WorkManagerReportSpotScheduler(androidContext()) }

    // --- Bluetooth Parking Detection ---
    // Named scope so tests can inject a TestScope and control cancellation. [§13]
    single(named("btDetectorScope")) { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    single { BluetoothParkingDetector(get(), get(), get(named("btDetectorScope"))) }

}
