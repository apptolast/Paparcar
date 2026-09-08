package com.rndeveloper.paparcar.presentation.vehicles

import com.rndeveloper.paparcar.domain.model.AddressInfo
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.UserParking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * One surface, one scope — the rule the ficha layout was rebuilt around. [STATS-UI-001]
 *
 * The screen used to carry three different time scopes without naming any of them: the identity
 * card counted the car's whole life, the Activity card counted the selected period, and the three
 * "facts" inside that same Activity card were computed over the whole visible history — so moving
 * the filter left them frozen. These tests pin the resolution, which is where all of it is decided.
 */
class HistoryTimelineScopeTest {

    private val nowMs = 1_790_000_000_000L
    private val dayMs = 86_400_000L

    private fun session(
        ageDays: Int,
        street: String? = null,
        publishedSpot: Boolean = false,
        active: Boolean = false,
    ) = sessionAt(nowMs - ageDays * dayMs, street, publishedSpot, active)

    private fun sessionAt(
        atMs: Long,
        street: String? = null,
        publishedSpot: Boolean = false,
        active: Boolean = false,
    ) = UserParking(
        id = "s-$atMs-$street-$publishedSpot",
        location = GpsPoint(40.4, -3.7, 8f, atMs, 0f),
        isActive = active,
        endedAtMs = if (active) null else atMs + 2 * 3_600_000L,
        publishedSpot = publishedSpot,
        address = street?.let { AddressInfo(street = it, city = null, region = null, country = null) },
    )

    @Test
    fun `should_rescopeTheFigures_when_theFilterChanges`() {
        // THE bug this ticket exists for: the usual street was computed over everything visible, so
        // it read the same under "this week" as under "all time". Now the figures follow the filter.
        // Anchored to the window the calculator actually decides, not to a guess about it: `nowMs`
        // happens to be a Monday, so "days ago" would have put two of these three in LAST week and
        // the test would have been measuring the calendar instead of the rescoping.
        val weekStart = VehicleHistoryCalculator.windowStartMs(HistoryFilter.ThisWeek, nowMs)!!
        val sessions = listOf(
            // This week: Gran Vía three times, enough to be a habit.
            sessionAt(weekStart + 3_600_000L, street = "Gran Vía"),
            sessionAt(weekStart + 2 * 3_600_000L, street = "Gran Vía"),
            sessionAt(weekStart + 3 * 3_600_000L, street = "Gran Vía"),
            // Older: Alcalá four times — the winner over the whole history.
            session(40, street = "Alcalá"), session(41, street = "Alcalá"),
            session(42, street = "Alcalá"), session(43, street = "Alcalá"),
        )

        val allTime = HistoryTimeline.resolve(sessions, HistoryFilter.All, isPlus = true, nowMs = nowMs)
        val thisWeek = HistoryTimeline.resolve(sessions, HistoryFilter.ThisWeek, isPlus = true, nowMs = nowMs)

        assertEquals("Alcalá", allTime.periodStats?.favoriteStreet)
        assertEquals("Gran Vía", thisWeek.periodStats?.favoriteStreet)
        assertNotEquals(allTime.periodStats?.parkings, thisWeek.periodStats?.parkings)
    }

    @Test
    fun `should_attachThePreviousPeriod_forTheComparison`() {
        // Two parkings this week, one the week before → the card can say how the week moved.
        val sessions = listOf(session(0), session(1), session(8))
        val resolved = HistoryTimeline.resolve(
            sessions, HistoryFilter.ThisWeek, isPlus = true, nowMs = nowMs,
        )
        assertNotNull(resolved.periodStats?.previous, "the week before must be available to compare")
    }

    @Test
    fun `should_haveNoPreviousPeriod_forTheAllTimeFilter`() {
        val resolved = HistoryTimeline.resolve(
            listOf(session(0), session(40)), HistoryFilter.All, isPlus = true, nowMs = nowMs,
        )
        assertNull(resolved.periodStats?.previous, "there is no previous everything")
    }

    @Test
    fun `should_countTheLifetimeOverTheFullHistory_evenForAFreeAccount`() {
        // The footer says "desde siempre", so it has to mean it. The gate narrows what a free user
        // can SEE; it must not make a line about the car's whole life quietly describe seven days.
        val sessions = listOf(
            session(1, publishedSpot = true),
            session(2),
            session(40, publishedSpot = true),
            session(80),
        )
        val free = HistoryTimeline.resolve(sessions, HistoryFilter.All, isPlus = false, nowMs = nowMs)

        assertEquals(4, free.lifetimeParkings)
        assertEquals(2, free.lifetimeSpotsReleased)
        // …while what the screen may show is still gated to the free window.
        assertEquals(2, free.visibleSessions.size)
        assertEquals(2, free.hiddenOlderCount)
    }

    @Test
    fun `should_computeAFreeAccountsFigures_overWhatItCanSee`() {
        // Anything else would be a paywall with a hole: figures derived from rows the user is not
        // allowed to read. [MONEY-001]
        val sessions = listOf(session(1), session(2), session(40), session(80))
        val free = HistoryTimeline.resolve(sessions, HistoryFilter.All, isPlus = false, nowMs = nowMs)
        val plus = HistoryTimeline.resolve(sessions, HistoryFilter.All, isPlus = true, nowMs = nowMs)

        assertEquals(2, free.periodStats?.parkings)
        assertEquals(4, plus.periodStats?.parkings)
    }

    @Test
    fun `should_excludeTheActiveSession_fromTheLifetimeCount`() {
        // The car parked right now has not finished a parking yet — the same rule the aggregate
        // applies to durations.
        val resolved = HistoryTimeline.resolve(
            listOf(session(0, active = true), session(1), session(2)),
            HistoryFilter.All,
            isPlus = true,
            nowMs = nowMs,
        )
        assertEquals(2, resolved.lifetimeParkings)
    }
}
