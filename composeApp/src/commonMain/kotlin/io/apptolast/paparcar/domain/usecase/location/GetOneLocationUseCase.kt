package io.apptolast.paparcar.domain.usecase.location

import io.apptolast.paparcar.domain.model.SpotLocation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Returns a single GPS location reading, then cancels the underlying stream.
 *
 * Wraps [ObserveLocationUpdatesUseCase] with a [TIMEOUT_MS] guard so callers
 * are never suspended indefinitely (e.g. when GPS is unavailable or cold).
 * Returns null on timeout or if no fix is obtained within the window.
 *
 * Used by [CheckDepartureWorker] to obtain the current speed at geofence-exit
 * time without starting a continuous location stream.
 */
class GetOneLocationUseCase(
    private val observeLocationUpdates: ObserveLocationUpdatesUseCase,
) {
    suspend operator fun invoke(): SpotLocation? =
        withTimeoutOrNull(TIMEOUT_MS) {
            observeLocationUpdates().first()
        }

    companion object {
        private const val TIMEOUT_MS = 8_000L
    }
}
