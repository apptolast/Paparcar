package io.apptolast.paparcar.di

import android.content.Context
import android.hardware.SensorManager
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.LocationServices
import io.apptolast.paparcar.detection.ActivityRecognitionManagerImpl
import io.apptolast.paparcar.detection.GeofenceManagerImpl
import io.apptolast.paparcar.domain.ActivityRecognitionManager
import io.apptolast.paparcar.domain.service.GeofenceService
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val androidDetectionModule = module {

    // --- Activity Recognition ---
    single<ActivityRecognitionManager> { ActivityRecognitionManagerImpl(androidContext()) }
    single { ActivityRecognition.getClient(androidContext()) }

    // --- Geofence ---
    single { LocationServices.getGeofencingClient(androidContext()) }
    single<GeofenceService> { GeofenceManagerImpl(androidContext(), get()) }

    // --- Gestor del Acelerómetro (desactivado) ---
//    single { androidContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager }
//    single {
//        AccelerometerManager(
//            sensorManager = get(),
//            notificationManager = get(),
//            onVehicleStartDetected = {
//                val intent = Intent(androidContext(), SpotDetectionForegroundService::class.java)
//                androidContext().startForegroundService(intent)
//            },
//            onVehicleStopDetected = {
//                // TODO: Lógica de parada de vehículo si es necesaria
//            }
//        )
//    }
}