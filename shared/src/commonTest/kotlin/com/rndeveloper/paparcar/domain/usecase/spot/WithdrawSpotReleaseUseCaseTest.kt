package com.rndeveloper.paparcar.domain.usecase.spot

import com.rndeveloper.paparcar.domain.detection.PendingSpotRelease
import com.rndeveloper.paparcar.domain.detection.SpotReleaseAction
import com.rndeveloper.paparcar.domain.detection.SpotReleaseLane
import com.rndeveloper.paparcar.domain.diagnostics.DetectionEvent
import com.rndeveloper.paparcar.domain.model.SpotTtlPolicy
import com.rndeveloper.paparcar.fakes.FakeAppPreferences
import com.rndeveloper.paparcar.fakes.FakeDetectionEventLogger
import com.rndeveloper.paparcar.fakes.FakeSpotRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** [SPOT-A-BOARDING-IS-NOT-A-DEPARTURE-001] Taking back a plaza whose boarding never became a trip. */
class WithdrawSpotReleaseUseCaseTest {

    private val nominatedAt = 100_000L

    private class Rig(
        val useCase: WithdrawSpotReleaseUseCase,
        val spots: FakeSpotRepository,
        val prefs: FakeAppPreferences,
        val logger: FakeDetectionEventLogger,
    ) {
        /** The lane's events for the nominated plaza, which is what a field diagnosis reads. */
        fun released(): List<DetectionEvent.SpotRelease> =
            logger.events.filterIsInstance<DetectionEvent.SpotRelease>()
    }

    private fun build(
        pending: PendingSpotRelease? = PendingSpotRelease("spot-1", "veh-A", nominatedAt, SpotReleaseLane.BT),
        nowMs: Long = nominatedAt + 60_000L,
        retractResult: Result<Unit> = Result.success(Unit),
    ): Rig {
        val spots = FakeSpotRepository().apply { this.retractResult = retractResult }
        val prefs = FakeAppPreferences().apply { storedSpotRelease = pending }
        val logger = FakeDetectionEventLogger()
        return Rig(WithdrawSpotReleaseUseCase(spots, prefs, logger) { nowMs }, spots, prefs, logger)
    }

    @Test
    fun should_withdrawTheSpot_when_theBoardingNeverBecameADeparture() = runTest {
        val rig = build()

        assertTrue(rig.useCase(SpotReleaseLane.BT))

        assertEquals(listOf("spot-1"), rig.spots.retractedSpotIds)
        // ⛔ The stamp SURVIVES the withdrawal, and that is the fix for the Bluetooth drop: the
        // document is gone but the parking session has still spent its one nomination, so a link
        // that comes back must not re-announce the same standing car. Only the departure retires
        // it. [SPOT-A-BOARDING-IS-NOT-A-DEPARTURE-001]
        assertNotNull(rig.prefs.storedSpotRelease, "a session that nominated once must stay spent")
    }

    @Test
    fun should_nameTheLaneThatTookItDown_when_aNominationIsWithdrawn() = runTest {
        // [DET-A-RELEASING-SPOT-MUST-SAY-IT-WENT-OUT-001] The whole point. The two lanes are driven
        // by different jobs, so a withdrawal with no author cannot be told from one lane taking
        // down the other's nomination — which is the open question of the 27-09-2026 field test.
        val rig = build()

        rig.useCase(SpotReleaseLane.BT)

        val event = rig.released().single()
        assertEquals(SpotReleaseAction.WITHDRAWN, event.action)
        assertEquals(SpotReleaseLane.BT, event.lane)
        assertEquals("spot-1", event.sessionId, "the plaza's id IS the parking session's")
        assertEquals(60_000L, event.liveMs, "how long the phantom was out is the field's question")
    }

