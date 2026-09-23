package com.rndeveloper.paparcar.domain.usecase.spot

import com.rndeveloper.paparcar.domain.detection.PendingSpotRelease
import com.rndeveloper.paparcar.domain.model.SpotTtlPolicy
import com.rndeveloper.paparcar.fakes.FakeAppPreferences
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

    private fun build(
        pending: PendingSpotRelease? = PendingSpotRelease("spot-1", "veh-A", nominatedAt),
        nowMs: Long = nominatedAt + 60_000L,
    ): Triple<WithdrawSpotReleaseUseCase, FakeSpotRepository, FakeAppPreferences> {
        val spots = FakeSpotRepository()
        val prefs = FakeAppPreferences().apply { storedSpotRelease = pending }
        return Triple(WithdrawSpotReleaseUseCase(spots, prefs) { nowMs }, spots, prefs)
    }

    @Test
    fun should_withdrawTheSpot_when_theBoardingNeverBecameADeparture() = runTest {
        val (useCase, spots, prefs) = build()

        assertTrue(useCase())

        assertEquals(listOf("spot-1"), spots.retractedSpotIds)
        // ⛔ The stamp SURVIVES the withdrawal, and that is the fix for the Bluetooth drop: the
        // document is gone but the parking session has still spent its one nomination, so a link
        // that comes back must not re-announce the same standing car. Only the departure retires
        // it. [SPOT-A-BOARDING-IS-NOT-A-DEPARTURE-001]
        assertNotNull(prefs.storedSpotRelease, "a session that nominated once must stay spent")
    }

    @Test
    fun should_doNothing_when_nothingWasEverNominated() = runTest {
        val (useCase, spots, _) = build(pending = null)

        assertFalse(useCase())
        assertTrue(spots.retractedSpotIds.isEmpty())
    }

    @Test
    fun should_notWriteIntoTheVoid_when_theReleasingWindowAlreadyRanOut() = runTest {
        // [DET-RETRACT-DENIED-FOREVER-001] Past the TTL the expiry already swept the document, and
        // `retractSpot` is an UPDATE: Firestore answers PERMISSION_DENIED, not NOT_FOUND. Measured
        // once as 256 denials in five days for a spot that had never been published.
        val (useCase, spots, prefs) = build(
            nowMs = nominatedAt + SpotTtlPolicy.RELEASING_SPOT_TTL_MS + 1,
        )

        assertFalse(useCase())

        assertTrue(spots.retractedSpotIds.isEmpty(), "withdrew: ${spots.retractedSpotIds}")
    }
}
