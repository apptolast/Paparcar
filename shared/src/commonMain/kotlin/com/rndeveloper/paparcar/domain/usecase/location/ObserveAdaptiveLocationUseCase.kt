package com.rndeveloper.paparcar.domain.usecase.location

import com.rndeveloper.paparcar.domain.location.ChargingStateSource
import com.rndeveloper.paparcar.domain.location.LocationDataSource
import com.rndeveloper.paparcar.domain.model.GpsPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach

/**
 * Returns a single location Flow that internally switches between three sampling tiers by measured
 * speed. [DET-ADAPTIVE-SAMPLING-EARNS-ITS-FIXES-001]
 *
 * - **HIGH_ACCURACY** (5 s) — slowing/stopped (≤ [SPEED_THRESHOLD_TO_HIGH_ACCURACY]): where parking
 *   decisions and the search-for-a-spot happen, densest by design.
 * - **CRUISE** (10 s, still HIGH_ACCURACY priority) — clearly driving below motorway pace. Corpus
 *   measurement (8 field traces, 421 driving-band fix pairs) showed the old BALANCED tier really
 *   delivered ~14–15 s cadence → p90 **132 m between route vertices** (265 m in one urban trace):
 *   corners the map-matcher had to guess. At 10 s/5 s a 50 km/h street keeps vertices ~70–140 m
 *   apart. The trip's route is a product deliverable now — this tier is where it is earned.
 * - **HIGHWAY** (BALANCED, 30 s) — at or above [SPEED_THRESHOLD_TO_HIGHWAY]. Motorway geometry is
 *   the map-matcher's trivial case (the road graph restores it), so the old cadence stays and the
 *   battery is spent where it buys shape. Wide re-entry hysteresis
 *   ([SPEED_THRESHOLD_HIGHWAY_TO_CRUISE]) so speed oscillation around the bound doesn't flap.
 *
 * ⛔ **Only-densify rule** (ticket invariant): no state at any speed samples SLOWER than the
 * two-tier design did. Cruise strictly densifies the old BALANCED band; Highway IS the old
 * BALANCED band. [DET-BURST-001] measured that fix starvation changes verdicts
 * (`maxSpeedMps < 5` was hunger, not a slow car) — density only ever moves up.
 *
 * **Charging boost**: while the device charges, the cost of density is irrelevant — the mode pins
 * HIGH_ACCURACY at any speed. The [ChargingStateSource] port is optional (null = no boost, iOS
 * today) so no platform is forced to wire power state to get location.
 *
 * **[DET-BURST-001] Post-arm burst** (unchanged): for the first [initialHighAccuracyWindowMs] of
 * the stream (measured from the first fix's timestamp) the mode is PINNED to HIGH_ACCURACY
 * regardless of speed, so short trips measure their driving speed. Falls back to plain adaptive
 * behaviour when fixes carry no wall-clock timestamp (window never opens).
 *
 * **[BATT-003] Relax-only debounce.** Densifying (moving toward HIGH_ACCURACY) is ALWAYS instant —
 * the only-densify rule means that direction can never cost the detector anything. Relaxing (moving
 * toward HIGHWAY) must sustain the SAME request for [RELAX_DEBOUNCE_MS] before it takes effect: a
 * field measurement of the plain version (`dumpsys location`, 16/17-11-2026) showed the platform
 * location request re-registering 65 times in under 12 h, alternating `@+5s`/`@+10s` as speed
 * oscillated a few km/h around the CRUISE boundary — each re-registration is its own wakelock +
 * WiFi/cell scan, not a cheaper request. A speed return to the denser band CANCELS the pending relax
 * outright (it is re-armed from zero on the next request, never resumed), so this can only ever
 * leave the stream denser for longer, never sparser — compatible with the only-densify rule by
 * construction. Skipped when a fix carries no trustworthy wall clock (`timestamp <= 0`), the same
 * sentinel the burst window above already treats as "no clock to measure against": an untimed
 * stream gets the old, instant behaviour rather than a debounce that can never satisfy.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveAdaptiveLocationUseCase(
    private val locationDataSource: LocationDataSource,
    private val chargingStateSource: ChargingStateSource? = null,
    private val initialHighAccuracyWindowMs: Long = INITIAL_HIGH_ACCURACY_WINDOW_MS,
) {
    private enum class Mode { HighAccuracy, Cruise, Highway }

    operator fun invoke(): Flow<GpsPoint> {
        val mode = MutableStateFlow(Mode.HighAccuracy)
        // [DET-BURST-001] First fix's wall-clock, captured once; null until a timestamped fix arrives.
        var burstStartMs: Long? = null
        // [BATT-003] The relax request currently being sustained, and when it started — null means
        // nothing is pending. Reset to null (never resumed) the moment speed stops asking for it, so
        // a boundary oscillation restarts the window instead of accumulating across it.
        var pendingRelaxMode: Mode? = null
        var pendingRelaxSinceMs: Long? = null

        return mode
            .flatMapLatest { currentMode ->
                when (currentMode) {
                    Mode.HighAccuracy -> locationDataSource.observeHighAccuracyLocation()
                    Mode.Cruise -> locationDataSource.observeCruiseLocation()
                    Mode.Highway -> locationDataSource.observeBalancedLocation()
                }
            }
            .onEach { location ->
                if (burstStartMs == null && location.timestamp > 0L) burstStartMs = location.timestamp
                val start = burstStartMs
                val inBurst = start != null && (location.timestamp - start) < initialHighAccuracyWindowMs
                val current = mode.value
                val requested = nextMode(current, location.speed, inBurst)
                when {
                    requested == current -> {
                        // Nothing asked for a change — any in-flight relax is no longer wanted.
                        pendingRelaxMode = null
                        pendingRelaxSinceMs = null
                    }
                    // [BATT-003] Densify (lower ordinal = denser): never debounced, by construction
                    // of the only-densify rule.
                    requested.ordinal < current.ordinal -> {
                        mode.value = requested
                        pendingRelaxMode = null
                        pendingRelaxSinceMs = null
                    }
                    location.timestamp <= 0L -> mode.value = requested // no clock to debounce against
                    pendingRelaxMode != requested -> {
                        // A new (or first) relax ask — start sustaining it from here.
                        pendingRelaxMode = requested
                        pendingRelaxSinceMs = location.timestamp
                    }
                    location.timestamp - (pendingRelaxSinceMs ?: location.timestamp) >= RELAX_DEBOUNCE_MS -> {
                        mode.value = requested
                        pendingRelaxMode = null
                        pendingRelaxSinceMs = null
                    }
                    // else: same relax ask, not sustained long enough yet — stay put.
                }
            }
            .catch { throw it } // propagate errors to the terminal call site (.catch in caller)
    }

    /** The tier transition rule — per-mode, so each boundary keeps its own hysteresis band. */
    private fun nextMode(current: Mode, speed: Float, inBurst: Boolean): Mode = when {
        // [DET-BURST-001] Dense early sampling so short trips measure their driving speed.
        inBurst -> Mode.HighAccuracy
        // Charging: density is free — spend it. [DET-ADAPTIVE-SAMPLING-EARNS-ITS-FIXES-001]
        chargingStateSource?.isCharging() == true -> Mode.HighAccuracy
        else -> when (current) {
            Mode.HighAccuracy -> when {
                speed >= SPEED_THRESHOLD_TO_HIGHWAY -> Mode.Highway // burst ends mid-motorway
                speed > SPEED_THRESHOLD_TO_CRUISE -> Mode.Cruise
                else -> Mode.HighAccuracy
            }
            Mode.Cruise -> when {
                speed >= SPEED_THRESHOLD_TO_HIGHWAY -> Mode.Highway
                speed < SPEED_THRESHOLD_TO_HIGH_ACCURACY -> Mode.HighAccuracy
                else -> Mode.Cruise // hysteresis: 3–25 m/s keeps cruising
            }
            Mode.Highway -> when {
                speed < SPEED_THRESHOLD_TO_HIGH_ACCURACY -> Mode.HighAccuracy // hard stop
                speed < SPEED_THRESHOLD_HIGHWAY_TO_CRUISE -> Mode.Cruise
                else -> Mode.Highway // hysteresis: 20–25 m/s stays Highway
            }
        }
    }

    companion object {
        /** Above this speed (m/s ≈ 18 km/h) → leave HIGH_ACCURACY for CRUISE. Same bound the
         *  two-tier design used toward BALANCED — the band keeps its meaning, only its density
         *  changed. */
        private const val SPEED_THRESHOLD_TO_CRUISE = 5f

        /** Below this speed (m/s ≈ 11 km/h) → back to HIGH_ACCURACY. */
        private const val SPEED_THRESHOLD_TO_HIGH_ACCURACY = 3f

        /** At or above this speed (m/s = 90 km/h) → HIGHWAY (the old BALANCED cadence). Urban and
         *  interurban roads never sustain it; motorways do. */
        private const val SPEED_THRESHOLD_TO_HIGHWAY = 25f

        /** Below this speed (m/s = 72 km/h) a Highway stream returns to CRUISE. The wide 20–25 m/s
         *  band absorbs motorway speed oscillation without flapping streams. */
        private const val SPEED_THRESHOLD_HIGHWAY_TO_CRUISE = 20f

        /** [DET-BURST-001] Duration from the first fix during which HIGH_ACCURACY is forced,
         *  regardless of speed. Bounds the battery cost while guaranteeing a dense early sample
         *  stream so short trips measure their driving speed. Default 3 min. */
        const val INITIAL_HIGH_ACCURACY_WINDOW_MS = 3 * 60_000L

        /** [BATT-003] How long a relax request (toward a less dense tier) must sustain before it
         *  takes effect. Lower bound of the 20–30 s window measured against the real re-registration
         *  churn (`dumpsys location`, 16/17-11-2026) — wide enough to absorb a boundary oscillation,
         *  never applied to a densify. */
        private const val RELAX_DEBOUNCE_MS = 20_000L
    }
}
