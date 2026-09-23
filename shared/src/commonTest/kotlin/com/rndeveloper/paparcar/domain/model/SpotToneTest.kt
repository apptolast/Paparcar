package com.rndeveloper.paparcar.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [SPOT-A-BOARDING-IS-NOT-A-DEPARTURE-001] The one resolver every surface paints a spot from.
 *
 * The test that matters is the first one: a releasing plaza is published seconds before it is
 * read, so on the age ramp it is the FRESHEST thing on the map — bright green — about the one
 * space that still has a car in it. If this falls, the map lies in the most expensive direction.
 */
class SpotToneTest {

    private fun spot(status: SpotStatus, reportedAtMs: Long) = Spot(
        id = "s",
        location = GpsPoint(40.0, -3.0, accuracy = 5f, timestamp = reportedAtMs, speed = 0f),
        reportedBy = "u",
        status = status,
    )

    @Test
    fun should_neverReadAsFresh_when_theCarIsStillInTheSpace() {
        val justPublished = spot(SpotStatus.RELEASING, reportedAtMs = 1_000L)

        assertEquals(SpotTone.RELEASING, justPublished.tone(nowMs = 1_000L))
        // …and it does not decay into the ramp as it ages, either: the claim outranks the clock
        // for as long as the document says RELEASING.
        assertEquals(SpotTone.RELEASING, justPublished.tone(nowMs = 1_000L + 45 * 60 * 1_000L))
    }

    @Test
    fun should_readTheAgeRamp_when_theSpaceIsActuallyFree() {
        val freedAt = 1_000L
        val free = spot(SpotStatus.CONFIRMED, reportedAtMs = freedAt)

        assertEquals(SpotTone.FRESH, free.tone(nowMs = freedAt))
        assertEquals(SpotTone.RECENT, free.tone(nowMs = freedAt + 20 * 60 * 1_000L))
        assertEquals(SpotTone.STALE, free.tone(nowMs = freedAt + 90 * 60 * 1_000L))
    }

    @Test
    fun should_stillReadTheRamp_when_theDepartureWasOnlyDeduced() {
        // PROVISIONAL is doubt about the DEPARTURE, told by its badge — not a different colour.
        // Folding it into the tone would have been the easy mistake here. [DET-HANDOFF-NOT-MANUAL-001 §B.3]
        val provisional = spot(SpotStatus.PROVISIONAL, reportedAtMs = 1_000L)

        assertEquals(SpotTone.FRESH, provisional.tone(nowMs = 1_000L))
    }

    @Test
    fun should_keepEveryRampTierMappable_when_crossingTheTwoTypes() {
        // The three-to-three half of the mapping, asked of the whole enum so a new freshness tier
        // cannot be added without deciding what it paints as.
        SpotFreshness.entries.forEach { tier ->
            assertEquals(tier.name, tier.asTone().name, "the ramp tier and its tone must stay paired")
        }
    }
}
