@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.apptolast.paparcar.location

import io.apptolast.paparcar.domain.location.LocationDataSource
import io.apptolast.paparcar.domain.model.GpsPoint
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationAccuracy
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.CoreLocation.kCLLocationAccuracyHundredMeters
import platform.Foundation.NSError
import platform.Foundation.timeIntervalSince1970
import platform.darwin.NSObject

class IosLocationDataSourceImpl : LocationDataSource {

    override fun observeHighAccuracyLocation(): Flow<GpsPoint> = locationFlow(
        accuracy = kCLLocationAccuracyBest,
        distanceFilterMeters = HIGH_ACCURACY_DISTANCE_FILTER_M,
    )

    override fun observeBalancedLocation(): Flow<GpsPoint> = locationFlow(
        accuracy = kCLLocationAccuracyHundredMeters,
        distanceFilterMeters = BALANCED_DISTANCE_FILTER_M,
    )

    private fun locationFlow(
        accuracy: CLLocationAccuracy,
        distanceFilterMeters: Double,
    ): Flow<GpsPoint> = callbackFlow {
        val manager = CLLocationManager()
        manager.desiredAccuracy = accuracy
        manager.distanceFilter = distanceFilterMeters
        manager.pausesLocationUpdatesAutomatically = false
        manager.allowsBackgroundLocationUpdates = true

        val delegate = LocationDelegate(this@callbackFlow)
        manager.delegate = delegate
        manager.startUpdatingLocation()

        awaitClose {
            manager.stopUpdatingLocation()
            manager.delegate = null
        }
    }.flowOn(Dispatchers.Main)

    private companion object {
        const val HIGH_ACCURACY_DISTANCE_FILTER_M = 5.0
        const val BALANCED_DISTANCE_FILTER_M = 50.0
    }
}

private class LocationDelegate(
    private val scope: ProducerScope<GpsPoint>,
) : NSObject(), CLLocationManagerDelegateProtocol {

    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        val latest = didUpdateLocations.filterIsInstance<CLLocation>().lastOrNull() ?: return
        latest.coordinate.useContents {
            scope.trySend(
                GpsPoint(
                    latitude = latitude,
                    longitude = longitude,
                    accuracy = latest.horizontalAccuracy.toFloat(),
                    timestamp = (latest.timestamp.timeIntervalSince1970 * MILLIS_PER_SECOND).toLong(),
                    speed = latest.speed.toFloat().coerceAtLeast(0f),
                ),
            )
        }
    }

    override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
        // Transient CoreLocation errors (kCLErrorLocationUnknown, etc.) are routine while waiting
        // for a fix. Closing the flow here would force every consumer to retry — let upstream
        // timeouts handle persistent failures instead.
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000.0
    }
}
