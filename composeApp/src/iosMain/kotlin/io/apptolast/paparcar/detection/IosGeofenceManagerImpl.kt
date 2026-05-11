@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.apptolast.paparcar.detection

import io.apptolast.paparcar.domain.service.GeofenceEvent
import io.apptolast.paparcar.domain.service.GeofenceEventBus
import io.apptolast.paparcar.domain.service.GeofenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import platform.CoreLocation.CLCircularRegion
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.CLRegion
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.timeIntervalSince1970
import platform.darwin.NSObject

/**
 * iOS implementation of [GeofenceManager] backed by [CLLocationManager] region monitoring.
 *
 * Differences vs. the Android (`GeofencingClient`) implementation:
 * - iOS limits an app to **20 monitored regions** total. Paparcar registers one
 *   geofence per active parking session, so this is comfortably within the cap.
 * - iOS has no equivalent of `NEVER_EXPIRE` — registered regions persist until
 *   explicitly stopped via [removeGeofence] or until the app is uninstalled.
 * - There is no `loiteringDelay` knob. We only monitor exit transitions, matching
 *   the Android `GEOFENCE_TRANSITION_EXIT` setup.
 * - Authorisation must be "Always" (background) for transitions to fire while the
 *   app is suspended; the [IosPermissionRequester] flow already prompts for this.
 *
 * Registrations are routed through `Dispatchers.Main` because [CLLocationManager]
 * must be called on a thread with an active runloop.
 */
class IosGeofenceManagerImpl(
    private val geofenceEventBus: GeofenceEventBus,
) : GeofenceManager {

    private val manager = CLLocationManager()

    private val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
        override fun locationManager(manager: CLLocationManager, didExitRegion: CLRegion) {
            geofenceEventBus.emit(
                GeofenceEvent.Exited(
                    geofenceId = didExitRegion.identifier,
                    timestamp = nowMillis(),
                ),
            )
        }

        override fun locationManager(
            manager: CLLocationManager,
            monitoringDidFailForRegion: CLRegion?,
            withError: NSError,
        ) {
            val regionId = monitoringDidFailForRegion?.identifier ?: "unknown"
            geofenceEventBus.emit(
                GeofenceEvent.Error(
                    error = "$regionId: ${withError.localizedDescription}",
                    timestamp = nowMillis(),
                ),
            )
        }
    }

    init {
        manager.delegate = delegate
    }

    override suspend fun createGeofence(
        geofenceId: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Float,
    ): Result<Unit> = withContext(Dispatchers.Main) {
        runCatching {
            val effectiveRadius = radiusMeters.toDouble().coerceAtLeast(MIN_RADIUS_M)
            val region = CLCircularRegion(
                center = CLLocationCoordinate2DMake(latitude, longitude),
                radius = effectiveRadius,
                identifier = geofenceId,
            ).apply {
                setNotifyOnEntry(false)
                setNotifyOnExit(true)
            }
            manager.startMonitoringForRegion(region)
        }
    }

    override suspend fun removeGeofence(geofenceId: String): Result<Unit> = withContext(Dispatchers.Main) {
        runCatching {
            val region = manager.monitoredRegions
                .filterIsInstance<CLRegion>()
                .firstOrNull { it.identifier == geofenceId }
            if (region != null) manager.stopMonitoringForRegion(region)
        }
    }

    override fun getGeofenceEvents(): Flow<GeofenceEvent> = geofenceEventBus.events

    private fun nowMillis(): Long = (NSDate().timeIntervalSince1970 * MILLIS_PER_SECOND).toLong()

    private companion object {
        /**
         * Practical minimum radius for CLCircularRegion. Apple recommends ≥100 m to
         * stay above device GPS noise floor; Android-side default is 80 m which we
         * round up to keep the iOS path within spec.
         */
        const val MIN_RADIUS_M = 100.0
        const val MILLIS_PER_SECOND = 1_000.0
    }
}
