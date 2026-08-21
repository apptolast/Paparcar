package io.apptolast.paparcar.data.mapper

import io.apptolast.paparcar.data.datasource.remote.dto.toDto
import io.apptolast.paparcar.data.datasource.remote.dto.typeName
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
}
