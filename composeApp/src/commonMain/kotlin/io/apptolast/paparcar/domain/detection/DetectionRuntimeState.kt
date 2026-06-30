package io.apptolast.paparcar.domain.detection

import io.apptolast.paparcar.domain.model.GpsPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Context of the trip the detection job is currently following, resolved by the foreground service
 * when it arms (e.g. a geofence-exit knows the exact departing session). Carried to the UI through
 * [DetectionReadiness.Monitoring] so the trip's blue origin dot and driving puck bind to the vehicle
 * that actually left — not a guessed "most recent session" / "active vehicle". Null for trips armed
 * without a known origin (manual start). This is the single channel for service→UI trip context;
 * future per-trip signals (e.g. the candidate/confirming phase) extend this type rather than adding
 * new buses. [DEPART-CONSISTENCY-001]
 */
data class TripContext(
    val departurePoint: GpsPoint,
    // Nullable to mirror UserParking.vehicleId; in practice a parked session always has one. Home
    // falls back to the active-vehicle guess when null. [DEPART-CONSISTENCY-001]
    val departingVehicleId: String?,
)

/**
 * Coarse, UI-facing phase of an in-progress trip — the probabilistic coordinator's rich internal
 * [io.apptolast.paparcar.domain.coordinator.ConfirmationPhase] mapped down to what Home shows. Kept
 * intentionally minimal (no scores, no Android types) so it can ride the same service→UI channel as
 * [TripContext]. [DET-PHASE-001]
 */
enum class DetectionPhase {
    /** Vehicle in motion (or just armed) — a normal trip in progress ("Conduciendo"). */
    Driving,

    /** Stopped and being evaluated for parking — the user appears to be leaving the car. The moment
     *  to surface a distinct "looking for / confirming a spot" treatment in the UI. */
    Candidate,
}

/**
 * Write side of the detection phase: the probabilistic coordinator pushes its mapped [DetectionPhase]
 * here. Narrow on purpose so the coordinator (commonMain domain) depends only on this, not on the
 * full mutable runtime. [DET-PHASE-001]
 */
interface DetectionPhaseSink {
    fun setPhase(phase: DetectionPhase)
}

/**
 * Read-only visibility into whether a detection tracking job is currently active and, when known,
 * the [TripContext] and [DetectionPhase] of the trip it is following.
 *
 * The Coordinator foreground service owns the real job; this interface exposes platform-agnostic
 * state so the domain (e.g. [io.apptolast.paparcar.domain.usecase.detection.ObserveDetectionReadinessUseCase])
 * can tell "armed & idle" (Ready) apart from "actively tracking a trip" (Monitoring) without
 * depending on Android. [DET-READY-001c]
 */
interface DetectionRuntimeState {
    /** True while a tracking job is running in the foreground service. */
    val isRunning: StateFlow<Boolean>

    /** The current trip's origin (departing vehicle + spot), or null when idle / origin unknown.
     *  Default no-op so test/preview doubles that don't exercise it need no change. [DEPART-CONSISTENCY-001] */
    val trip: StateFlow<TripContext?> get() = NO_TRIP

    /** The current trip's coarse phase. Defaults to [DetectionPhase.Driving] so doubles that don't
     *  exercise it need no change. [DET-PHASE-001] */
    val phase: StateFlow<DetectionPhase> get() = ALWAYS_DRIVING

    private companion object {
        val NO_TRIP: StateFlow<TripContext?> = MutableStateFlow(null).asStateFlow()
        val ALWAYS_DRIVING: StateFlow<DetectionPhase> = MutableStateFlow(DetectionPhase.Driving).asStateFlow()
    }
}

/**
 * Production [DetectionRuntimeState]: a shared singleton the Coordinator foreground service
 * updates as its tracking job starts and ends, and the coordinator pushes the phase into. Held as a
 * single in DI so the service, the coordinator and the readiness use case share the same flags.
 * [DET-READY-001c]
 */
class MutableDetectionRuntimeState : DetectionRuntimeState, DetectionPhaseSink {
    private val _isRunning = MutableStateFlow(false)
    override val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _trip = MutableStateFlow<TripContext?>(null)
    override val trip: StateFlow<TripContext?> = _trip.asStateFlow()

    private val _phase = MutableStateFlow(DetectionPhase.Driving)
    override val phase: StateFlow<DetectionPhase> = _phase.asStateFlow()

    /** Called by the detection service when a tracking job starts (true) or ends (false). Ending a
     *  job also clears the trip context and resets the phase to [DetectionPhase.Driving]. */
    fun setRunning(running: Boolean) {
        _isRunning.value = running
        if (!running) {
            _trip.value = null
            _phase.value = DetectionPhase.Driving
        }
    }

    /** Called by the detection service when arming with a known trip origin (e.g. geofence-exit).
     *  Pass null for trips armed without a resolved origin (manual start). [DEPART-CONSISTENCY-001] */
    fun setTrip(trip: TripContext?) {
        _trip.value = trip
    }

    /** Called by the coordinator as its confirmation phase advances. [DET-PHASE-001] */
    override fun setPhase(phase: DetectionPhase) {
        _phase.value = phase
    }
}

/**
 * Fixed-value [DetectionRuntimeState] for tests and previews — reports a constant `running`, an
 * optional trip context and a fixed phase. [DET-READY-001c]
 */
class StaticDetectionRuntimeState(
    running: Boolean = false,
    trip: TripContext? = null,
    phase: DetectionPhase = DetectionPhase.Driving,
) : DetectionRuntimeState {
    override val isRunning: StateFlow<Boolean> = MutableStateFlow(running).asStateFlow()
    override val trip: StateFlow<TripContext?> = MutableStateFlow(trip).asStateFlow()
    override val phase: StateFlow<DetectionPhase> = MutableStateFlow(phase).asStateFlow()
}
