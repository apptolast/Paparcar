package com.rndeveloper.paparcar.location

import android.annotation.SuppressLint
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.rndeveloper.paparcar.domain.location.LocationDataSource
import com.rndeveloper.paparcar.domain.location.UserLocationUi
import com.rndeveloper.paparcar.domain.model.GpsPoint
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
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

    @SuppressLint("MissingPermission")
    override fun observeUiLocation(): Flow<UserLocationUi> = callbackFlow {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            TimeUnit.SECONDS.toMillis(HIGH_ACCURACY_INTERVAL_S),
        ).setMinUpdateIntervalMillis(TimeUnit.SECONDS.toMillis(HIGH_ACCURACY_MIN_INTERVAL_S)).build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    // Bearing is only trustworthy while actually moving; below the threshold the
                    // course is noise, so the puck renders without rotation.
                    val bearing = if (loc.hasBearing() && loc.speed >= MIN_BEARING_SPEED_MPS) loc.bearing else null
                    trySend(
                        UserLocationUi(
                            latitude = loc.latitude,
                            longitude = loc.longitude,
                            accuracy = loc.accuracy,
                            speed = loc.speed,
                            bearingDegrees = bearing,
                        ),
                    )
                }
            }
        }
        fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { fusedLocationClient.removeLocationUpdates(callback) }
    }

    // [ROUTE-PASSIVE-FILL-001] PRIORITY_PASSIVE piggybacks on fixes OTHER apps request (a running
    // navigation app) at zero battery cost — no sampling of our own is triggered, so it can never
    // provoke a geofence or feed the OEM's power-abuse scoring. The interval only caps delivery
    // rate; with nobody else requesting, nothing arrives.
    @SuppressLint("MissingPermission")
    override fun observePassiveLocation(): Flow<GpsPoint> = callbackFlow {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_PASSIVE,
            TimeUnit.SECONDS.toMillis(PASSIVE_INTERVAL_S)
        ).build()

        val callback = createCallback(this)

        fusedLocationClient.requestLocationUpdates(
            request,
            callback,
            Looper.getMainLooper()
        )

        awaitClose { fusedLocationClient.removeLocationUpdates(callback) }
    }

    // [DET-AR-REARM-001] Passive cached read — fusedLocationClient.lastLocation does NOT request
    // updates, so it adds no fixes to the fused stream and cannot provoke a registered geofence to
    // fire a spurious EXIT (unlike requestLocationUpdates). Returns null when nothing is cached.
    @SuppressLint("MissingPermission")
    override suspend fun getLastKnownLocation(): GpsPoint? =
        runCatching { fusedLocationClient.lastLocation.await() }.getOrNull()?.toGpsPoint()

    private companion object {
        const val HIGH_ACCURACY_INTERVAL_S = 5L
        const val HIGH_ACCURACY_MIN_INTERVAL_S = 2L
        const val BALANCED_INTERVAL_S = 30L
        const val BALANCED_MIN_INTERVAL_S = 15L
        // Delivery-rate cap for the passive piggyback stream (route decimation drops the excess
        // anyway). [ROUTE-PASSIVE-FILL-001]
        const val PASSIVE_INTERVAL_S = 5L
        // Below ~walking pace the GPS course is unreliable jitter → drop the heading. [MAP-ICONS-V2]
        const val MIN_BEARING_SPEED_MPS = 1.5f

        // [DET-A-FIX-MUST-SAY-WHERE-IT-CAME-FROM-001] Populated by the location stack only on
        // GNSS-derived fixes, which is exactly what makes it the discriminator the provider string
        // cannot be: the fused client labels a network fix and a satellite fix alike.
        const val EXTRA_SATELLITES = "satellites"
    }

    // [DET-A-FIX-MUST-SAY-WHERE-IT-CAME-FROM-001] The single mapping from platform fix to domain
    // fix. Provenance is captured here or nowhere — past this point every consumer sees a GpsPoint
    // and the platform object is gone.
    private fun Location.toGpsPoint() = GpsPoint(
        latitude = latitude,
        longitude = longitude,
        accuracy = accuracy,
        timestamp = time,
        speed = speed,
        provider = provider,
        // `getInt` answers 0 for an absent key, which would claim "GNSS with zero satellites" about
        // a network fix that never mentioned satellites at all. The key has to be asked for first.
        satelliteCount = extras?.takeIf { it.containsKey(EXTRA_SATELLITES) }?.getInt(EXTRA_SATELLITES),
    )

    private fun createCallback(scope: ProducerScope<GpsPoint>) =
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { scope.trySend(it.toGpsPoint()) }
            }
        }
}
