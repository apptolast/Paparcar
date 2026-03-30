package io.apptolast.paparcar.location

import android.annotation.SuppressLint
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import io.apptolast.paparcar.domain.location.LocationDataSource
import io.apptolast.paparcar.domain.model.GpsPoint
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.TimeUnit

class AndroidLocationDataSourceImpl(
    private val fusedLocationClient: FusedLocationProviderClient
) : LocationDataSource {

    @SuppressLint("MissingPermission")
    override fun observeHighAccuracyLocation(): Flow<GpsPoint> = callbackFlow {

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            TimeUnit.SECONDS.toMillis(HIGH_ACCURACY_INTERVAL_S)
        ).setMinUpdateIntervalMillis(TimeUnit.SECONDS.toMillis(HIGH_ACCURACY_MIN_INTERVAL_S))
            .build()

        val callback = createCallback(this)

        fusedLocationClient.requestLocationUpdates(
            request,
            callback,
            Looper.getMainLooper()
        )

        awaitClose { fusedLocationClient.removeLocationUpdates(callback) }
    }

    @SuppressLint("MissingPermission")
    override fun observeBalancedLocation(): Flow<GpsPoint> = callbackFlow {

        val request = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            TimeUnit.SECONDS.toMillis(BALANCED_INTERVAL_S)
        ).setMinUpdateIntervalMillis(TimeUnit.SECONDS.toMillis(BALANCED_MIN_INTERVAL_S))
            .build()

        val callback = createCallback(this)

        fusedLocationClient.requestLocationUpdates(
            request,
            callback,
            Looper.getMainLooper()
        )

        awaitClose { fusedLocationClient.removeLocationUpdates(callback) }
    }

    private companion object {
        const val HIGH_ACCURACY_INTERVAL_S = 5L
        const val HIGH_ACCURACY_MIN_INTERVAL_S = 2L
        const val BALANCED_INTERVAL_S = 30L
        const val BALANCED_MIN_INTERVAL_S = 15L
    }

    private fun createCallback(scope: ProducerScope<GpsPoint>) =
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let {
                    scope.trySend(
                        GpsPoint(
                            latitude = it.latitude,
                            longitude = it.longitude,
                            accuracy = it.accuracy,
                            timestamp = it.time,
                            speed = it.speed
                        )
                    )
                }
            }
        }
}
