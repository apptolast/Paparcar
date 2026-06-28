package io.apptolast.paparcar.domain.location

import io.apptolast.paparcar.domain.model.GpsPoint
import kotlinx.coroutines.flow.Flow

interface LocationDataSource {

    fun observeBalancedLocation(): Flow<GpsPoint>

    fun observeHighAccuracyLocation(): Flow<GpsPoint>

    /**
     * Returns the **last known** location WITHOUT starting active sampling, or null if none is
     * cached. Critical distinction vs [observeBalancedLocation]: an active location request feeds
     * fresh fixes to Play Services, which then re-evaluates any registered geofence against them —
     * a bad indoor fix outside the radius fires a spurious GEOFENCE_EXIT. Reading the cached fix
     * generates no new updates, so it never provokes the geofence. Used by the AR proximity re-arm
     * and the watchdog, which must read position while parked without nudging the geofence.
     * [DET-AR-REARM-001 / no-active-poll-while-parked]
     */
    suspend fun getLastKnownLocation(): GpsPoint?
}