package com.rndeveloper.paparcar.domain.zone

import com.rndeveloper.paparcar.domain.model.Zone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [ZONE-A-SPOT-TELLS-THE-SERVER-AND-THE-SERVER-TELLS-YOU-001]
 *
 * ⛔ The rule under test is the one that replaced a server-side entitlement check: **being in the
 * index IS the permission**. The Cloud Function never asks who holds Plus — so every way of losing
 * the right to be watched has to come out of here as an empty list, and every one of them has to
 * leave the user's own zones untouched.
 */
class ZoneAlertSubscriptionTest {

    private fun zone(id: String = "z-1", notify: Boolean = true, radius: Float = 250f) = Zone(
        id = id,
        userId = "me",
        name = "Casa",
        lat = 40.0,
        lon = -3.0,
        iconKey = "home",
        createdAt = 0L,
        radiusMeters = radius,
        notifyOnSpot = notify,
    )

    /** Stand-in for the real geohash: records what radius it was asked for. */
    private var askedRadius: Double? = null
    private val cells: (Double, Double, Double) -> List<String> = { _, _, r ->
        askedRadius = r
        listOf("ezjmgx", "ezjmgy")
    }

    private fun subs(
        uid: String? = "me",
        zones: List<Zone> = listOf(zone()),
        isPlus: Boolean = true,
        notificationsEnabled: Boolean = true,
        tokens: List<String> = listOf("token-a"),
    ) = zoneAlertSubscriptionsFor(
        uid = uid,
        zones = zones,
        isPlus = isPlus,
        notificationsEnabled = notificationsEnabled,
        tokens = tokens,
        nowMs = 1_000L,
        cellsOf = cells,
    )

    @Test
    fun should_produceOneRowPerSubscribedZone_when_everythingHolds() {
        val result = subs(zones = listOf(zone("z-1"), zone("z-2"), zone("z-3", notify = false)))

        assertEquals(listOf("me_z-1", "me_z-2"), result.map { it.id })
        assertEquals(listOf("token-a"), result.first().tokens)
    }

    /** ⛔ Losing Plus empties the index. That IS the pause — and it touches no zone. */
    @Test
    fun should_produceNothing_when_thereIsNoPlus() {
        assertTrue(subs(isPlus = false).isEmpty())
    }

    @Test
    fun should_produceNothing_when_theMasterSwitchIsOff() {
        assertTrue(subs(notificationsEnabled = false).isEmpty())
    }

    @Test
    fun should_produceNothing_when_thereIsNoSession() {
        assertTrue(subs(uid = null).isEmpty())
        assertTrue(subs(uid = "").isEmpty())
    }

    @Test
    fun should_produceNothing_when_noZoneAsksToBeWatched() {
        assertTrue(subs(zones = listOf(zone(notify = false))).isEmpty())
    }

    /**
     * A row nobody can be reached through is a row the server reads on every nearby spot for
     * nothing — the cheapest way to make a cheap fan-out expensive.
     */
    @Test
    fun should_produceNothing_when_thereIsNoUsableToken() {
        assertTrue(subs(tokens = emptyList()).isEmpty())
        assertTrue(subs(tokens = listOf("", "  ")).isEmpty())
    }

    /** The cells must cover the ALERT circle, not the drawn one, or the server looks too narrow. */
    @Test
    fun should_askForTheAlertRadius_when_theZoneIsAPin() {
        subs(zones = listOf(zone(radius = 50f)))

        assertEquals(ZONE_ALERT_MIN_RADIUS_METERS.toDouble(), askedRadius)
    }

    @Test
    fun should_carryTheAlertRadius_when_theZoneIsAlreadyWide() {
        val result = subs(zones = listOf(zone(radius = 500f)))

        assertEquals(500f, result.single().radiusMeters)
    }

    /** Refreshed on every sync, so a live user never brushes it and a dead one stops being read. */
    @Test
    fun should_stampAnExpiry_when_itSubscribes() {
        assertEquals(1_000L + SUBSCRIPTION_TTL_MS, subs().single().expiresAt)
    }

    /** One row per zone, so unsubscribing one never disturbs the others. */
    @Test
    fun should_keyRowsByUserAndZone_when_severalZonesAreWatched() {
        val result = subs(zones = listOf(zone("home"), zone("work")))

        assertEquals(setOf("me_home", "me_work"), result.map { it.id }.toSet())
        assertTrue(result.all { it.uid == "me" })
    }
}
