package io.apptolast.paparcar.domain.service

import kotlinx.coroutines.flow.Flow

/**
 * Platform geofence registration port. [IOS-F0-04] The CONTRACT is command + observe; how the OS
 * delivers a transition into the process is platform INFRASTRUCTURE, deliberately outside it:
 *
 *  - **Android**: the OS fires a `PendingIntent.getForegroundService` that REVIVES a dead process
 *    straight into `CoordinatorDetectionService` (RC 9100 EXIT / 9101 enter-cure / 9102 witness —
 *    load-bearing, see the manifest and [DET-G-01]). The service acts on the intent decisively
 *    and republishes the event on [GeofenceEventBus] for in-process observers.
 *  - **iOS**: the OS relaunches the app (even after reboot) and calls the CoreLocation delegate,
 *    which publishes on the bus — there the bus IS the primary consumption path.
 *
 * **Re-registration semantics** (why no common re-register API exists): Play Services can lose
 * registrations (boot, GMS wipe) — Android re-registers via its boot receiver / janitor / cure
 * paths. CoreLocation's `monitoredRegions` persist across reboots and app kills on their own —
 * an iOS impl must RECONCILE OS regions against Room sessions on start, never blind-re-register.
 */
interface GeofenceManager {

    /**
     * The platform's radius floor: [createGeofence] clamps any smaller request UP to this value.
     * Default 0 = no platform floor (Android: sizing lives in `geofenceRadiusFor`, 60–200 m).
     * iOS declares 100 m (Apple's practical CLCircularRegion minimum) — callers sizing fences
     * must read the floor here instead of assuming the registered radius equals the requested
     * one. [IOS-F0-04, decisión 6]
     */
    val minRadiusMeters: Float get() = 0f

    /** Registers a fence at the given center. [radiusMeters] below [minRadiusMeters] is clamped
     *  up by the implementation. */
    suspend fun createGeofence(
        geofenceId: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Float,
    ): Result<Unit>

    suspend fun removeGeofence(geofenceId: String): Result<Unit>

    /**
     * Deregisters every geofence this app has registered with the OS, regardless of id.
     *
     * Geofences live in Play Services / CoreLocation, not in Room, so wiping local storage
     * (sign-out, account switch) does not remove them — they would keep monitoring and could
     * fire an exit transition under the next user's session. This is the single teardown hook
     * that guarantees session isolation at the OS level. [SESSION-ISOLATION-001]
     */
    suspend fun removeAllGeofences(): Result<Unit>

    /** In-process observation stream of geofence transitions — the [GeofenceEventBus] surface.
     *  See the bus contract for its delivery guarantees (hot, broadcast, non-durable). */
    fun getGeofenceEvents(): Flow<GeofenceEvent>
}

sealed class GeofenceEvent {
    data class Exited(val geofenceId: String, val timestamp: Long) : GeofenceEvent()
    data class Error(val error: String, val timestamp: Long) : GeofenceEvent()
}