    @Test
    fun should_notClaimAWithdrawal_when_theWireRefusedIt() = runTest {
        // A trace saying WITHDRAWN on a retraction the rules denied sends the next diagnosis looking
        // for a phantom it has been told is gone. The plaza is still standing here.
        val rig = build(retractResult = Result.failure(IllegalStateException("PERMISSION_DENIED")))

        assertFalse(rig.useCase(SpotReleaseLane.BT))

        val event = rig.released().single()
        assertEquals(SpotReleaseAction.WITHDRAW_FAILED, event.action)
        assertEquals("PERMISSION_DENIED", event.reason)
    }

    @Test
    fun should_doNothing_when_nothingWasEverNominated() = runTest {
        val rig = build(pending = null)

        assertFalse(rig.useCase(SpotReleaseLane.BT))
        assertTrue(rig.spots.retractedSpotIds.isEmpty())
        // Not an event either: every coordinator job end calls this and most have nothing to say.
        // Logging those would bury the boardings that DID happen under the ones that never were.
        assertTrue(rig.released().isEmpty(), "recorded: ${rig.released()}")
    }

    @Test
    fun should_notTouchIt_when_theNominationBelongsToTheOtherLane() = runTest {
        // [DET-A-WITHDRAWAL-MUST-NAME-WHAT-IT-TAKES-BACK-001] El caso que existia y nadie impedia:
        // un job del Coordinator que seguia vivo cuando llego el CONNECT (el CONNECT cancela
        // `btHuntJob`, nunca `detectionJob`) terminaba 18-36 s despues y se llevaba por delante la
        // plaza del carril BT — y, como el sello sobrevive a las retiradas, la unica nominacion de
        // la sesion con ella.
        val rig = build()

        assertFalse(rig.useCase(SpotReleaseLane.COORDINATOR))

        assertTrue(rig.spots.retractedSpotIds.isEmpty(), "retiro: ${rig.spots.retractedSpotIds}")
        // La plaza sigue publicada, con su dueno todavia vigilandola: la promocion aun puede llegar.
        assertNotNull(rig.prefs.storedSpotRelease, "el sello ajeno no se toca")
        val event = rig.released().single()
        assertEquals(SpotReleaseAction.SKIPPED, event.action)
        assertEquals("not_my_nomination", event.reason)
        assertEquals(SpotReleaseLane.COORDINATOR, event.lane, "quien lo intento, no quien la puso")
    }

    @Test
    fun should_letAnyLaneWithdrawIt_when_theStampPredatesLanes() = runTest {
        // Un sello escrito por el build anterior no lleva carril. Negarselo a todos dejaria de pie,
        // justo despues del update, un fantasma que nada podria tumbar salvo la TTL.
        val rig = build(pending = PendingSpotRelease("spot-1", "veh-A", nominatedAt, SpotReleaseLane.LEGACY))

        assertTrue(rig.useCase(SpotReleaseLane.COORDINATOR))

        assertEquals(listOf("spot-1"), rig.spots.retractedSpotIds)
    }

    @Test
    fun should_notWriteIntoTheVoid_when_theReleasingWindowAlreadyRanOut() = runTest {
        // [DET-RETRACT-DENIED-FOREVER-001] Past the TTL the expiry already swept the document, and
        // `retractSpot` is an UPDATE: Firestore answers PERMISSION_DENIED, not NOT_FOUND. Measured
        // once as 256 denials in five days for a spot that had never been published.
        val rig = build(nowMs = nominatedAt + SpotTtlPolicy.RELEASING_SPOT_TTL_MS + 1)

        assertFalse(rig.useCase(SpotReleaseLane.BT))

        assertTrue(rig.spots.retractedSpotIds.isEmpty(), "withdrew: ${rig.spots.retractedSpotIds}")
        // Told apart from a real withdrawal: the spot expired on its own and nothing reached the
        // wire. Reporting it as a retraction would hide the denial storm the branch exists to stop.
        assertEquals(SpotReleaseAction.WITHDRAW_EXPIRED, rig.released().single().action)
    }
}
