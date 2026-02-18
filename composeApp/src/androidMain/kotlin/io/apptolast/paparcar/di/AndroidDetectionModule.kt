package io.apptolast.paparcar.di

import android.content.Context
import android.content.Intent
import android.hardware.SensorManager
import io.apptolast.paparcar.detection.AccelerometerManager
import io.apptolast.paparcar.detection.ActivityRecognitionManager
import io.apptolast.paparcar.detection.ActivityRecognitionManagerImpl
import io.apptolast.paparcar.detection.services.SpotDetectionForegroundService
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val androidDetectionModule = module {
    single<ActivityRecognitionManager> { ActivityRecognitionManagerImpl(androidContext()) }

    single { androidContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager }

    single {
        AccelerometerManager(get()) { // Ahora inyecta SensorManager
            val intent = Intent(androidContext(), SpotDetectionForegroundService::class.java)
            androidContext().startForegroundService(intent)
        }
    }
}
