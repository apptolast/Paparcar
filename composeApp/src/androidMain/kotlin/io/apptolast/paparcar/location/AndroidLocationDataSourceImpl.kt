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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.TimeUnit

class AndroidLocationDataSourceImpl(
    private val fusedLocationClient: FusedLocationProviderClient
) : PlatformLocationDataSource {

    @SuppressLint("MissingPermission")
    override fun observeLocation(): Flow<SpotLocation> {
        return callbackFlow {
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                TimeUnit.SECONDS.toMillis(5)
            ).apply {
                setMinUpdateIntervalMillis(TimeUnit.SECONDS.toMillis(2))
            }.build()

            val locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let {
                        trySend(SpotLocation(it.latitude, it.longitude, it.accuracy, it.time))
                    }
                }
            }

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )

            awaitClose { fusedLocationClient.removeLocationUpdates(locationCallback) }
        }
    }
}
