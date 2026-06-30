package io.apptolast.paparcar.domain.usecase.detection

import io.apptolast.paparcar.domain.detection.ParkingStrategy
import io.apptolast.paparcar.domain.model.DetectionReadiness
import io.apptolast.paparcar.domain.model.DisabledReason
import io.apptolast.paparcar.domain.usecase.detection.EvaluateFirstParkNudgeUseCase.Companion.COOLDOWN_MILLIS
import io.apptolast.paparcar.domain.usecase.detection.EvaluateFirstParkNudgeUseCase.Companion.MAX_NUDGES
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EvaluateFirstParkNudgeUseCaseTest {

    private val coldStart = DetectionReadiness.Ready(ParkingStrategy.COORDINATOR)
    private val now = COOLDOWN_MILLIS * 100 // comfortably past any cooldown from t=0

    @Test
    fun should_nudge_when_coldStart_neverParked_underCap_pastCooldown() {
        assertTrue(
            shouldSendFirstParkNudge(
                readiness = coldStart,
                hasConfirmedFirstPark = false,
                nudgeCount = 0,
                lastNudgeAtMillis = 0L,
                nowMillis = now,
            ),
        )
    }

    @Test
    fun should_notNudge_when_bluetoothStrategy() {
        // Bluetooth is fully automatic — never the cold-start case.
        assertFalse(
            shouldSendFirstParkNudge(
                readiness = DetectionReadiness.Ready(ParkingStrategy.BLUETOOTH),
                hasConfirmedFirstPark = false,
                nudgeCount = 0,
                lastNudgeAtMillis = 0L,
                nowMillis = now,
            ),
        )
    }

    @Test
    fun should_notNudge_when_notReady() {
        listOf(
            DetectionReadiness.Disabled(DisabledReason.TURNED_OFF),
            DetectionReadiness.Disabled(DisabledReason.NO_VEHICLE),
            DetectionReadiness.Blocked(emptySet()),
            DetectionReadiness.Monitoring(ParkingStrategy.COORDINATOR),
        ).forEach { readiness ->
            assertFalse(
                shouldSendFirstParkNudge(readiness, false, 0, 0L, now),
                "expected no nudge for $readiness",
            )
        }
    }

    @Test
    fun should_notNudge_when_alreadyConfirmedAPark() {
        assertFalse(
            shouldSendFirstParkNudge(coldStart, hasConfirmedFirstPark = true, nudgeCount = 0, lastNudgeAtMillis = 0L, nowMillis = now),
        )
    }

    @Test
    fun should_notNudge_when_capReached() {
        assertFalse(
            shouldSendFirstParkNudge(coldStart, hasConfirmedFirstPark = false, nudgeCount = MAX_NUDGES, lastNudgeAtMillis = 0L, nowMillis = now),
        )
    }

    @Test
    fun should_notNudge_when_withinCooldown() {
        val lastNudge = now - (COOLDOWN_MILLIS / 2) // half a cooldown ago
        assertFalse(
            shouldSendFirstParkNudge(coldStart, hasConfirmedFirstPark = false, nudgeCount = 1, lastNudgeAtMillis = lastNudge, nowMillis = now),
        )
    }

    @Test
    fun should_nudge_when_pastCooldown_underCap() {
        val lastNudge = now - COOLDOWN_MILLIS - 1 // just past the cooldown
        assertTrue(
            shouldSendFirstParkNudge(coldStart, hasConfirmedFirstPark = false, nudgeCount = 1, lastNudgeAtMillis = lastNudge, nowMillis = now),
        )
    }
}
