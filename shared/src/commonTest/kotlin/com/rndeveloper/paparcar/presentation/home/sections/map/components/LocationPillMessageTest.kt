package com.rndeveloper.paparcar.presentation.home.sections.map.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What the map's location pill says — the whole product rule of the pill, kept out of the
 * composition so it can be read as the sentence it is. [UI-NO-LOCATION-WEARS-THE-ACCURACY-PILL-001]
 */
class LocationPillMessageTest {

    @Test
    fun should_staySilent_when_theFixIsGoodEnoughToParkOn() {
        // The answer nearly always — and the pill costs no layout for it.
        assertNull(locationPillMessage(locationMissing = false, accuracy = 8f))
    }

    @Test
    fun should_staySilent_when_thereIsNoFixYet_butLocationWorks() {
        // No reading is not a bad reading. The old ladder started at `accuracy > 0f` and painted a
        // warning measured from nothing. [UI-A-WARNING-COSTS-NOTHING-WHEN-SILENT-001]
        assertNull(locationPillMessage(locationMissing = false, accuracy = null))
        assertNull(locationPillMessage(locationMissing = false, accuracy = 0f))
    }

    @Test
    fun should_reportTheMetres_when_theFixIsTooCoarseToTrust() {
        assertEquals(
            LocationPillMessage.PoorAccuracy(120),
            locationPillMessage(locationMissing = false, accuracy = 120.7f),
        )
        // 50 m exactly is still good enough: the threshold is what you pass, not what you reach.
        assertNull(locationPillMessage(locationMissing = false, accuracy = 50f))
    }

    @Test
    fun should_sayNoLocation_when_thereIsNoLocationAtAll() {
        assertEquals(
            LocationPillMessage.NoLocation,
            locationPillMessage(locationMissing = true, accuracy = null),
        )
    }

    /**
     * ⛔ Sin ubicación MANDA sobre precisión pobre. No pueden darse a la vez de verdad —sin fix no
     * hay metros—, pero el orden se escribe: una píldora que dijera «±120 m» con el permiso
     * denegado estaría informando de una medición que no existe, y encima se quedaría sin la única
     * parte accionable, que es abrir los permisos.
     */
    @Test
    fun should_outrankPoorAccuracy_when_locationIsMissingToo() {
        assertEquals(
            LocationPillMessage.NoLocation,
            locationPillMessage(locationMissing = true, accuracy = 120f),
        )
    }
}
