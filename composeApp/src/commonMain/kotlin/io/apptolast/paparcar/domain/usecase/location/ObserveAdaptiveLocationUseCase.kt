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
 *
 * **[DET-BURST-001] Post-arm burst.** For the first [initialHighAccuracyWindowMs] of the stream
 * (measured from the first fix's timestamp) the mode is PINNED to HIGH_ACCURACY regardless of
 * speed. A short trip (Durango→Glorieta, ~2 min, field 2026-07-12) otherwise dropped to BALANCED
 * (30 s) the instant it crossed 5 m/s, so only ~4 fixes arrived in the whole trip and the session
 * never measured driving (`maxSpeedMps < 5` was starvation, not a slow car). The burst guarantees a
 * dense sample stream over the arm window, then hands back to the adaptive logic — a long highway
 * drive still degrades to BALANCED after the window, so the battery cost is bounded (and the
 * detection foreground service is already running, so Doze does not apply). Falls back to plain
 * adaptive behaviour when fixes carry no wall-clock timestamp (window never opens).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveAdaptiveLocationUseCase(
    private val locationDataSource: LocationDataSource,
    private val initialHighAccuracyWindowMs: Long = INITIAL_HIGH_ACCURACY_WINDOW_MS,
) {
    private enum class Mode { HighAccuracy, Balanced }

    operator fun invoke(): Flow<GpsPoint> {
        val mode = MutableStateFlow(Mode.HighAccuracy)
        // [DET-BURST-001] First fix's wall-clock, captured once; null until a timestamped fix arrives.
        var burstStartMs: Long? = null

        return mode
            .flatMapLatest { currentMode ->
                when (currentMode) {
                    Mode.HighAccuracy -> locationDataSource.observeHighAccuracyLocation()
                    Mode.Balanced -> locationDataSource.observeBalancedLocation()
                }
            }
            .onEach { location ->
                if (burstStartMs == null && location.timestamp > 0L) burstStartMs = location.timestamp
                val start = burstStartMs
                val inBurst = start != null && (location.timestamp - start) < initialHighAccuracyWindowMs
                val newMode = when {
                    // [DET-BURST-001] Pin HIGH_ACCURACY through the arm window so a short trip is
                    // densely sampled and its driving speed is actually measured.
                    inBurst -> Mode.HighAccuracy
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

        /** [DET-BURST-001] Duration from the first fix during which HIGH_ACCURACY is forced,
         *  regardless of speed. Bounds the battery cost while guaranteeing a dense early sample
         *  stream so short trips measure their driving speed. Default 3 min. */
        const val INITIAL_HIGH_ACCURACY_WINDOW_MS = 3 * 60_000L
    }
}
