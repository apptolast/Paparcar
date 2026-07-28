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
 * Coarse lifecycle of the Coordinator foreground service PROCESS — distinct from [isRunning], which
 * only says whether a tracking JOB is in flight. [DET-RESIDENT-FGS-001]
 *
 * Today the service is [Dead] between parkings and [Active] only during the 4–15 min of a tracking
 * session (wake-and-kill). The residency experiment adds [Sentry]: after a park, the service stays
 * alive with the GPS OFF, so a subsequent departure trigger (geofence EXIT / AR ENTER / significant
 * motion) lands on a LIVE foreground process instead of having to resurrect a dead one — the class
 * of false-negative that OEM/Doze process-kills + the Android 12+ background-FGS-start restriction
 * produce today. Gated behind an internal flag (default OFF) for F1.
 */
enum class ServicePresence {
    /** No process, no FGS, no notification — the service is not running at all. */
    Dead,

    /** Alive and foreground but idle: GPS off, no tracking job — waiting to catch the next departure. */
    Sentry,

    /** A tracking job is in flight (GPS on, following a trip). */
    Active,
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

    /** Coarse lifecycle of the service process itself. Defaults to [ServicePresence.Dead] so doubles
     *  that don't exercise it need no change. [DET-RESIDENT-FGS-001] */
    val presence: StateFlow<ServicePresence> get() = ALWAYS_DEAD

    /** The current trip's origin (departing vehicle + spot), or null when idle / origin unknown.
     *  Default no-op so test/preview doubles that don't exercise it need no change. [DEPART-CONSISTENCY-001] */
    val trip: StateFlow<TripContext?> get() = NO_TRIP

    /** The current trip's coarse phase. Defaults to [DetectionPhase.Driving] so doubles that don't
     *  exercise it need no change. [DET-PHASE-001] */
    val phase: StateFlow<DetectionPhase> get() = ALWAYS_DRIVING

    private companion object {
        val NO_TRIP: StateFlow<TripContext?> = MutableStateFlow(null).asStateFlow()
        val ALWAYS_DRIVING: StateFlow<DetectionPhase> = MutableStateFlow(DetectionPhase.Driving).asStateFlow()
        val ALWAYS_DEAD: StateFlow<ServicePresence> = MutableStateFlow(ServicePresence.Dead).asStateFlow()
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

    private val _presence = MutableStateFlow(ServicePresence.Dead)
    override val presence: StateFlow<ServicePresence> = _presence.asStateFlow()

    /** Called by the detection service when a tracking job starts (true) or ends (false). Every
     *  trip begins in-motion, so both edges reset the phase to [DetectionPhase.Driving] (a fresh trip
     *  never inherits a leftover "Candidate" from a prior one); ending also clears the trip context. */
    fun setRunning(running: Boolean) {
        _isRunning.value = running
        _phase.value = DetectionPhase.Driving
        if (!running) {
            _trip.value = null
        }
    }

    /** Called by the detection service at its three lifecycle edges: [ServicePresence.Active] when a
     *  tracking job launches, [ServicePresence.Sentry] when it degrades to the resident idle watcher,
     *  [ServicePresence.Dead] when the service tears down. Independent of [setRunning] so entering
     *  Sentry (job ended, process alive) is expressible. [DET-RESIDENT-FGS-001] */
    fun setPresence(presence: ServicePresence) {
        _presence.value = presence
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
    presence: ServicePresence = ServicePresence.Dead,
) : DetectionRuntimeState {
    override val isRunning: StateFlow<Boolean> = MutableStateFlow(running).asStateFlow()
    override val trip: StateFlow<TripContext?> = MutableStateFlow(trip).asStateFlow()
    override val phase: StateFlow<DetectionPhase> = MutableStateFlow(phase).asStateFlow()
    override val presence: StateFlow<ServicePresence> = MutableStateFlow(presence).asStateFlow()
}
