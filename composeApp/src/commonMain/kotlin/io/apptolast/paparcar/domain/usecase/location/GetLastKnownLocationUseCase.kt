package io.apptolast.paparcar.domain.usecase.location

import io.apptolast.paparcar.domain.location.LocationDataSource
import io.apptolast.paparcar.domain.model.GpsPoint

/**
 * Returns the last known location WITHOUT starting active sampling, or null if none is cached.
 *
 * Unlike [GetOneLocationUseCase] (which opens an active balanced-location stream), this reads the
 * cached fix only, so it does NOT feed new updates to Play Services and therefore cannot provoke a
 * registered geofence into firing a spurious EXIT. Used by the AR proximity re-arm and the watchdog,
 * which need the device position while the user is parked without nudging the geofence.
 * [DET-AR-REARM-001]
 */
class GetLastKnownLocationUseCase(
    private val locationDataSource: LocationDataSource,
) {
    suspend operator fun invoke(): GpsPoint? = locationDataSource.getLastKnownLocation()
}
