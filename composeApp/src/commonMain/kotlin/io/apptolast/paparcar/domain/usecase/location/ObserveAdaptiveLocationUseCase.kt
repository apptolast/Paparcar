package io.apptolast.paparcar.domain.usecase.location

import io.apptolast.paparcar.domain.model.SpotLocation
import io.apptolast.paparcar.domain.repository.LocationRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val locationRepository: LocationRepository,
) {
    private enum class Mode { HighAccuracy, Balanced }

    operator fun invoke(): Flow<SpotLocation> {
        val mode = MutableStateFlow(Mode.HighAccuracy)

        return mode
            .flatMapLatest { currentMode ->
                when (currentMode) {
                    Mode.HighAccuracy -> locationRepository.observeHighAccuracyFlow()
                    Mode.Balanced -> locationRepository.locationFlow()
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
    }

    companion object {
        /** Above this speed (m/s ≈ 18 km/h) → switch to BALANCED to save battery. */
        private const val SPEED_THRESHOLD_TO_BALANCED = 5f

        /** Below this speed (m/s ≈ 11 km/h) → switch back to HIGH_ACCURACY. */
        private const val SPEED_THRESHOLD_TO_HIGH_ACCURACY = 3f
    }
}