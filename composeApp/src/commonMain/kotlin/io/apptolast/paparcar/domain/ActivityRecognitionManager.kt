package io.apptolast.paparcar.domain

import io.apptolast.paparcar.domain.coordinator.ingestion.TraceEvent

/**
 * Activity Recognition registration port — two independent lanes. [DET-SOLID-001][DET-AR-FIRST-001]
 *
 * The legacy AR-proximity arming API (`registerVehicleEnterArming`) was purged. Today the single
 * `registerTransitions()` call installs BOTH lanes:
 *  - **Evidence lane** (always-on): IN_VEHICLE ENTER + EXIT to a plain broadcast receiver; EXIT
 *    is a non-decisive hint for a running Coordinator; ENTER stamps `DepartureEventBus` with the
 *    true transition time as departure evidence. [DET-G-01]
 *  - **Decision lane** [DET-AR-FIRST-001]: ENTER only, delivered via `getForegroundService`
 *    straight into the Coordinator service, which runs the pure arm ladder
 *    (`EvaluateArEnterArmUseCase`) — the event still only NOMINATES; arming requires the ladder's
 *    verdict, and a bus ride costs one notification flash.
 *
 * [IOS-F0-05] A third, PULL lane exists for wake-and-query reconstruction: [queryTransitions]
 * asks the PLATFORM for the transitions it recorded while the app was dead. Push platforms
 * (Android: live delivery is authoritative, nothing queryable is recorded) keep the default
 * empty answer; iOS derives transitions from `CMMotionActivityManager.queryActivityStarting`
 * historical samples (`docs/IOS-IMPLEMENTATION-PLAN.md` §2.3/§4).
 */
interface ActivityRecognitionManager {
    fun registerTransitions()
    fun unregisterTransitions()

    /**
     * AR transitions the platform recorded in `[fromMs, toMs]` (epoch ms, ascending order),
     * for composing a wake-and-query trace ([ActivityTransitionEvent.toTraceEvent]).
     *
     * Default: empty — the honest answer for platforms whose AR is push-only. An empty answer
     * means "no recorded history", never "no transitions happened": consumers must treat it as
     * absence of evidence (weaker prompts, not silent confirms), per the asymmetric-failure
     * doctrine.
     */
    suspend fun queryTransitions(fromMs: Long, toMs: Long): List<ActivityTransitionEvent> = emptyList()
}

/** An AR transition recorded by the platform, as returned by
 *  [ActivityRecognitionManager.queryTransitions]. [tMs] is the transition's epoch time as the
 *  platform recorded it — NOT the delivery/query time. */
data class ActivityTransitionEvent(
    val tMs: Long,
    val activity: TraceEvent.Activity,
)

/** The bridge into the wake-and-query trace: a recorded transition becomes a [TraceEvent.Kind.ACTIVITY]
 *  event, delivered by `DetectionTraceIngestion` in timestamp order with the rest of the trace. */
fun ActivityTransitionEvent.toTraceEvent(): TraceEvent =
    TraceEvent(tMs = tMs, kind = TraceEvent.Kind.ACTIVITY, activity = activity)
