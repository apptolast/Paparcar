package com.rndeveloper.paparcar.domain.detection.coordinator.ingestion

import com.rndeveloper.paparcar.domain.ActivityTransitionEvent
import com.rndeveloper.paparcar.domain.detection.ArmEvidence
import com.rndeveloper.paparcar.domain.model.GpsPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [IOS-F2-A-WAKE-MUST-QUERY-THE-PAST-001] The §4 step-5 composition: what a wake batch may and
 *  may not contain. Conservative by doctrine — every rule here fails toward "less evidence". */
class WakeTraceCompositionTest {

    private val armedAt = 1_000_000L
    private val now = 1_600_000L

    private fun enter(t: Long) = ActivityTransitionEvent(t, TraceEvent.Activity.VEHICLE_ENTER)
    private fun exit(t: Long) = ActivityTransitionEvent(t, TraceEvent.Activity.VEHICLE_EXIT)
    private fun fix(t: Long) = GpsPoint(40.4, -3.7, 8f, t, 0f)

    @Test
    fun should_orderEventsByTime_when_historyFixAndStepsCombine() {
        val trace = composeWakeTrace(
            armedAtMs = armedAt,
            nowMs = now,
            transitions = listOf(enter(1_100_000L), exit(1_300_000L)),
            wakeFix = fix(1_500_000L),
            stepsSinceLastVehicleExit = 3L,
        )
        assertEquals(trace.sortedBy { it.tMs }, trace)
        assertEquals(2, trace.count { it.kind == TraceEvent.Kind.ACTIVITY })
        assertEquals(1, trace.count { it.kind == TraceEvent.Kind.FIX })
        assertEquals(3, trace.count { it.kind == TraceEvent.Kind.STEP })
        assertTrue(trace.filter { it.kind == TraceEvent.Kind.STEP }.all { it.tMs > 1_300_000L })
    }

    @Test
    fun should_dropTransitionsOutsideTheArmWindow_when_thePlatformRecordPredatesTheArm() {
        // A stale ride recorded before the arm must not seed the session with someone else's trip.
        val trace = composeWakeTrace(
            armedAtMs = armedAt,
            nowMs = now,
            transitions = listOf(enter(armedAt - 60_000L), exit(1_200_000L)),
            wakeFix = null,
            stepsSinceLastVehicleExit = null,
        )
        assertEquals(1, trace.size)
        assertEquals(TraceEvent.Activity.VEHICLE_EXIT, trace.single().activity)
    }

    @Test
    fun should_dropAWakeFixOlderThanTheArm_when_theCacheServedAPreTripFix() {
        val trace = composeWakeTrace(
            armedAtMs = armedAt,
            nowMs = now,
            transitions = emptyList(),
            wakeFix = fix(armedAt - 1L),
            stepsSinceLastVehicleExit = null,
        )
        assertTrue(trace.isEmpty())
    }

    @Test
    fun should_refuseStepsWithoutAnEgressAnchor_when_noVehicleExitWasRecorded() {
        // A step count with no egress moment has no origin — mis-anchored budgets are how walks
        // read as rides. [DET-STEP-BUDGET-ORIGIN-001]
        val trace = composeWakeTrace(
            armedAtMs = armedAt,
            nowMs = now,
            transitions = listOf(enter(1_100_000L)),
            wakeFix = fix(1_200_000L),
            stepsSinceLastVehicleExit = 500L,
        )
        assertEquals(0, trace.count { it.kind == TraceEvent.Kind.STEP })
    }

    @Test
    fun should_capStepEvents_when_theWalkWasLong() {
        val trace = composeWakeTrace(
            armedAtMs = armedAt,
            nowMs = now,
            transitions = listOf(exit(1_200_000L)),
            wakeFix = null,
            stepsSinceLastVehicleExit = 10_000L,
        )
        assertEquals(WAKE_TRACE_MAX_STEP_EVENTS, trace.count { it.kind == TraceEvent.Kind.STEP })
        assertTrue(trace.last().tMs <= now)
    }

    @Test
    fun should_returnEmpty_when_nothingIsReconstructable() {
        // The caller clears the arm and stays silent — a bare wake never invents a session.
        assertTrue(
            composeWakeTrace(armedAt, now, emptyList(), wakeFix = null, stepsSinceLastVehicleExit = null)
                .isEmpty(),
        )
    }

    @Test
    fun should_degradeAutomaticTriggersToUnverified_when_reArmingAReconstructedSession() {
        // Everything measured died with the process; only intent survives it.
        assertEquals(ArmEvidence.Manual, reconstructedArmEvidence("MANUAL"))
        assertEquals(ArmEvidence.ArrivalHandoff, reconstructedArmEvidence("ARRIVAL_HANDOFF"))
        assertEquals(ArmEvidence.Unverified, reconstructedArmEvidence("GEOFENCE_EXIT"))
        assertEquals(ArmEvidence.Unverified, reconstructedArmEvidence("AR_VEHICLE_ENTER"))
        assertEquals(ArmEvidence.Unverified, reconstructedArmEvidence("SIGNIFICANT_MOTION"))
        assertEquals(ArmEvidence.Unverified, reconstructedArmEvidence("garbage"))
    }
}
