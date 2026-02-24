package io.apptolast.paparcar.location

import android.annotation.SuppressLint
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import io.apptolast.paparcar.data.datasource.platform.PlatformLocationDataSource
import io.apptolast.paparcar.domain.model.SpotLocation
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

class AndroidLocationDataSourceImpl(
    private val fusedLocationClient: FusedLocationProviderClient
) : PlatformLocationDataSource {

    @SuppressLint("MissingPermission")
    override fun observeHighAccuracyLocation(): Flow<SpotLocation> = callbackFlow {

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            TimeUnit.SECONDS.toMillis(5)
        ).setMinUpdateIntervalMillis(TimeUnit.SECONDS.toMillis(2))
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
    override fun observeBalancedLocation(): Flow<SpotLocation> = callbackFlow {

        val request = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            TimeUnit.SECONDS.toMillis(30)
        ).setMinUpdateIntervalMillis(TimeUnit.SECONDS.toMillis(15))
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
    override suspend fun getHighAccuracyLocation(): SpotLocation? {
        val location = try {
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                null
            ).await()
        } catch (e: Exception) {
            // Can happen if location is disabled
            null
        }

        return location?.let {
            SpotLocation(
                latitude = it.latitude,
                longitude = it.longitude,
                accuracy = it.accuracy,
                timestamp = it.time,
                speed = it.speed
            )
        }
    }

    private fun createCallback(scope: ProducerScope<SpotLocation>) =
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let {
                    scope.trySend(
                        SpotLocation(
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
