@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.apptolast.paparcar.location

import io.apptolast.paparcar.domain.location.LocationDataSource
import io.apptolast.paparcar.domain.location.UserLocationUi
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

    override fun observeUiLocation(): Flow<UserLocationUi> = callbackFlow {
        val manager = CLLocationManager()
        manager.desiredAccuracy = kCLLocationAccuracyBest
        manager.distanceFilter = HIGH_ACCURACY_DISTANCE_FILTER_M
        manager.pausesLocationUpdatesAutomatically = false
        manager.allowsBackgroundLocationUpdates = true

        val delegate = UiLocationDelegate(this@callbackFlow)
        manager.delegate = delegate
        manager.startUpdatingLocation()

        awaitClose {
            manager.stopUpdatingLocation()
            manager.delegate = null
        }
    }.flowOn(Dispatchers.Main)

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

    // [DET-AR-REARM-001] Passive cached read — CLLocationManager.location returns the most recently
    // cached fix without starting updates, so it never provokes region monitoring. Null if none.
    override suspend fun getLastKnownLocation(): GpsPoint? {
        val cached = CLLocationManager().location ?: return null
        return cached.coordinate.useContents {
            GpsPoint(
                latitude = latitude,
                longitude = longitude,
                accuracy = cached.horizontalAccuracy.toFloat(),
                timestamp = (cached.timestamp.timeIntervalSince1970 * MILLIS_PER_SECOND).toLong(),
                speed = cached.speed.toFloat().coerceAtLeast(0f),
            )
        }
    }

    private companion object {
        const val HIGH_ACCURACY_DISTANCE_FILTER_M = 5.0
        const val BALANCED_DISTANCE_FILTER_M = 50.0
        const val MILLIS_PER_SECOND = 1_000.0
    }
}

private class UiLocationDelegate(
    private val scope: ProducerScope<UserLocationUi>,
) : NSObject(), CLLocationManagerDelegateProtocol {

    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        val latest = didUpdateLocations.filterIsInstance<CLLocation>().lastOrNull() ?: return
        val speed = latest.speed.toFloat().coerceAtLeast(0f)
        // CLLocation.course is negative when invalid; trust it only while actually moving.
        val bearing = if (latest.course >= 0.0 && speed >= MIN_BEARING_SPEED_MPS) latest.course.toFloat() else null
        latest.coordinate.useContents {
            scope.trySend(
                UserLocationUi(
                    latitude = latitude,
                    longitude = longitude,
                    accuracy = latest.horizontalAccuracy.toFloat(),
                    speed = speed,
                    bearingDegrees = bearing,
                ),
            )
        }
    }

    override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
        // Same rationale as [LocationDelegate]: transient CoreLocation errors are routine.
    }

    private companion object {
        const val MIN_BEARING_SPEED_MPS = 1.5f
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
