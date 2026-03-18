package io.apptolast.paparcar.domain.usecase.location

import io.apptolast.paparcar.domain.location.LocationDataSource
import io.apptolast.paparcar.domain.model.GpsPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach

/**
 * Returns a single location Flow that internally switches between:
 * - HIGH_ACCURACY (5s interval) when speed ≤ [SPEED_THRESHOLD_TO_BALANCED] — slowing/stopped
 * - BALANCED (30s interval)      when speed > [SPEED_THRESHOLD_TO_BALANCED] — clearly driving
 *
 * Hysteresis band [SPEED_THRESHOLD_TO_HIGH_ACCURACY, SPEED_THRESHOLD_TO_BALANCED] prevents
 * rapid toggling when speed oscillates near the threshold.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveAdaptiveLocationUseCase(
    private val locationDataSource: LocationDataSource,
) {
    private enum class Mode { HighAccuracy, Balanced }

    operator fun invoke(): Flow<GpsPoint> {
        val mode = MutableStateFlow(Mode.HighAccuracy)

        return mode
            .flatMapLatest { currentMode ->
                when (currentMode) {
                    Mode.HighAccuracy -> locationDataSource.observeHighAccuracyLocation()
                    Mode.Balanced -> locationDataSource.observeBalancedLocation()
                }
            }
            .onEach { location ->
                val newMode = when {
                    location.speed > SPEED_THRESHOLD_TO_BALANCED -> Mode.Balanced
                    location.speed < SPEED_THRESHOLD_TO_HIGH_ACCURACY -> Mode.HighAccuracy
                    else -> mode.value // hysteresis: keep current mode in transition zone
                }
                if (newMode != mode.value) mode.value = newMode
            }
            .catch { throw it } // propagate errors to the terminal call site (.catch in caller)
    }

    companion object {
        /** Above this speed (m/s ≈ 18 km/h) → switch to BALANCED to save battery. */
        private const val SPEED_THRESHOLD_TO_BALANCED = 5f

        /** Below this speed (m/s ≈ 11 km/h) → switch back to HIGH_ACCURACY. */
        private const val SPEED_THRESHOLD_TO_HIGH_ACCURACY = 3f
    }
}