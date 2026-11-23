package com.rndeveloper.paparcar.domain.stats

import com.rndeveloper.paparcar.domain.model.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The rule the whole ticket hangs on: **a blur is only honest when there is something behind it**.
 * [MONEY-A-BLUR-IS-ONLY-HONEST-WHEN-THERE-IS-SOMETHING-BEHIND-IT-001]
 */
class LockedIntelligenceTest {

    private fun stats(
        places: List<ParkingCluster> = emptyList(),
        cost: Money? = null,
    ) = PeriodStats(
        parkings = 1,
        endedParkings = 1,
        distance = null,
        fuelLiters = null,
        energyKwh = null,
        costEstimate = cost,
        co2Kg = null,
        avgParkedDurationMs = null,
        peakHourBucket = null,
        mostActiveDayOfWeek = null,
        places = places,
        refuelsConfirmed = 0,
        spotsReleased = 0,
        autoDetectedCount = 0,
        detectionKnownCount = 0,
    )

    private fun cluster(name: String? = "Trabajo") = ParkingCluster(
        id = "c1",
        name = name,
        latitude = 40.4,
        longitude = -3.7,
        parkings = 4,
        avgStayMs = null,
        searchTime = null,
        spotsReleased = 0,
        lastParkedAtMs = 0L,
    )

    @Test
    fun `should_lockNothing_when_thePlanIsPlus`() {
        val locked = lockedIntelligence(
            isPlus = true,
            allTime = stats(places = listOf(cluster())),
            insightCount = 3,
            heatmapHasData = true,
            thisMonth = stats(cost = Money(12.0, "EUR")),
            closedMonth = stats(),
        )

        assertEquals(LockedIntelligence.NONE, locked, "someone who sees the cards has none locked")
    }

    /** ⛔ The half that keeps this from being advertising: nothing behind the glass, no glass. */
    @Test
    fun `should_lockNothing_when_thereIsNothingToShowBehindTheGlass`() {
        val locked = lockedIntelligence(
            isPlus = false,
            allTime = null,
            insightCount = 0,
            heatmapHasData = false,
            thisMonth = null,
            closedMonth = null,
        )

        assertEquals(LockedIntelligence.NONE, locked)
    }

    @Test
    fun `should_lockEachCard_when_itWouldHaveSomethingToSay`() {
        val locked = lockedIntelligence(
            isPlus = false,
            allTime = stats(places = listOf(cluster())),
            insightCount = 2,
            heatmapHasData = true,
            thisMonth = stats(cost = Money(9.5, "EUR")),
            closedMonth = stats(),
        )

        assertTrue(locked.recap)
        assertTrue(locked.insights)
        assertTrue(locked.places)
        assertTrue(locked.heatmap)
        assertTrue(locked.pace)
    }

    /** A car that parked but was never measured has no forecast withheld from it. */
    @Test
    fun `should_notLockThePace_when_theMonthHasNoMeasuredMagnitude`() {
        val locked = lockedIntelligence(
            isPlus = false,
            allTime = stats(places = listOf(cluster())),
            insightCount = 0,
            heatmapHasData = true,
            thisMonth = stats(),
            closedMonth = null,
        )

        assertFalse(locked.pace, "no spend, no distance and no search time is nothing to project")
        assertTrue(locked.places, "the places are still there")
    }

    /**
     * ⛔ The card lists NAMED places only and opens on its empty state otherwise, so an unnamed
     * cluster is a blur with nothing behind it. Same filter on both sides or the glass lies.
     * [UI-A-BLUR-MUST-LOOK-LIKE-THE-CARD-IT-HIDES-001]
     */
    @Test
    fun `should_notLockThePlaces_when_noClusterHasAName`() {
        val locked = lockedIntelligence(
            isPlus = false,
            allTime = stats(places = listOf(cluster(name = null))),
            insightCount = 0,
            heatmapHasData = false,
            thisMonth = null,
            closedMonth = null,
        )

        assertFalse(locked.places, "a cluster the app cannot name is not a place the card would show")
    }

    /** The places card is locked by its own content, never by the fact that a period exists. */
    @Test
    fun `should_notLockThePlaces_when_theHistoryHasNoCluster`() {
        val locked = lockedIntelligence(
            isPlus = false,
            allTime = stats(places = emptyList()),
            insightCount = 0,
            heatmapHasData = false,
            thisMonth = null,
            closedMonth = null,
        )

        assertFalse(locked.places)
    }
}
