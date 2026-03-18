package io.apptolast.paparcar.domain.usecase.location

import io.apptolast.paparcar.domain.location.LocationDataSource
import io.apptolast.paparcar.domain.model.GpsPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Returns a single GPS location reading, then cancels the underlying stream.
 *
 * Wraps [LocationDataSource.observeBalancedLocation] with a [TIMEOUT_MS] guard
 * so callers are never suspended indefinitely (e.g. when GPS is unavailable or cold).
 * Returns null on timeout or if no fix is obtained within the window.
 *
 * Used by [DepartureDetectionWorker] to obtain the current speed at geofence-exit
 * time without starting a continuous location stream.
 */
class GetOneLocationUseCase(
    private val locationDataSource: LocationDataSource,
) {
    suspend operator fun invoke(): GpsPoint? =
        withTimeoutOrNull(TIMEOUT_MS) {
            locationDataSource.observeBalancedLocation().first()
        }

    companion object {
        private const val TIMEOUT_MS = 15_000L
    }
}