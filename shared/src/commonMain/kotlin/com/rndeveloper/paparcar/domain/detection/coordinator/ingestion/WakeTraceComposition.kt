package com.rndeveloper.paparcar.domain.detection.coordinator.ingestion

import com.rndeveloper.paparcar.domain.ActivityTransitionEvent
import com.rndeveloper.paparcar.domain.detection.ArmEvidence
import com.rndeveloper.paparcar.domain.detection.DetectionTrigger
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.sensor.StepDetectorSource
import com.rndeveloper.paparcar.domain.toTraceEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Composes the batch a wake feeds into [DetectionTraceIngestion]: the recorded AR history, the
 * wake's own fix, and the post-egress step budget — the §4 reconstruction protocol's step 5.
 * [IOS-F2-A-WAKE-MUST-QUERY-THE-PAST-001]
 *
 * Pure and deliberately conservative, per the asymmetric-failure doctrine:
 * - Transitions outside `[armedAtMs, nowMs]` are clamped away — a stale platform record predating
 *   the arm must not seed the session with someone else's ride.
 * - A wake fix older than the arm is dropped (a cached fix from before the trip says nothing
 *   about it).
 * - Steps are emitted ONLY when a VEHICLE_EXIT exists to anchor them: a step count with no egress
 *   moment has no origin, and a mis-anchored budget is how walks read as rides. They are spread
 *   evenly strictly after the last exit, capped at [maxStepEvents] (the evaluators care about
 *   "enough egress steps", not the exact thousand).
 *
 * An empty answer means "nothing reconstructable" — the caller clears the pending arm and stays
 * silent; it never invents a session out of a bare wake.
 */
fun composeWakeTrace(
    armedAtMs: Long,
    nowMs: Long,
    transitions: List<ActivityTransitionEvent>,
    wakeFix: GpsPoint?,
    stepsSinceLastVehicleExit: Long?,
    maxStepEvents: Int = WAKE_TRACE_MAX_STEP_EVENTS,
): List<TraceEvent> {
    val activityEvents = transitions
        .filter { it.tMs in armedAtMs..nowMs }
        .sortedBy { it.tMs }
        .map { it.toTraceEvent() }

    val fixEvent = wakeFix
        ?.takeIf { it.timestamp in armedAtMs..nowMs }
        ?.let {
            TraceEvent(
                tMs = it.timestamp,
                kind = TraceEvent.Kind.FIX,
                lat = it.latitude,
                lon = it.longitude,
                accuracy = it.accuracy,
                speed = it.speed,
            )
        }

    val lastExitMs = activityEvents
        .lastOrNull { it.activity == TraceEvent.Activity.VEHICLE_EXIT }
        ?.tMs
    val stepEvents = if (lastExitMs != null && (stepsSinceLastVehicleExit ?: 0L) > 0L) {
        val count = minOf(stepsSinceLastVehicleExit!!, maxStepEvents.toLong()).toInt()
        val span = (nowMs - lastExitMs).coerceAtLeast(count.toLong())
        (1..count).map { i ->
            TraceEvent(tMs = lastExitMs + (span * i) / count, kind = TraceEvent.Kind.STEP)
        }
    } else {
        emptyList()
    }

    return (activityEvents + listOfNotNull(fixEvent) + stepEvents).sortedBy { it.tMs }
}

/**
 * The evidence a RECONSTRUCTED arm re-enters with, from the trigger name the pending record
 * persisted. Everything measured died with the process, so every automatic trigger degrades to
 * [ArmEvidence.Unverified] — every anti-walking guard stays active and only what the trace itself
 * proves can confirm. The two intent-bearing triggers keep their meaning: MANUAL is the user's
 * word (it survives process death like it survives everything else), and ARRIVAL_HANDOFF keeps
 * its own weak label so a reconstructed handoff can never masquerade as intent.
 * [DET-HANDOFF-NOT-MANUAL-001][DET-FAIL-CLOSED-BY-CONSTRUCTION-001]
 */
fun reconstructedArmEvidence(persistedTrigger: String): ArmEvidence = when (persistedTrigger) {
    DetectionTrigger.MANUAL.name -> ArmEvidence.Manual
    DetectionTrigger.ARRIVAL_HANDOFF.name -> ArmEvidence.ArrivalHandoff
    else -> ArmEvidence.Unverified
}

/** A [StepDetectorSource] a replay can drive — the reconstruction coordinator's step feed, since
 *  the live pedometer source cannot replay history. */
class ReplayStepSource : StepDetectorSource {
    private val emissions = MutableSharedFlow<Unit>(extraBufferCapacity = REPLAY_STEP_BUFFER)
    override fun steps(): Flow<Unit> = emissions
    fun emit() {
        emissions.tryEmit(Unit)
    }
}

/** Step events are a budget signal, not an odometer: past this many the evaluators' thresholds
 *  are long since answered, and a 10 000-event replay would only slow the wake. */
const val WAKE_TRACE_MAX_STEP_EVENTS = 120
private const val REPLAY_STEP_BUFFER = 256
