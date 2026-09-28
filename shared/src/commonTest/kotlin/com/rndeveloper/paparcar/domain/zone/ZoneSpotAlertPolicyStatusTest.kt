package com.rndeveloper.paparcar.domain.zone

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.Spot
import com.rndeveloper.paparcar.domain.model.SpotStatus
import com.rndeveloper.paparcar.domain.model.Zone
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which SPOT STATES a zone alert may speak about.
 *
 * ⚠️ The gate is a `when` that ends in an `else`, so a status that says nothing becomes alertable
 * by default. This test is the record of the three that have said something.
 */
class ZoneSpotAlertPolicyStatusTest {

    private val zone = Zone(
        id = "z", userId = "u", name = "Casa",
        lat = 40.0, lon = -3.0, iconKey = "home", createdAt = 0L,
        radiusMeters = 200f,
    )

    private fun spot(status: SpotStatus) = Spot(
        id = "s-${status.name}",
        location = GpsPoint(40.0, -3.0, accuracy = 5f, timestamp = 1_000L, speed = 0f),
        reportedBy = "someone-else",
        status = status,
        expiresAt = 0L,
    )

    @Test
    fun should_alert_when_theSpaceIsActuallyFree() {
        assertTrue(isAlertableInZone(spot(SpotStatus.CONFIRMED), zone, ownUserId = "me", nowMs = 2_000L))
    }

    @Test
    fun should_staySilent_when_theCarHasNotLeftYet() {
        // [SPOT-A-BOARDING-IS-NOT-A-DEPARTURE-001] Two independent reasons, each sufficient: a
        // releasing plaza lives four minutes — the background look runs every fifteen, so nobody
        // could be told in time — and it is not a free space at the moment it would buzz.
        assertFalse(isAlertableInZone(spot(SpotStatus.RELEASING), zone, ownUserId = "me", nowMs = 2_000L))
    }

    @Test
    fun should_staySilent_when_theDepartureWasWithdrawn() {
        assertFalse(isAlertableInZone(spot(SpotStatus.RETRACTED), zone, ownUserId = "me", nowMs = 2_000L))
    }

    @Test
    fun should_alert_when_theDepartureWasOnlyDeduced() {
        // ⛔ Esto afirmaba lo contrario hasta el 28-09, y era la mitad EQUIVOCADA de una paridad que el
        // propio servidor daba por hecha: `fanout.ts` anuncia las PROVISIONAL con `isProvisional: true`
        // para que el copy las matice, y el carril local las tiraba. Resultado: te llegaba con la app
        // CERRADA y se perdía mirando el mapa. [ALERT-A-PROVISIONAL-SPOT-IS-PUSHED-BUT-NOT-SHOWN-001]
        assertTrue(isAlertableInZone(spot(SpotStatus.PROVISIONAL), zone, ownUserId = "me", nowMs = 2_000L))
    }
}
