package io.apptolast.paparcar.data.mapper

import io.apptolast.paparcar.data.datasource.remote.dto.toDto
import io.apptolast.paparcar.data.datasource.remote.dto.typeName
import io.apptolast.paparcar.domain.detection.HoldAction
import io.apptolast.paparcar.domain.detection.sentry.TriggerDisposition
import io.apptolast.paparcar.domain.diagnostics.DetectionEvent
import io.apptolast.paparcar.domain.model.GpsPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001 §C] Serializer parity for the AR evidence lane. The
 * exhaustive `when` in `toDto()` makes a NEW variant a compile error, but it cannot catch a new
 * FIELD that nobody mapped — and an unmapped field is a silently-null Firestore column that
 * corrupts every replay fixture generated from it (see MEMORY: "DTO field parity").
 */
class DetectionEventDtoTest {

    private val fix = GpsPoint(latitude = 36.6, longitude = -6.2, accuracy = 5f, timestamp = 10L, speed = 1f)

    @Test
    fun should_carryTheArStaleness_when_mappingACyclingStamp() {
        val dto = DetectionEvent.ActivityTransition(
            sessionId = "s-1",
            timestampMs = 1_000L,
            activity = "ON_BICYCLE",
            transition = "ENTER",
            location = fix,
            trueTimeAgeMs = 90_000L,
        ).toDto()

        assertEquals("ACTIVITY_TRANSITION", dto.type)
        assertEquals("ON_BICYCLE", dto.activity)
        assertEquals("ENTER", dto.transition)
        assertEquals(90_000L, dto.enterAgeMs, "the staleness rides the existing enterAgeMs column")
        assertEquals(36.6, dto.lat)
        assertEquals(1f, dto.speed)
    }

    @Test
    fun should_leaveTheStalenessNull_when_theTransitionCarriesNone() {
        // The vehicle EXIT edge is noticed from the fix stream, not from a timestamped AR payload.
        val dto = DetectionEvent.ActivityTransition(
            sessionId = "s-1",
            timestampMs = 1_000L,
            activity = "IN_VEHICLE",
            transition = "EXIT",
            location = fix,
        ).toDto()

        assertNull(dto.enterAgeMs)
        assertEquals("IN_VEHICLE", dto.activity)
    }

    @Test
    fun should_keepTheWireDiscriminatorStable_when_namingTheEventTypes() {
        // The trace tooling (`tools/trace2fixture`) reads these strings; renaming one silently
        // orphans every fixture generated before the rename.
        assertEquals(
            "ACTIVITY_TRANSITION",
            DetectionEvent.ActivityTransition("s", 0L, "ON_BICYCLE", "ENTER").typeName(),
        )
    }

    @Test
    fun should_carryThePromptCause_when_mappingADegradedConfirm() {
        // [DET-PROMPT-STATES-ITS-REASON-001] The point of the ticket: the cause has to survive
        // serialization, because the remote trace is the only place it can be read after the fact —
        // logcat rotates and nobody drives cabled to a PC.
        val dto = DetectionEvent.Decision(
            sessionId = "s-1",
            timestampMs = 1_000L,
            outcome = "CONFIRM_DEGRADED_PROMPT",
            pathLabel = "steps+egress",
            location = fix,
            reason = "human_powered",
        ).toDto()

        assertEquals("DECISION", dto.type)
        assertEquals(
            "CONFIRM_DEGRADED_PROMPT",
            dto.outcome,
            "the outcome string every saved trace quotes must NOT change",
        )
        assertEquals("steps+egress", dto.pathLabel, "the path axis stays independent of the cause")
        assertEquals("human_powered", dto.reason, "the cause rides the existing reason column")
    }

    @Test
    fun should_leaveTheReasonNull_when_theDecisionHasNoCauseToName() {
        // Confirms, rejections and the band-crossing marker carry no cause: the column stays empty
        // rather than inventing one.
        val dto = DetectionEvent.Decision(
            sessionId = "s-1",
            timestampMs = 1_000L,
            outcome = "MOTOR_WITNESSED",
            pathLabel = "motorBand=628700ms",
        ).toDto()

        assertNull(dto.reason)
    }

    // ── Trigger lane [DET-EVERY-TRIGGER-LEAVES-A-TRACE-001] ────────────────

    @Test
    fun should_carryLaneVerdictAndReason_when_mappingATriggerDisposition() {
        val dto = DetectionEvent.Trigger(
            sessionId = "triggers_20687",
            timestampMs = 1_787_349_600_000L,
            trigger = "GEOFENCE_EXIT",
            disposition = TriggerDisposition.REFUSED_STRATEGY,
            detail = "owner=BLUETOOTH",
            geofenceId = "a786c135",
        ).toDto()

        assertEquals("TRIGGER", dto.type)
        assertEquals("GEOFENCE_EXIT", dto.event, "the lane rides the event column")
        assertEquals("REFUSED_STRATEGY", dto.outcome, "the verdict rides the outcome column")
        assertEquals("owner=BLUETOOTH", dto.reason, "the why rides the reason column")
        assertEquals("a786c135", dto.geofenceId)
    }

    /**
     * A disposition that reached Firestore as a null outcome would be a row nobody can group —
     * the exact failure this ticket exists to remove, reintroduced at the wire.
     */
    @Test
    fun should_neverSerializeANullVerdict_when_anyDispositionIsUsed() {
        for (d in TriggerDisposition.entries) {
            val dto = DetectionEvent.Trigger(
                sessionId = "triggers_0",
                timestampMs = 0L,
                trigger = "AR_TRANSITION",
                disposition = d,
            ).toDto()
            assertEquals(d.name, dto.outcome, d.name + " lost its verdict on the wire")
        }
    }

    @Test
    fun should_leaveNeighbourColumnsEmpty_when_aTriggerOwnsNoFix() {
        val event = DetectionEvent.Trigger(
            sessionId = "triggers_0",
            timestampMs = 0L,
            trigger = "SENTRY_WAKE",
            disposition = TriggerDisposition.ARMED,
        )
        assertEquals("TRIGGER", event.typeName())
        assertNull(event.toDto().lat)
        assertNull(event.toDto().confidence)
    }

    // ── Hold lane [DET-HOLD-BRANCHES-MUST-SPEAK-001] ─────────────────────

    @Test
    fun should_carryActionAndDuration_when_mappingAHoldExit() {
        val dto = DetectionEvent.Hold(
            sessionId = "s-1",
            timestampMs = 2_000L,
            action = HoldAction.DISCARDED_DROVE_OFF,
            heldMs = 47_000L,
            pathLabel = "steps+egress",
            location = fix,
        ).toDto()

        assertEquals("HOLD", dto.type)
        assertEquals("DISCARDED_DROVE_OFF", dto.action, "the exit rides the action column")
        assertEquals("steps+egress", dto.pathLabel)
        assertEquals(47_000L, dto.sessionAgeMs, "the duration rides the how-old column")
    }

    /**
     * An exit that serialized to a null action would be a hold whose fate is unreadable — which is
     * the state this ticket exists to leave behind, reintroduced at the wire.
     */
    @Test
    fun should_neverSerializeANullAction_when_anyHoldExitIsUsed() {
        for (a in HoldAction.entries) {
            val dto = DetectionEvent.Hold(
                sessionId = "s-1",
                timestampMs = 0L,
                action = a,
            ).toDto()
            assertEquals(a.name, dto.action, a.name + " lost its action on the wire")
        }
    }
}
